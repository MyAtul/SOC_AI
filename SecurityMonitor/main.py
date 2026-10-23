import time
import requests
import win32evtlog


SERVER = "localhost"
LOG_TYPE = "Security"
EVENT_ID = 4625

BACKEND_URL = "http://localhost:8080/api/alerts"

POLL_INTERVAL = 5

last_record_number = 0


def send_alert(event):

    event_id = event.EventID & 0xFFFF

    alert = {
        "alertId": f"WIN-{event.RecordNumber}",
        "source": "WINDOWS_SECURITY",
        "rule": "FAILED_LOGON",
        "severity": "MEDIUM",
        "host": SERVER,
        "description": f"Windows Security Event {event_id}: failed logon detected",
        "timestamp": event.TimeGenerated.strftime("%Y-%m-%dT%H:%M:%S")
    }

    try:

        response = requests.post(
            BACKEND_URL,
            json=alert,
            timeout=5
        )

        if response.ok:
            print(
                f"[ALERT SENT] "
                f"Record={event.RecordNumber} "
                f"EventID={event_id}"
            )
        else:
            print(
                f"[ERROR] Backend returned "
                f"{response.status_code}: {response.text}"
            )

    except requests.RequestException as error:

        print(f"[ERROR] Could not reach backend: {error}")


def read_new_events():

    global last_record_number

    hand = win32evtlog.OpenEventLog(
        SERVER,
        LOG_TYPE
    )

    flags = (
        win32evtlog.EVENTLOG_BACKWARDS_READ
        | win32evtlog.EVENTLOG_SEQUENTIAL_READ
    )

    try:

        events = win32evtlog.ReadEventLog(
            hand,
            flags,
            0
        )

        if not events:
            return

        # Events are read newest first.
        # Reverse them so we process them oldest → newest.
        events = reversed(events)

        for event in events:

            record_number = event.RecordNumber

            # Ignore events already processed.
            if record_number <= last_record_number:
                continue

            last_record_number = record_number

            event_id = event.EventID & 0xFFFF

            if event_id == EVENT_ID:

                print(
                    f"[DETECTED] "
                    f"EventID={event_id} "
                    f"Record={record_number}"
                )

                send_alert(event)

    finally:

        win32evtlog.CloseEventLog(hand)


def main():

    global last_record_number

    print("===================================")
    print(" AI-SOC Windows Security Monitor")
    print("===================================")
    print("Monitoring Security Event ID 4625...")
    print(f"Backend: {BACKEND_URL}")
    print()

    # Establish the starting point.
    # We don't want to resend old events when the monitor starts.
    hand = win32evtlog.OpenEventLog(
        SERVER,
        LOG_TYPE
    )

    flags = (
        win32evtlog.EVENTLOG_BACKWARDS_READ
        | win32evtlog.EVENTLOG_SEQUENTIAL_READ
    )

    try:

        events = win32evtlog.ReadEventLog(
            hand,
            flags,
            0
        )

        if events:
            last_record_number = events[0].RecordNumber

    finally:

        win32evtlog.CloseEventLog(hand)

    print(f"Starting from RecordNumber: {last_record_number}")
    print("Waiting for new failed-login events...")
    print()

    while True:

        try:

            read_new_events()

        except Exception as error:

            print(f"[ERROR] Monitor error: {error}")

        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()