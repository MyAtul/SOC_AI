"""Send clearly labeled synthetic failed-logon alerts to the local test backend."""

from datetime import datetime, timezone
import time
import uuid

import requests


API_BASE = "http://127.0.0.1:8082/api"
ALERT_COUNT = 5
POLL_SECONDS = 30
POLL_INTERVAL_SECONDS = 0.5
EXPECTED_DETECTION = "Multiple Failed Logins"


def build_alerts(run_id, event_time=None):
    """Build one identifiable five-alert run in a single UTC bucket."""
    if event_time is None:
        event_time = datetime.now(timezone.utc).replace(microsecond=0)
    if event_time.tzinfo is None or event_time.utcoffset() is None:
        raise ValueError("event_time must be timezone-aware")

    event_time = event_time.astimezone(timezone.utc).replace(microsecond=0)
    host = f"aisoc-synth-{run_id}".lower()
    utc_time = event_time.isoformat(timespec="seconds").replace("+00:00", "Z")
    # The legacy field remains timezone-less, while this synthetic source uses
    # the UTC wall clock. Detection uses the explicit eventTimestampUtc field.
    legacy_time = event_time.replace(tzinfo=None).isoformat(timespec="seconds")
    return host, [
        {
            "alertId": f"SYNTHETIC-{run_id}-{index + 1}",
            "source": "SYNTHETIC_PIPELINE_TEST",
            "rule": "FAILED_LOGON",
            "severity": "MEDIUM",
            "host": host,
            "description": "Synthetic test event; not Windows telemetry",
            "timestamp": legacy_time,
            "eventTimestampUtc": utc_time,
        }
        for index in range(ALERT_COUNT)
    ]


def run_pipeline(poll_seconds=POLL_SECONDS, session=requests):
    """Submit alerts, verify saved IDs, then poll for the correlated incident."""
    run_id = uuid.uuid4().hex[:12]
    host, alerts = build_alerts(run_id)

    for alert in alerts:
        response = session.post(f"{API_BASE}/alerts", json=alert, timeout=5)
        response.raise_for_status()
        saved_alert = response.json()
        if not isinstance(saved_alert, dict) or saved_alert.get("alertId") != alert["alertId"]:
            raise RuntimeError(
                "Alert API did not return the submitted alert ID; stop and verify the test backend."
            )

    deadline = time.monotonic() + poll_seconds
    while time.monotonic() < deadline:
        response = session.get(f"{API_BASE}/incidents", timeout=5)
        response.raise_for_status()
        incidents = response.json()
        if not isinstance(incidents, list):
            raise RuntimeError("Incident API returned an unexpected response shape.")
        matching = [
            incident for incident in incidents
            if incident.get("host") == host
            and incident.get("detection") == EXPECTED_DETECTION
        ]
        if matching:
            incident = matching[0]
            return {
                "host": host,
                "alert_ids": [alert["alertId"] for alert in alerts],
                "incident_id": incident.get("incidentId"),
            }
        time.sleep(POLL_INTERVAL_SECONDS)

    raise TimeoutError(
        f"No correlated incident appeared for synthetic host {host} within "
        f"{poll_seconds} seconds; inspect the test backend logs and detection-work state."
    )


def main():
    result = run_pipeline()
    print(
        f"Verified synthetic incident {result['incident_id']} for host {result['host']} "
        f"from {len(result['alert_ids'])} synthetic alerts."
    )
    print("This verifies the isolated test pipeline only; it is not evidence of real telemetry.")


if __name__ == "__main__":
    main()
