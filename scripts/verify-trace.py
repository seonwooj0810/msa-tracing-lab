#!/usr/bin/env python3
"""Verify actual application responses and Jaeger traces using only the standard library."""
import argparse
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


def tags(span):
    return {tag["key"]: tag["value"] for tag in span.get("tags", [])}


def parent(span):
    parents = [ref["spanID"] for ref in span.get("references", []) if ref["refType"] == "CHILD_OF"]
    require(len(parents) <= 1, "Span has multiple CHILD_OF parents")
    return parents[0] if parents else None


def wait_for_trace(base_url, trace_id, count):
    deadline = time.monotonic() + 30
    last = "not indexed yet"
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        try:
            status, result = get_json(f"{base_url}/api/traces/{trace_id}", timeout=min(8, remaining))
            if time.monotonic() >= deadline:
                last = "response arrived after deadline"
                break
            traces = result.get("data") or []
            if status == 200 and traces and len(traces[0].get("spans", [])) >= count:
                return traces[0]
            last = f"HTTP {status}, {len(traces[0]['spans']) if traces else 0} spans"
        except (URLError, TimeoutError, ValueError) as error:
            last = str(error)
        time.sleep(min(0.5, max(0, deadline - time.monotonic())))
    raise AssertionError(f"Jaeger trace {trace_id} incomplete after 30s: {last}")


def verify(trace, trace_id, failure=False, external_parent=None):
    spans = trace["spans"]
    expected_count = 6 if failure else 7
    require(len(spans) == expected_count, f"Expected {expected_count} spans, got {len(spans)}")
    require(all(s["traceID"] == trace_id for s in spans), "Trace ID changed between services")
    require(len({s["spanID"] for s in spans}) == expected_count, "Span IDs are not unique")
    processes = trace["processes"]

    def service(span):
        return processes[span["processID"]]["serviceName"]

    def only(service_name, kind):
        found = [s for s in spans if service(s) == service_name and tags(s).get("span.kind") == kind]
        require(len(found) == 1, f"Expected one {service_name} {kind} span, got {len(found)}")
        return found[0]

    bff = only("bff", "server")
    member = only("member-service", "server")
    order = only("order-service", "server")
    by_id = {s["spanID"]: s for s in spans}
    require(parent(member) in by_id and parent(order) in by_id, "Missing downstream client parent")
    member_client, order_client = by_id[parent(member)], by_id[parent(order)]
    for client in (member_client, order_client):
        require(service(client) == "bff" and tags(client).get("span.kind") == "client", "Wrong BFF client span")
        require(parent(client) == bff["spanID"], "BFF client disconnected from root")
    require(member_client["startTime"] + member_client["duration"] <= order_client["startTime"],
            "Order request started before member request completed")
    if external_parent:
        require(parent(bff) == external_parent, "External parent span was not preserved")
    else:
        require(parent(bff) is None, "New trace unexpectedly has an external parent")

    product_client = only("order-service", "client")
    require(parent(product_client) == order["spanID"], "Product client disconnected from order server")
    if failure:
        require(tags(product_client).get("error") is True, "Failed product client has no error tag")
        for server in (bff, order):
            attributes = tags(server)
            status = attributes.get("status", attributes.get("http.status_code", attributes.get("http.response.status_code")))
            require(int(status or 0) == 502, f"Expected 502 span on {service(server)}, got {status}")
    else:
        product = only("product-service", "server")
        require(parent(product) == product_client["spanID"], "Product server disconnected from client")
    return spans


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bff-url", default="http://localhost:8080")
    parser.add_argument("--jaeger-url", default="http://localhost:16686")
    parser.add_argument("--expect-error", action="store_true", help="Run after stopping product-service")
    args = parser.parse_args()
    bff_url, jaeger_url = args.bff_url.rstrip("/"), args.jaeger_url.rstrip("/")
    scenarios = [("new trace", {}, None, None)]
    if not args.expect_error:
        trace_id, parent_id = secrets.token_hex(16), secrets.token_hex(8)
        scenarios += [
            ("external W3C context", {"traceparent": f"00-{trace_id}-{parent_id}-01"}, trace_id, parent_id),
            ("malformed W3C context", {"traceparent": "invalid-traceparent"}, None, None),
        ]
    for name, headers, expected_trace_id, external_parent in scenarios:
        status, body = get_json(f"{bff_url}/api/mypage/1", headers)
        require(status == (502 if args.expect_error else 200), f"{name}: unexpected HTTP {status}: {body}")
        trace_id = body.get("traceId", "")
        require(re.fullmatch(r"[0-9a-f]{32}", trace_id) and int(trace_id, 16) != 0, "Invalid response traceId")
        if expected_trace_id:
            require(trace_id == expected_trace_id, "External traceId was replaced")
        if not args.expect_error:
            require(body.get("member") == {"id": 1, "name": "선우"}, "Wrong member payload")
            require(body.get("orders") == [{"id": 1000, "memberId": 1, "product": {
                "id": 100, "name": "MSA 학습 도서", "price": 30000}, "quantity": 1}], "Wrong orders payload")
        trace = wait_for_trace(jaeger_url, trace_id, 6 if args.expect_error else 7)
        spans = verify(trace, trace_id, args.expect_error, external_parent)
        print(f"PASS {name}: {len(spans)} spans, traceId={trace_id}")
        print(f"     {jaeger_url}/trace/{trace_id}")
    print("All trace checks passed.")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, URLError, TimeoutError, ValueError, KeyError, TypeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
