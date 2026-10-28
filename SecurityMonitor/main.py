
import json
import math
import time
from pathlib import Path

import requests
import win32evtlog


SERVER = "localhost"
LOG_TYPE = "Security"
EVENT_ID = 4625

BACKEND_URL = "http://localhost:8080/api/alerts"
POLL_INTERVAL = 5
REQUEST_TIMEOUT = 5
MAX_RETRY_DELAY = 60

CHECKPOINT_FILE = Path(__file__).with_name("checkpoint.json")
OUTBOX_FILE = Path(__file__).with_name("outbox.json")
last_record_number = 0


class StateFileError(RuntimeError):
    """A persisted monitor state file is invalid or could not be read."""


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
    fields = {"alertId", "source", "rule", "severity", "host", "description", "timestamp"}
    return (isinstance(alert, dict) and set(alert) == fields
            and all(isinstance(alert[key], str) and alert[key] for key in fields))


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
    return {
        "alertId": f"WIN-{record_number}",
        "source": "WINDOWS_SECURITY",
        "rule": "FAILED_LOGON",
        "severity": "MEDIUM",
        "host": SERVER,
        "description": f"Windows Security Event {event_id}: failed logon detected",
        "timestamp": event.TimeGenerated.strftime("%Y-%m-%dT%H:%M:%S"),
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
    handle = win32evtlog.OpenEventLog(SERVER, LOG_TYPE)
    flags = win32evtlog.EVENTLOG_BACKWARDS_READ | win32evtlog.EVENTLOG_SEQUENTIAL_READ
    try:
        events = win32evtlog.ReadEventLog(handle, flags, 0)
        return events[0].RecordNumber if events else 0
    finally:
        win32evtlog.CloseEventLog(handle)


def read_new_events(outbox):
    global last_record_number
    handle = win32evtlog.OpenEventLog(SERVER, LOG_TYPE)
    flags = win32evtlog.EVENTLOG_SEEK_READ | win32evtlog.EVENTLOG_FORWARDS_READ
    try:
        next_record = last_record_number + 1
        while True:
            events = win32evtlog.ReadEventLog(handle, flags, next_record)
            if not events:
                break
            for event in events:
                record_number = event.RecordNumber
                if record_number <= last_record_number:
                    continue
                if (event.EventID & 0xFFFF) == EVENT_ID:
                    alert = build_alert(event)
                    # A failed outbox write raises before either checkpoint changes.
                    enqueue_alert(outbox, alert)
                    print(f"[QUEUED] EventID={EVENT_ID} Record={record_number}")
                save_checkpoint(record_number)
                last_record_number = record_number
            next_record = last_record_number + 1
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
    while True:
        try:
            read_new_events(outbox)
        except Exception as error:
            print(f"[ERROR] Event log read failed: {error}")
        try:
            drain_outbox(outbox)
        except Exception as error:
            print(f"[ERROR] Outbox processing failed: {error}")
        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()
