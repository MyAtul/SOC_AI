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
fake_evtlog.EVENTLOG_SEQUENTIAL_READ = 0x0001
fake_evtlog.EVENTLOG_SEEK_READ = 0x0002
fake_evtlog.EVENTLOG_FORWARDS_READ = 0x0004
fake_evtlog.EVENTLOG_BACKWARDS_READ = 0x0008
fake_evtlog.OpenEventLog = mock.Mock(return_value="handle")
fake_evtlog.CloseEventLog = mock.Mock()
fake_evtlog.ReadEventLog = mock.Mock(return_value=[])
fake_evtlog.GetNumberOfEventLogRecords = mock.Mock(return_value=100)
fake_evtlog.GetOldestEventLogRecord = mock.Mock(return_value=1)
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


class FakeWinError(Exception):
    def __init__(self, code, operation="ReadEventLog", message="mock Windows error"):
        self.winerror = code
        super().__init__(code, operation, message)


class TemporaryState:
    def setUp(self):
        fake_evtlog.OpenEventLog.reset_mock(side_effect=True)
        fake_evtlog.OpenEventLog.return_value = "handle"
        fake_evtlog.CloseEventLog.reset_mock(side_effect=True)
        fake_evtlog.ReadEventLog.reset_mock(side_effect=True)
        fake_evtlog.ReadEventLog.return_value = []
        fake_evtlog.GetNumberOfEventLogRecords.reset_mock(side_effect=True)
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 100
        fake_evtlog.GetOldestEventLogRecord.reset_mock(side_effect=True)
        fake_evtlog.GetOldestEventLogRecord.return_value = 1
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
        self.assertEqual(set(alert), {"alertId", "source", "rule", "severity", "host", "description", "timestamp", "eventTimestampUtc"})
        self.assertTrue(alert["eventTimestampUtc"].endswith("Z"))

    def test_missing_event_time_fails_closed(self):
        event = make_event(20)
        event.TimeGenerated = None
        with self.assertRaises(monitor.StateFileError):
            monitor.build_alert(event)

    def test_alert_uses_real_monitor_identity_and_rejects_localhost(self):
        with mock.patch.object(monitor, "MONITOR_HOST", "WIN-CLIENT-01"):
            alert = monitor.build_alert(make_event(23))
        self.assertEqual(alert["host"], "WIN-CLIENT-01")
        self.assertTrue(monitor.valid_monitor_host(alert["host"]))
        self.assertFalse(monitor.valid_monitor_host("localhost"))
        with mock.patch.object(monitor, "MONITOR_HOST", "localhost"):
            with self.assertRaises(monitor.StateFileError):
                monitor.build_alert(make_event(24))

    def test_existing_outbox_payload_without_utc_field_remains_readable(self):
        alert = monitor.build_alert(make_event(21))
        del alert["eventTimestampUtc"]
        monitor.save_outbox([{"alert": alert, "attempts": 0, "next_attempt_at": 0}])
        self.assertEqual(monitor.load_outbox()[0]["alert"], alert)

    def test_duplicate_alert_id_is_not_queued_twice(self):
        alert = monitor.build_alert(make_event(22))
        queue = []
        monitor.enqueue_alert(queue, alert)
        monitor.enqueue_alert(queue, alert)
        self.assertEqual(len(queue), 1)

    def test_local_pywin_time_round_trip_preserves_fall_back_fold_when_available(self):
        try:
            import pywintypes
        except ImportError:
            self.skipTest("pywin32 is available only on the Windows monitor host")
        from datetime import datetime, timezone

        start = int(datetime(2025, 1, 1, tzinfo=timezone.utc).timestamp())
        end = int(datetime(2028, 1, 1, tzinfo=timezone.utc).timestamp())
        for epoch in range(start, end, 1800):
            generated = pywintypes.Time(epoch)
            if getattr(generated, "fold", 0) == 1:
                self.assertEqual(monitor.event_epoch_seconds(types.SimpleNamespace(TimeGenerated=generated)), epoch)
                return
        self.skipTest("Local timezone has no detectable DST fall-back during the sample interval")

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
    def test_first_run_latest_lookup_uses_backward_sequential_read(self):
        fake_evtlog.ReadEventLog.return_value = [make_event(90)]

        self.assertEqual(monitor.get_latest_record_number(), 90)

        fake_evtlog.ReadEventLog.assert_called_once_with(
            "handle",
            fake_evtlog.EVENTLOG_BACKWARDS_READ | fake_evtlog.EVENTLOG_SEQUENTIAL_READ,
            0,
        )
        fake_evtlog.CloseEventLog.assert_called_once_with("handle")

    def test_first_run_treats_windows_eof_as_an_empty_log(self):
        fake_evtlog.ReadEventLog.side_effect = FakeWinError(38)

        self.assertEqual(monitor.get_latest_record_number(), 0)

        fake_evtlog.CloseEventLog.assert_called_once_with("handle")

    def test_checkpoint_at_latest_record_does_not_seek_past_eof(self):
        monitor.last_record_number = 101
        fake_evtlog.GetOldestEventLogRecord.return_value = 1
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 101

        monitor.read_new_events([])

        fake_evtlog.ReadEventLog.assert_not_called()
        self.assertEqual(monitor.last_record_number, 101)
        fake_evtlog.CloseEventLog.assert_called_once_with("handle")

    def test_resumes_from_existing_checkpoint_with_backward_sequential_read(self):
        monitor.last_record_number = 100
        fake_evtlog.GetOldestEventLogRecord.return_value = 1
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 102
        fake_evtlog.ReadEventLog.return_value = [make_event(102), make_event(101), make_event(100)]
        queue = []

        monitor.read_new_events(queue)

        fake_evtlog.ReadEventLog.assert_called_once_with(
            "handle",
            fake_evtlog.EVENTLOG_BACKWARDS_READ | fake_evtlog.EVENTLOG_SEQUENTIAL_READ,
            0,
        )
        self.assertEqual(monitor.last_record_number, 102)
        self.assertEqual(len(queue), 2)
        self.assertEqual(queue[0]["alert"]["alertId"], "WIN-101")
        self.assertEqual(queue[1]["alert"]["alertId"], "WIN-102")
        self.assertEqual(monitor.load_checkpoint(), 102)
        fake_evtlog.CloseEventLog.assert_called_once_with("handle")

    def test_empty_log_at_initial_checkpoint_is_eof(self):
        monitor.last_record_number = 0
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 0
        fake_evtlog.GetOldestEventLogRecord.return_value = 0

        monitor.read_new_events([])

        fake_evtlog.ReadEventLog.assert_not_called()
        fake_evtlog.CloseEventLog.assert_called_once_with("handle")

    def test_empty_read_batch_before_checkpoint_is_recoverable(self):
        monitor.last_record_number = 99
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 100
        fake_evtlog.GetOldestEventLogRecord.return_value = 1
        fake_evtlog.ReadEventLog.return_value = []

        with self.assertRaises(monitor.EventLogTransientError):
            monitor.read_new_events([])

        fake_evtlog.ReadEventLog.assert_called_once_with(
            "handle",
            fake_evtlog.EVENTLOG_BACKWARDS_READ | fake_evtlog.EVENTLOG_SEQUENTIAL_READ,
            0,
        )
        self.assertEqual(monitor.last_record_number, 99)
        fake_evtlog.CloseEventLog.assert_called_once_with("handle")

    def test_overwritten_records_are_reported_without_checkpoint_change(self):
        monitor.last_record_number = 100
        monitor.save_checkpoint(100)
        fake_evtlog.GetOldestEventLogRecord.return_value = 120
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 10

        with self.assertRaises(monitor.EventLogHistoryGapError):
            monitor.read_new_events([])

        self.assertEqual(monitor.last_record_number, 100)
        self.assertEqual(monitor.load_checkpoint(), 100)
        fake_evtlog.ReadEventLog.assert_not_called()

    def test_cleared_or_reset_log_is_reported_without_checkpoint_change(self):
        monitor.last_record_number = 100
        monitor.save_checkpoint(100)
        fake_evtlog.GetOldestEventLogRecord.return_value = 1
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 10

        with self.assertRaises(monitor.EventLogResetError):
            monitor.read_new_events([])

        self.assertEqual(monitor.last_record_number, 100)
        self.assertEqual(monitor.load_checkpoint(), 100)
        fake_evtlog.ReadEventLog.assert_not_called()

    def test_invalid_parameter_from_sequential_reader_is_permanent_and_diagnostic(self):
        monitor.last_record_number = 100
        fake_evtlog.GetOldestEventLogRecord.return_value = 1
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 200
        fake_evtlog.ReadEventLog.side_effect = FakeWinError(87)

        with self.assertRaisesRegex(monitor.EventLogPermanentError, "Windows error 87"):
            monitor.read_new_events([])

        self.assertEqual(monitor.last_record_number, 100)
        fake_evtlog.CloseEventLog.assert_called_once_with("handle")

    def test_transient_and_permanent_windows_errors_are_distinguished(self):
        monitor.last_record_number = 100
        fake_evtlog.GetOldestEventLogRecord.return_value = 1
        fake_evtlog.GetNumberOfEventLogRecords.return_value = 200
        fake_evtlog.ReadEventLog.side_effect = FakeWinError(1722)

        with self.assertRaises(monitor.EventLogTransientError):
            monitor.read_new_events([])
        fake_evtlog.CloseEventLog.assert_called_once_with("handle")

        fake_evtlog.ReadEventLog.reset_mock()
        fake_evtlog.CloseEventLog.reset_mock()
        fake_evtlog.OpenEventLog.side_effect = FakeWinError(5, "OpenEventLog", "Access denied")
        with self.assertRaises(monitor.EventLogPermanentError):
            monitor.read_new_events([])
        fake_evtlog.OpenEventLog.side_effect = None
        fake_evtlog.OpenEventLog.return_value = "handle"

    def test_failed_outbox_persist_does_not_advance_checkpoint_or_memory_queue(self):
        monitor.last_record_number = 7
        queue = []
        fake_evtlog.ReadEventLog.side_effect = [[make_event(8)], [make_event(7)]]
        with mock.patch.object(monitor, "save_outbox", side_effect=monitor.StateFileError("disk full")):
            with self.assertRaises(monitor.StateFileError):
                monitor.read_new_events(queue)
        self.assertEqual(monitor.last_record_number, 7)
        self.assertEqual(queue, [])
        fake_evtlog.ReadEventLog.reset_mock(side_effect=True)

    def test_alert_is_persisted_before_its_checkpoint(self):
        monitor.last_record_number = 30
        queue = []
        fake_evtlog.ReadEventLog.side_effect = [[make_event(31), make_event(30)]]
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

    def test_missing_event_time_does_not_advance_checkpoint(self):
        monitor.last_record_number = 31
        event = make_event(32)
        event.TimeGenerated = None
        fake_evtlog.ReadEventLog.side_effect = [[event, make_event(31)]]
        with self.assertRaises(monitor.StateFileError):
            monitor.read_new_events([])
        self.assertEqual(monitor.last_record_number, 31)
        fake_evtlog.ReadEventLog.reset_mock(side_effect=True)

    def test_collection_advances_while_backend_is_unavailable(self):
        monitor.last_record_number = 50
        queue = []
        fake_evtlog.ReadEventLog.side_effect = [[make_event(52, 4624), make_event(51), make_event(50)]]
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
