import win32evtlog

LOG = "Security"
CHECKPOINT = 3953927
FLAGS = (
    win32evtlog.EVENTLOG_BACKWARDS_READ
    | win32evtlog.EVENTLOG_SEQUENTIAL_READ
)

handle = None

try:
    handle = win32evtlog.OpenEventLog(None, LOG)
    oldest = win32evtlog.GetOldestEventLogRecord(handle)
    count = win32evtlog.GetNumberOfEventLogRecords(handle)
    newest = oldest + count - 1 if count else 0

    print(f"Checkpoint: {CHECKPOINT}")
    print(f"Oldest retained record: {oldest}")
    print(f"Newest record estimate: {newest}")
    print(f"Retained record count: {count}")

    if not count:
        print("RESULT: Security log is empty.")
    elif CHECKPOINT > newest:
        print("RESULT: Checkpoint is newer than the current log.")
    elif CHECKPOINT < oldest - 1:
        print("RESULT: Checkpoint is older than retained history.")
    elif CHECKPOINT == newest:
        print("RESULT: No newer records to scan.")
    else:
        scanned = 0
        failed_logins = []
        boundary_found = False

        while True:
            try:
                events = win32evtlog.ReadEventLog(handle, FLAGS, 0)
            except Exception as exc:
                if getattr(exc, "winerror", None) == 38:
                    events = []
                else:
                    raise

            if not events:
                break

            for event in events:
                record = event.RecordNumber

                if record <= CHECKPOINT:
                    boundary_found = record == CHECKPOINT
                    break

                scanned += 1
                if (event.EventID & 0xFFFF) == 4625:
                    failed_logins.append(
                        (record, event.TimeGenerated)
                    )

            if boundary_found or (
                events and events[-1].RecordNumber <= CHECKPOINT
            ):
                break

        print(f"Newer records scanned: {scanned}")
        print(f"Checkpoint record encountered: {boundary_found}")
        print(f"Event ID 4625 records found: {len(failed_logins)}")

        for record, timestamp in failed_logins:
            print(f"4625 record={record}, time={timestamp}")

        if boundary_found:
            print("RESULT: Reader reached the checkpoint boundary.")
        else:
            print("RESULT: Boundary not confirmed; investigate reader output.")

except Exception as exc:
    print(f"DIAGNOSTIC ERROR: {type(exc).__name__}: {exc}")
finally:
    if handle is not None:
        win32evtlog.CloseEventLog(handle)
