import importlib.util
import json
import sys
import tempfile
import types
import unittest
from datetime import datetime
from pathlib import Path
from unittest import mock


fake_evtlog = types.ModuleType("win32evtlog")
fake_evtlog.EVENTLOG_BACKWARDS_READ = 1
fake_evtlog.EVENTLOG_SEQUENTIAL_READ = 2
fake_evtlog.EVENTLOG_SEEK_READ = 4
fake_evtlog.EVENTLOG_FORWARDS_READ = 8
fake_evtlog.OpenEventLog = mock.Mock(return_value="handle")
fake_evtlog.CloseEventLog = mock.Mock()
fake_evtlog.ReadEventLog = mock.Mock(return_value=[])
original_evtlog = sys.modules.get("win32evtlog")
sys.modules["win32evtlog"] = fake_evtlog

MODULE_PATH = Path(__file__).with_name("main.py")
spec = importlib.util.spec_from_file_location("security_monitor_main", MODULE_PATH)
monitor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(monitor)
if original_evtlog is None:
    del sys.modules["win32evtlog"]
else:
    sys.modules["win32evtlog"] = original_evtlog


def make_event(record_number, event_id=4625):
    return types.SimpleNamespace(
        EventID=event_id,
        RecordNumber=record_number,
        TimeGenerated=datetime(2026, 1, 2, 3, 4, 5),
    )


class TemporaryState:
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory(dir=Path(__file__).parent)
        root = Path(self.temp_dir.name)
        self.checkpoint = mock.patch.object(monitor, "CHECKPOINT_FILE", root / "checkpoint.json")
        self.outbox_path = mock.patch.object(monitor, "OUTBOX_FILE", root / "outbox.json")
        self.checkpoint.start()
        self.outbox_path.start()

    def tearDown(self):
        mock.patch.stopall()
        self.temp_dir.cleanup()


class PersistenceTests(TemporaryState, unittest.TestCase):
    def test_checkpoint_and_outbox_round_trip_with_stable_alert_id(self):
        event = make_event(19)
        alert = monitor.build_alert(event)
        queue = []
        monitor.enqueue_alert(queue, alert)
        monitor.save_checkpoint(19)
        self.assertEqual(monitor.load_checkpoint(), 19)
        restored = monitor.load_outbox()
        self.assertEqual(restored[0]["alert"], alert)
        self.assertEqual(restored[0]["alert"]["alertId"], "WIN-19")
        self.assertEqual(set(alert), {"alertId", "source", "rule", "severity", "host", "description", "timestamp"})

    def test_corrupt_checkpoint_and_outbox_fail_closed(self):
        monitor.CHECKPOINT_FILE.write_text('{"last_record_number": -1}', encoding="utf-8")
        with self.assertRaises(monitor.StateFileError):
            monitor.load_checkpoint()
        monitor.OUTBOX_FILE.write_text('{"version":1,"alerts":[{}]}', encoding="utf-8")
        with self.assertRaises(monitor.StateFileError):
            monitor.load_outbox()

    def test_interrupted_temp_file_is_not_treated_as_missing_state(self):
        monitor.CHECKPOINT_FILE.with_suffix(".json.tmp").write_text("partial", encoding="utf-8")
        with self.assertRaises(monitor.StateFileError):
            monitor.load_checkpoint()


class QueueAndCheckpointTests(TemporaryState, unittest.TestCase):
    def test_failed_outbox_persist_does_not_advance_checkpoint_or_memory_queue(self):
        monitor.last_record_number = 7
        queue = []
        fake_evtlog.ReadEventLog.side_effect = [[make_event(8)], []]
        with mock.patch.object(monitor, "save_outbox", side_effect=monitor.StateFileError("disk full")):
            with self.assertRaises(monitor.StateFileError):
                monitor.read_new_events(queue)
        self.assertEqual(monitor.last_record_number, 7)
        self.assertEqual(queue, [])
        fake_evtlog.ReadEventLog.reset_mock(side_effect=True)

    def test_alert_is_persisted_before_its_checkpoint(self):
        monitor.last_record_number = 30
        queue = []
        fake_evtlog.ReadEventLog.side_effect = [[make_event(31)], []]
        order = []
        real_save_outbox = monitor.save_outbox
        real_save_checkpoint = monitor.save_checkpoint

        def save_queue(items):
            order.append("outbox")
            real_save_outbox(items)

        def save_position(record_number):
            order.append("checkpoint")
            real_save_checkpoint(record_number)

        with mock.patch.object(monitor, "save_outbox", side_effect=save_queue), \
                mock.patch.object(monitor, "save_checkpoint", side_effect=save_position):
            monitor.read_new_events(queue)
        self.assertEqual(order, ["outbox", "checkpoint"])
        self.assertEqual(monitor.last_record_number, 31)
        fake_evtlog.ReadEventLog.reset_mock(side_effect=True)

    def test_collection_advances_while_backend_is_unavailable(self):
        monitor.last_record_number = 50
        queue = []
        fake_evtlog.ReadEventLog.side_effect = [[make_event(51), make_event(52, 4624)], []]
        monitor.read_new_events(queue)
        self.assertEqual(monitor.last_record_number, 52)
        self.assertEqual(len(queue), 1)
        fake_evtlog.ReadEventLog.reset_mock(side_effect=True)


class RetryTests(TemporaryState, unittest.TestCase):
    def test_retry_backoff_is_bounded_and_payload_id_is_unchanged(self):
        queue = []
        alert = monitor.build_alert(make_event(73))
        monitor.enqueue_alert(queue, alert)
        response = types.SimpleNamespace(ok=False, status_code=503, text="unavailable")
        with mock.patch.object(monitor.requests, "post", return_value=response) as post:
            monitor.drain_outbox(queue, now=100)
            self.assertEqual(queue[0]["attempts"], 1)
            self.assertEqual(queue[0]["next_attempt_at"], 101)
            monitor.drain_outbox(queue, now=101)
            self.assertEqual(queue[0]["attempts"], 2)
            self.assertEqual(queue[0]["next_attempt_at"], 103)
            for attempt in range(3, 11):
                due = queue[0]["next_attempt_at"]
                monitor.drain_outbox(queue, now=due)
                self.assertLessEqual(queue[0]["next_attempt_at"] - due, monitor.MAX_RETRY_DELAY)
            self.assertTrue(all(call.kwargs["json"]["alertId"] == "WIN-73" for call in post.call_args_list))
        self.assertEqual(monitor.load_outbox()[0]["alert"], alert)

    def test_success_removes_persisted_head(self):
        queue = []
        monitor.enqueue_alert(queue, monitor.build_alert(make_event(74)))
        with mock.patch.object(monitor.requests, "post", return_value=types.SimpleNamespace(ok=True)):
            monitor.drain_outbox(queue, now=1)
        self.assertEqual(queue, [])
        self.assertEqual(monitor.load_outbox(), [])


if __name__ == "__main__":
    unittest.main()
