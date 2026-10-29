import assert from "node:assert/strict";
import test from "node:test";

import { applyApprovalStatus } from "../src/services/incidentState.ts";

test("approval updates only the matching dashboard incident", () => {
  const incidents = [
    { incidentId: "INC-001", status: "AWAITING_APPROVAL", title: "one" },
    { incidentId: "INC-002", status: "OPEN", title: "two" },
  ];

  const updated = applyApprovalStatus(incidents, "INC-001", "APPROVED");

  assert.deepEqual(updated.map(({ incidentId, status }) => ({ incidentId, status })), [
    { incidentId: "INC-001", status: "APPROVED" },
    { incidentId: "INC-002", status: "OPEN" },
  ]);
  assert.equal(updated[0].title, "one");
});

test("denial updates the dashboard incident status", () => {
  const updated = applyApprovalStatus(
    [{ incidentId: "INC-003", status: "AWAITING_APPROVAL" }],
    "INC-003",
    "DENIED",
  );

  assert.equal(updated[0].status, "DENIED");
});

test("non-decision outcomes leave pending incidents unchanged", () => {
  const incidents = [{ incidentId: "INC-002", status: "OPEN" }];

  assert.deepEqual(applyApprovalStatus(incidents, "INC-002", "AUTO_ALLOWED"), incidents);
});
