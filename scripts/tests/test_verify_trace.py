import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('verify_trace', ROOT / 'verify-trace.py')
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


def raw_spans(raw):
    return [span for rs in raw['trace']['resourceSpans'] for ss in rs['scopeSpans'] for span in ss['spans']]


class TraceVerificationTest(unittest.TestCase):
    def setUp(self):
        # Captured from this application's real Tempo /api/v2/traces response (product-service stopped).
        self.raw = json.loads((ROOT / 'tests/fixtures/product-unavailable.json').read_text())
        self.trace_id = '7a97f35fe0dc2c5b197f6d87bc0efc47'

    def verify(self):
        return verifier.verify(verifier.normalize(self.raw), self.trace_id, failure=True)

    def test_rejects_trace_arriving_after_poll_deadline(self):
        with patch.object(verifier.time, 'monotonic', side_effect=[0, 0, 33.5]), \
             patch.object(verifier, 'get_json', return_value=(200, self.raw)):
            with self.assertRaisesRegex(AssertionError, 'incomplete after 30s'):
                verifier.wait_for_trace('http://unused', self.trace_id, 6)

    def test_decodes_ids_and_understands_micrometer_status_attribute(self):
        spans = self.verify()
        self.assertEqual({s['traceID'] for s in spans}, {self.trace_id})

    def test_rejects_success_status_in_failure_trace(self):
        for span in raw_spans(self.raw):
            for attribute in span['attributes']:
                if attribute['key'] == 'status' and attribute['value']['stringValue'] == '502':
                    attribute['value']['stringValue'] = '200'
        with self.assertRaisesRegex(AssertionError, 'Expected 502'):
            self.verify()

    def test_rejects_failure_trace_without_error_status(self):
        for span in raw_spans(self.raw):
            span['status'] = {}
        with self.assertRaisesRegex(AssertionError, 'no error status'):
            self.verify()

    def test_rejects_missing_span(self):
        self.raw['trace']['resourceSpans'][-1]['scopeSpans'][0]['spans'].pop()
        with self.assertRaisesRegex(AssertionError, 'Expected 6 spans'):
            self.verify()

    def test_rejects_overlapping_member_and_order_calls(self):
        for span in raw_spans(self.raw):
            if span['kind'] == 'SPAN_KIND_CLIENT' and span['name'] == 'http get':
                span['startTimeUnixNano'], span['endTimeUnixNano'] = '100', '150'
        with self.assertRaisesRegex(AssertionError, 'before member request completed'):
            self.verify()


if __name__ == '__main__':
    unittest.main()
