#!/usr/bin/env python3
"""Verify actual application responses and Tempo traces using only the standard library."""
import argparse
import base64
import json
import re
import secrets
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def get_json(url, headers=None, timeout=8):
    try:
        with urlopen(Request(url, headers=headers or {}), timeout=timeout) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)


def normalize(response):
    """Flatten a Tempo /api/v2/traces response into spans with hex ids, service, kind, nanosecond times."""
    spans = []
    for resource_spans in response.get("trace", {}).get("resourceSpans", []):
        service = next(a["value"]["stringValue"] for a in resource_spans["resource"]["attributes"]
                       if a["key"] == "service.name")
        for scope_spans in resource_spans.get("scopeSpans", []):
            for span in scope_spans.get("spans", []):
                spans.append({
                    "traceID": base64.b64decode(span["traceId"]).hex(),
                    "spanID": base64.b64decode(span["spanId"]).hex(),
                    "parent": base64.b64decode(span["parentSpanId"]).hex() if span.get("parentSpanId") else None,
                    "service": service,
                    "kind": span["kind"].removeprefix("SPAN_KIND_").lower(),
                    "start": int(span["startTimeUnixNano"]),
                    "end": int(span["endTimeUnixNano"]),
                    "error": span.get("status", {}).get("code") == "STATUS_CODE_ERROR",
                    "attributes": {a["key"]: next(iter(a["value"].values())) for a in span.get("attributes", [])},
                })
    return spans


def wait_for_trace(base_url, trace_id, count):
    deadline = time.monotonic() + 30
    last = "not indexed yet"
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        try:
            status, result = get_json(f"{base_url}/api/v2/traces/{trace_id}", timeout=min(8, remaining))
            if time.monotonic() >= deadline:
                last = "response arrived after deadline"
                break
            spans = normalize(result) if status == 200 else []
            if len(spans) >= count:
                return spans
            last = f"HTTP {status}, {len(spans)} spans"
        except (URLError, TimeoutError, ValueError) as error:
            last = str(error)
        time.sleep(min(0.5, max(0, deadline - time.monotonic())))
    raise AssertionError(f"Tempo trace {trace_id} incomplete after 30s: {last}")


def verify(spans, trace_id, failure=False, external_parent=None):
    expected_count = 6 if failure else 7
    require(len(spans) == expected_count, f"Expected {expected_count} spans, got {len(spans)}")
    require(all(s["traceID"] == trace_id for s in spans), "Trace ID changed between services")
    require(len({s["spanID"] for s in spans}) == expected_count, "Span IDs are not unique")

    def only(service_name, kind):
        found = [s for s in spans if s["service"] == service_name and s["kind"] == kind]
        require(len(found) == 1, f"Expected one {service_name} {kind} span, got {len(found)}")
        return found[0]

    bff = only("bff", "server")
    member = only("member-service", "server")
    order = only("order-service", "server")
    by_id = {s["spanID"]: s for s in spans}
    require(member["parent"] in by_id and order["parent"] in by_id, "Missing downstream client parent")
    member_client, order_client = by_id[member["parent"]], by_id[order["parent"]]
    for client in (member_client, order_client):
        require(client["service"] == "bff" and client["kind"] == "client", "Wrong BFF client span")
        require(client["parent"] == bff["spanID"], "BFF client disconnected from root")
    require(member_client["end"] <= order_client["start"],
            "Order request started before member request completed")
    if external_parent:
        require(bff["parent"] == external_parent, "External parent span was not preserved")
    else:
        require(bff["parent"] is None, "New trace unexpectedly has an external parent")

    product_client = only("order-service", "client")
    require(product_client["parent"] == order["spanID"], "Product client disconnected from order server")
    if failure:
        require(product_client["error"], "Failed product client has no error status")
        for server in (bff, order):
            status = server["attributes"].get("status", server["attributes"].get("http.response.status_code"))
            require(int(status or 0) == 502, f"Expected 502 span on {server['service']}, got {status}")
    else:
        product = only("product-service", "server")
        require(product["parent"] == product_client["spanID"], "Product server disconnected from client")
    return spans


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bff-url", default="http://localhost:8080")
    parser.add_argument("--tempo-url", default="http://localhost:3200")
    parser.add_argument("--expect-error", action="store_true", help="Run after stopping product-service")
    args = parser.parse_args()
    bff_url, tempo_url = args.bff_url.rstrip("/"), args.tempo_url.rstrip("/")
    scenarios = [("new trace", {}, None, None)]
    if not args.expect_error:
        trace_id, parent_id = secrets.token_hex(16), secrets.token_hex(8)
        scenarios += [
            ("external W3C context", {"traceparent": f"00-{trace_id}-{parent_id}-01"}, trace_id, parent_id),
            ("malformed W3C context", {"traceparent": "invalid-traceparent"}, None, None),
        ]
    for name, headers, expected_trace_id, external_parent in scenarios:
        status, body = get_json(f"{bff_url}/api/v1/members/1/mypage", headers)
        require(status == (502 if args.expect_error else 200), f"{name}: unexpected HTTP {status}: {body}")
        payload = body if args.expect_error else body.get("data", {})
        trace_id = payload.get("traceId", "")
        require(re.fullmatch(r"[0-9a-f]{32}", trace_id) and int(trace_id, 16) != 0, "Invalid response traceId")
        if expected_trace_id:
            require(trace_id == expected_trace_id, "External traceId was replaced")
        if not args.expect_error:
            require(body.get("success") is True, "Response is not wrapped in ApiResult")
            require(payload.get("member") == {"id": 1, "name": "선우"}, "Wrong member payload")
            require(payload.get("orders") == [{"id": 1000, "memberId": 1, "product": {
                "id": 100, "name": "MSA 학습 도서", "price": 30000}, "quantity": 1}], "Wrong orders payload")
        trace = wait_for_trace(tempo_url, trace_id, 6 if args.expect_error else 7)
        spans = verify(trace, trace_id, args.expect_error, external_parent)
        print(f"PASS {name}: {len(spans)} spans, traceId={trace_id}")
        print(f"     {tempo_url}/api/v2/traces/{trace_id}")
    print("All trace checks passed.")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, URLError, TimeoutError, ValueError, KeyError, TypeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
