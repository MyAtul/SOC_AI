
import json
import math
import os
import socket
import time
import unicodedata
from datetime import datetime, timezone
from pathlib import Path

import requests
import win32evtlog


SERVER = "localhost"
MONITOR_HOST = socket.gethostname()
LOG_TYPE = "Security"
EVENT_ID = 4625

BACKEND_URL = os.environ.get("AISOC_BACKEND_ALERT_URL", "http://localhost:8080/api/alerts")
POLL_INTERVAL = 5
REQUEST_TIMEOUT = 5
MAX_RETRY_DELAY = 60

CHECKPOINT_FILE = Path(__file__).with_name("checkpoint.json")
OUTBOX_FILE = Path(__file__).with_name("outbox.json")
last_record_number = 0


class StateFileError(RuntimeError):
    """A persisted monitor state file is invalid or could not be read."""


class EventLogReadError(RuntimeError):
    """Base class for a classified Windows Event Log read failure."""


class EventLogTransientError(EventLogReadError):
    """A temporary Windows Event Log failure that can be retried next poll."""


class EventLogPermanentError(EventLogReadError):
    """A configuration, state, or position failure requiring operator review."""


class EventLogHistoryGapError(EventLogPermanentError):
    """The checkpoint points before records still retained by the log."""


class EventLogResetError(EventLogPermanentError):
    """The log was cleared/reset or its record counter moved behind the checkpoint."""


_TRANSIENT_EVENT_LOG_ERROR_CODES = {21, 121, 170, 1460, 1722, 1726, 1231}
_ERROR_HANDLE_EOF = 38
_ERROR_INVALID_PARAMETER = 87
_MAX_EVENT_RECORD_NUMBER = 0xFFFFFFFF


def _event_log_error_code(error):
    code = getattr(error, "winerror", None)
    if code is None and error.args and isinstance(error.args[0], int):
        code = error.args[0]
    return code


def _classified_event_log_error(operation, error):
    code = _event_log_error_code(error)
    description = f"{operation} failed"
    if code is not None:
        description += f" with Windows error {code}"
    description += f": {error}"
    if code in _TRANSIENT_EVENT_LOG_ERROR_CODES:
        return EventLogTransientError(description)
    return EventLogPermanentError(description)


def _atomic_write_json(path, data):
    temp_file = path.with_suffix(path.suffix + ".tmp")
    try:
        temp_file.write_text(json.dumps(data, separators=(",", ":")), encoding="utf-8")
        temp_file.replace(path)
    except OSError as error:
        raise StateFileError(f"Could not persist {path.name}: {error}") from error


def _load_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise StateFileError(f"Could not read valid {path.name}: {error}") from error


def _reject_interrupted_write(path):
    temp_file = path.with_suffix(path.suffix + ".tmp")
    if temp_file.exists():
        raise StateFileError(
            f"Found {temp_file.name}; inspect it and {path.name} before restarting"
        )


def save_checkpoint(record_number):
    if isinstance(record_number, bool) or not isinstance(record_number, int) or record_number < 0:
        raise StateFileError("Checkpoint record number must be a non-negative integer")
    _atomic_write_json(CHECKPOINT_FILE, {"last_record_number": record_number})


def load_checkpoint():
    _reject_interrupted_write(CHECKPOINT_FILE)
    if not CHECKPOINT_FILE.exists():
        return None
    data = _load_json(CHECKPOINT_FILE)
    if (not isinstance(data, dict) or set(data) != {"last_record_number"}
            or isinstance(data["last_record_number"], bool)
            or not isinstance(data["last_record_number"], int)
            or data["last_record_number"] < 0):
        raise StateFileError("checkpoint.json must contain a non-negative integer last_record_number")
    return data["last_record_number"]


def _valid_alert(alert):
    required = {"alertId", "source", "rule", "severity", "host", "description", "timestamp"}
    if (not isinstance(alert, dict) or set(alert) not in (required, required | {"eventTimestampUtc"})
            or not all(isinstance(alert[key], str) and alert[key] for key in required)):
        return False
    if "eventTimestampUtc" not in alert:
        return True  # Existing durable outbox entries remain readable.
    value = alert["eventTimestampUtc"]
    if not isinstance(value, str) or not value.endswith("Z"):
        return False
    try:
        parsed = datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError:
        return False
    return parsed.tzinfo is not None and parsed.utcoffset().total_seconds() == 0


def valid_monitor_host(raw_host):
    if not isinstance(raw_host, str):
        return False
    normalized = unicodedata.normalize("NFC", raw_host.strip()).lower()
    return (bool(normalized) and normalized != "localhost" and len(normalized) <= 255
            and all(char.isalnum() or char in "._-:" for char in normalized)
            and normalized[0].isalnum() and normalized[-1].isalnum())


def event_epoch_seconds(event):
    """Recover the original time_t from pywin32's local-naive datetime view.

    pywin32 constructs TimeGenerated with DateTime_FromTimestamp(time_t). Calling
    timestamp() reverses that local-time view; datetime.fold preserves the repeated
    hour when supported by the installed Python/pywin32 combination. Fail closed if
    the local round trip does not preserve the value.
    """
    generated = getattr(event, "TimeGenerated", None)
    if not isinstance(generated, datetime):
        raise StateFileError("Event has no usable TimeGenerated value")
    try:
        epoch = generated.timestamp()
        if generated.tzinfo is None:
            reconstructed = datetime.fromtimestamp(epoch)
            if (reconstructed != generated
                    or getattr(reconstructed, "fold", 0) != getattr(generated, "fold", 0)):
                raise StateFileError("TimeGenerated cannot be mapped to its original local epoch safely")
        if not math.isfinite(epoch) or not epoch.is_integer():
            raise StateFileError("TimeGenerated is not a whole-second Windows event timestamp")
        return int(epoch)
    except (OverflowError, OSError, ValueError) as error:
        if isinstance(error, StateFileError):
            raise
        raise StateFileError("Could not recover the Windows event epoch") from error


def load_outbox():
    _reject_interrupted_write(OUTBOX_FILE)
    if not OUTBOX_FILE.exists():
        return []
    data = _load_json(OUTBOX_FILE)
    if (not isinstance(data, dict) or set(data) != {"version", "alerts"}
            or isinstance(data["version"], bool) or data["version"] != 1):
        raise StateFileError("outbox.json must have version 1 and an alerts list")
    if not isinstance(data["alerts"], list):
        raise StateFileError("outbox.json alerts must be a list")
    seen_ids = set()
    for item in data["alerts"]:
        if not isinstance(item, dict) or set(item) != {"alert", "attempts", "next_attempt_at"}:
            raise StateFileError("Each outbox item must contain alert, attempts, and next_attempt_at")
        alert = item["alert"]
        attempts = item["attempts"]
        due = item["next_attempt_at"]
        if (not _valid_alert(alert) or isinstance(attempts, bool) or not isinstance(attempts, int)
                or attempts < 0 or isinstance(due, bool) or not isinstance(due, (int, float))
                or not math.isfinite(due) or due < 0):
            raise StateFileError("Outbox item contains invalid alert or retry metadata")
        if alert["alertId"] in seen_ids:
            raise StateFileError(f"Duplicate alertId in outbox: {alert['alertId']}")
        seen_ids.add(alert["alertId"])
    return data["alerts"]


def save_outbox(outbox):
    # Validate before replacing the last known good queue.
    _validate_outbox_items(outbox)
    _atomic_write_json(OUTBOX_FILE, {"version": 1, "alerts": outbox})


def _validate_outbox_items(outbox):
    if not isinstance(outbox, list):
        raise StateFileError("Outbox must be a list")
    seen_ids = set()
    for item in outbox:
        if not isinstance(item, dict) or set(item) != {"alert", "attempts", "next_attempt_at"}:
            raise StateFileError("Each outbox item must contain alert, attempts, and next_attempt_at")
        alert, attempts, due = item["alert"], item["attempts"], item["next_attempt_at"]
        if (not _valid_alert(alert) or isinstance(attempts, bool) or not isinstance(attempts, int)
                or attempts < 0 or isinstance(due, bool) or not isinstance(due, (int, float))
                or not math.isfinite(due) or due < 0 or alert["alertId"] in seen_ids):
            raise StateFileError("Outbox contains invalid or duplicate entries")
        seen_ids.add(alert["alertId"])


def build_alert(event):
    event_id = event.EventID & 0xFFFF
    record_number = event.RecordNumber
    if not valid_monitor_host(MONITOR_HOST):
        raise StateFileError("Configured monitor host identity is invalid or resolves to localhost")
    epoch_seconds = event_epoch_seconds(event)
    generated = event.TimeGenerated
    event_timestamp_utc = datetime.fromtimestamp(epoch_seconds, timezone.utc).isoformat().replace("+00:00", "Z")
    return {
        "alertId": f"WIN-{record_number}",
        "source": "WINDOWS_SECURITY",
        "rule": "FAILED_LOGON",
        "severity": "MEDIUM",
        "host": MONITOR_HOST,
        "description": f"Windows Security Event {event_id}: failed logon detected",
        "timestamp": generated.strftime("%Y-%m-%dT%H:%M:%S"),
        "eventTimestampUtc": event_timestamp_utc,
    }


def enqueue_alert(outbox, alert):
    """Persist one alert before the caller is allowed to checkpoint its event."""
    if not _valid_alert(alert):
        raise StateFileError("Cannot enqueue an alert that does not match the API contract")
    if any(item["alert"]["alertId"] == alert["alertId"] for item in outbox):
        return
    updated = outbox + [{"alert": alert, "attempts": 0, "next_attempt_at": 0}]
    save_outbox(updated)
    outbox.append(updated[-1])


def get_latest_record_number():
    """Find the newest record when establishing the first checkpoint."""
    try:
        handle = win32evtlog.OpenEventLog(SERVER, LOG_TYPE)
    except Exception as error:
        raise _classified_event_log_error("OpenEventLog", error) from error
    flags = win32evtlog.EVENTLOG_BACKWARDS_READ | win32evtlog.EVENTLOG_SEQUENTIAL_READ
    try:
        try:
            events = win32evtlog.ReadEventLog(handle, flags, 0)
        except Exception as error:
            if _event_log_error_code(error) == _ERROR_HANDLE_EOF:
                return 0
            raise _classified_event_log_error("ReadEventLog (backward sequential)", error) from error
        return events[0].RecordNumber if events else 0
    finally:
        win32evtlog.CloseEventLog(handle)


def _event_log_record_bounds(handle):
    """Return the retained record range using Event Log metadata, not a speculative seek."""
    try:
        count = win32evtlog.GetNumberOfEventLogRecords(handle)
        oldest = win32evtlog.GetOldestEventLogRecord(handle)
    except Exception as error:
        raise _classified_event_log_error("Event Log bounds query", error) from error

    if (isinstance(count, bool) or not isinstance(count, int) or count < 0
            or isinstance(oldest, bool) or not isinstance(oldest, int) or oldest < 0):
        raise EventLogPermanentError("Windows returned invalid Event Log record bounds")
    if count == 0:
        return None, None
    if oldest == 0:
        raise EventLogPermanentError("Windows returned a zero oldest record for a non-empty Event Log")

    newest = oldest + count - 1
    if newest > _MAX_EVENT_RECORD_NUMBER:
        raise EventLogResetError(
            "The Security log record counter wrapped; preserve the checkpoint and review the log before resuming"
        )
    return oldest, newest


def _validate_checkpoint_against_log(oldest, newest):
    if oldest is None:
        if last_record_number > 0:
            raise EventLogResetError(
                f"The Security log is empty while checkpoint is {last_record_number}; "
                "the log may have been cleared. Checkpoint was preserved."
            )
        return
    if last_record_number > newest:
        raise EventLogResetError(
            f"Security log newest record {newest} is below checkpoint {last_record_number}; "
            "the log may have been cleared/reset. Checkpoint was preserved."
        )
    if last_record_number < oldest - 1:
        raise EventLogHistoryGapError(
            f"Security log now starts at record {oldest}, after checkpoint {last_record_number}; "
            "older records were overwritten. Checkpoint was preserved; review before resuming."
        )


def read_new_events(outbox):
    global last_record_number
    try:
        handle = win32evtlog.OpenEventLog(SERVER, LOG_TYPE)
    except Exception as error:
        raise _classified_event_log_error("OpenEventLog", error) from error
    flags = win32evtlog.EVENTLOG_BACKWARDS_READ | win32evtlog.EVENTLOG_SEQUENTIAL_READ
    try:
        oldest_record, newest_record = _event_log_record_bounds(handle)
        _validate_checkpoint_against_log(oldest_record, newest_record)
        if newest_record is None:
            return
        if newest_record == last_record_number:
            return

        # Seek-read can fail on large classic logs and a seek to checkpoint+1
        # is invalid at EOF. Read backward sequentially from the newest record,
        # collect only records newer than the checkpoint, then replay them in
        # chronological order after the checkpoint boundary is confirmed.
        alerts_to_enqueue = []
        highest_record_seen = None
        previous_record = None
        checkpoint_boundary_found = False
        oldest_boundary_found = False
        while not checkpoint_boundary_found and not oldest_boundary_found:
            try:
                events = win32evtlog.ReadEventLog(handle, flags, 0)
            except Exception as error:
                if _event_log_error_code(error) == _ERROR_HANDLE_EOF:
                    events = []
                else:
                    raise _classified_event_log_error("ReadEventLog (backward sequential)", error) from error
            if not events:
                current_oldest, current_newest = _event_log_record_bounds(handle)
                _validate_checkpoint_against_log(current_oldest, current_newest)
                if current_newest == last_record_number:
                    return
                raise EventLogTransientError(
                    "ReadEventLog reached EOF before the checkpoint boundary; "
                    "the log may have changed during the read, retrying next poll"
                )

            for event in events:
                record_number = event.RecordNumber
                if (isinstance(record_number, bool) or not isinstance(record_number, int)
                        or record_number <= 0):
                    raise EventLogPermanentError("ReadEventLog returned an invalid record number")
                if previous_record is not None and previous_record - record_number != 1:
                    current_oldest, current_newest = _event_log_record_bounds(handle)
                    _validate_checkpoint_against_log(current_oldest, current_newest)
                    raise EventLogPermanentError(
                        f"Backward Event Log records were not contiguous ({previous_record} then {record_number}); "
                        "checkpoint was preserved"
                    )
                previous_record = record_number

                if record_number <= last_record_number:
                    if record_number < last_record_number:
                        current_oldest, current_newest = _event_log_record_bounds(handle)
                        _validate_checkpoint_against_log(current_oldest, current_newest)
                        raise EventLogPermanentError(
                            f"Backward read skipped checkpoint record {last_record_number} and reached "
                            f"{record_number}; checkpoint was preserved"
                        )
                    checkpoint_boundary_found = True
                    break

                if highest_record_seen is None:
                    highest_record_seen = record_number
                if (event.EventID & 0xFFFF) == EVENT_ID:
                    alerts_to_enqueue.append((record_number, build_alert(event)))

                if oldest_record is not None and record_number == oldest_record:
                    oldest_boundary_found = True
                    break

            if checkpoint_boundary_found or oldest_boundary_found:
                break

        if highest_record_seen is None:
            raise EventLogTransientError(
                "Backward Event Log read ended without observing a record newer than the checkpoint; retrying next poll"
            )

        # The outbox records are durable before the checkpoint moves. If a write
        # fails, the old checkpoint makes the range replayable and alert IDs dedupe it.
        for record_number, alert in reversed(alerts_to_enqueue):
            enqueue_alert(outbox, alert)
            print(f"[QUEUED] EventID={EVENT_ID} Record={record_number}")
        save_checkpoint(highest_record_seen)
        last_record_number = highest_record_seen
    finally:
        win32evtlog.CloseEventLog(handle)


def drain_outbox(outbox, now=None):
    """Make at most one attempt per due poll, preserving FIFO order."""
    if not outbox:
        return
    now = time.time() if now is None else now
    item = outbox[0]
    if item["next_attempt_at"] > now:
        return
    try:
        response = requests.post(BACKEND_URL, json=item["alert"], timeout=REQUEST_TIMEOUT)
        if response.ok:
            updated = outbox[1:]
            save_outbox(updated)
            outbox.pop(0)
            print(f"[ALERT SENT] {item['alert']['alertId']}")
            return
        print(f"[ERROR] Backend returned {response.status_code}: {response.text}")
    except requests.RequestException as error:
        print(f"[ERROR] Delivery failed: {error}")
    attempts = item["attempts"] + 1
    delay = min(2 ** (attempts - 1), MAX_RETRY_DELAY)
    updated = [dict(entry) for entry in outbox]
    updated[0] = dict(item, attempts=attempts, next_attempt_at=now + delay)
    save_outbox(updated)
    outbox[0] = updated[0]
    print(f"[RETRY] {item['alert']['alertId']} in {delay} seconds")


def main():
    global last_record_number
    print("===================================")
    print(" AI-SOC Windows Security Monitor")
    print("===================================")
    print(f"Monitoring Security Event ID {EVENT_ID}...")
    print(f"Backend: {BACKEND_URL}\n")
    try:
        checkpoint = load_checkpoint()
        outbox = load_outbox()
        if checkpoint is None:
            last_record_number = get_latest_record_number()
            save_checkpoint(last_record_number)
            print("First run: initialized checkpoint at latest record.")
        else:
            last_record_number = checkpoint
            print(f"Resuming from saved record: {last_record_number}")
    except Exception as error:
        print(f"[FATAL] Could not initialize monitor state: {error}")
        return
    print("Waiting for new failed-login events.\n")
    reader_paused = False
    while True:
        if not reader_paused:
            try:
                read_new_events(outbox)
            except EventLogPermanentError as error:
                reader_paused = True
                print(f"[FATAL] Event log collection paused: {error}")
                print("Checkpoint and outbox were preserved. Resolve the log/state issue before restarting.")
            except EventLogTransientError as error:
                print(f"[WARN] Temporary Event Log read failure; will retry next poll: {error}")
            except Exception as error:
                print(f"[ERROR] Event log read failed: {error}")
        try:
            drain_outbox(outbox)
        except Exception as error:
            print(f"[ERROR] Outbox processing failed: {error}")
        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()
