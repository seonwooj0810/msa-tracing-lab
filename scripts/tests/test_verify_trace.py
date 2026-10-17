import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('verify_trace', ROOT / 'verify-trace.py')
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class TraceVerificationTest(unittest.TestCase):
    def setUp(self):
        # Captured from this application's real Jaeger response; logs and unrelated tags omitted.
        self.trace = json.loads((ROOT / 'tests/fixtures/product-unavailable.json').read_text())
        self.trace_id = '7964c0caf6a03bd036868758fb7b3281'

    def test_rejects_trace_arriving_after_poll_deadline(self):
        response = {"data": [{"spans": [None] * 7}]}
        with patch.object(verifier.time, 'monotonic', side_effect=[0, 0, 33.5]), \
             patch.object(verifier, 'get_json', return_value=(200, response)):
            with self.assertRaisesRegex(AssertionError, 'incomplete after 30s'):
                verifier.wait_for_trace('http://unused', self.trace_id, 7)

    def test_understands_micrometer_status_tag(self):
        verifier.verify(self.trace, self.trace_id, failure=True)

    def test_rejects_success_status_in_failure_trace(self):
        for span in self.trace['spans']:
            for tag in span['tags']:
                if tag['key'] == 'status' and tag['value'] == '502':
                    tag['value'] = '200'
        with self.assertRaisesRegex(AssertionError, 'Expected 502'):
            verifier.verify(self.trace, self.trace_id, failure=True)

    def test_rejects_missing_span(self):
        self.trace['spans'].pop()
        with self.assertRaisesRegex(AssertionError, 'Expected 6 spans'):
            verifier.verify(self.trace, self.trace_id, failure=True)

    def test_rejects_overlapping_member_and_order_calls(self):
        clients = [s for s in self.trace['spans'] if verifier.tags(s).get('span.kind') == 'client'
                   and self.trace['processes'][s['processID']]['serviceName'] == 'bff']
        for client in clients:
            client['startTime'] = 100
            client['duration'] = 50
        with self.assertRaisesRegex(AssertionError, 'before member request completed'):
            verifier.verify(self.trace, self.trace_id, failure=True)


if __name__ == '__main__':
    unittest.main()
