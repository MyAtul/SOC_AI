import unittest
from datetime import datetime, timezone
from unittest import mock

import synthetic_pipeline_test as harness


class SyntheticPipelineHarnessTests(unittest.TestCase):
    RUN_ID = "fixedrun0000"

    def test_builds_five_same_host_alerts_with_utc_timestamp(self):
        event_time = datetime(2026, 10, 29, 10, 15, 30, tzinfo=timezone.utc)

        host, alerts = harness.build_alerts("abc123", event_time)

        self.assertEqual(host, "aisoc-synth-abc123")
        self.assertEqual(len(alerts), 5)
        self.assertEqual({alert["host"] for alert in alerts}, {host})
        self.assertEqual({alert["eventTimestampUtc"] for alert in alerts}, {"2026-10-29T10:15:30Z"})
        self.assertEqual({alert["timestamp"] for alert in alerts}, {"2026-10-29T10:15:30"})
        self.assertTrue(all(alert["rule"] == "FAILED_LOGON" for alert in alerts))
        self.assertEqual(len({alert["alertId"] for alert in alerts}), 5)

    def test_rejects_timezone_naive_event_time(self):
        with self.assertRaises(ValueError):
            harness.build_alerts("abc123", datetime(2026, 10, 29, 10, 15, 30))

    def test_posts_all_alerts_and_matches_canonical_host_incident(self):
        session = mock.Mock()
        session.post.return_value.raise_for_status.return_value = None
        session.post.return_value.json.side_effect = [
            {"alertId": f"SYNTHETIC-{self.RUN_ID}-{index}"} for index in range(1, 6)
        ]
        session.get.return_value.raise_for_status.return_value = None
        session.get.return_value.json.return_value = [{
            "incidentId": "INC-test",
            "host": f"aisoc-synth-{self.RUN_ID}",
            "detection": harness.EXPECTED_DETECTION,
        }]

        with mock.patch.object(harness.uuid, "uuid4") as uuid4:
            uuid4.return_value.hex = self.RUN_ID + "0000"
            result = harness.run_pipeline(session=session)

        self.assertEqual(session.post.call_count, 5)
        self.assertTrue(all(
            call.args[0].startswith("http://127.0.0.1:8082/api/alerts")
            for call in session.post.call_args_list
        ))
        self.assertEqual(result["incident_id"], "INC-test")
        self.assertEqual(result["host"], f"aisoc-synth-{self.RUN_ID}")
        self.assertEqual(len(result["alert_ids"]), 5)

    def test_stops_when_alert_api_does_not_return_the_saved_alert_id(self):
        session = mock.Mock()
        session.post.return_value.raise_for_status.return_value = None
        session.post.return_value.json.return_value = {"alertId": "unexpected"}

        with self.assertRaisesRegex(RuntimeError, "submitted alert ID"):
            harness.run_pipeline(session=session)

        self.assertEqual(session.post.call_count, 1)
        session.get.assert_not_called()

    def test_polling_timeout_is_bounded_and_actionable(self):
        session = mock.Mock()
        session.post.return_value.raise_for_status.return_value = None
        session.post.return_value.json.side_effect = [
            {"alertId": f"SYNTHETIC-{self.RUN_ID}-{index}"} for index in range(1, 6)
        ]
        session.get.return_value.raise_for_status.return_value = None
        session.get.return_value.json.return_value = []

        with mock.patch.object(harness.uuid, "uuid4") as uuid4, \
                mock.patch.object(harness.time, "monotonic", side_effect=[0, 0, 2]), \
                mock.patch.object(harness.time, "sleep"):
            uuid4.return_value.hex = self.RUN_ID + "0000"
            with self.assertRaisesRegex(TimeoutError, "test backend logs"):
                harness.run_pipeline(poll_seconds=1, session=session)

        session.get.assert_called_once()


if __name__ == "__main__":
    unittest.main()
