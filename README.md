# AI-SOC — AI-Augmented Security Operations Center

An AI-Augmented Security Operations Center (AI-SOC) prototype designed to support security alert monitoring, incident detection, investigation, MITRE ATT&CK mapping, and analyst-supervised incident response.

The project combines a React dashboard with a Spring Boot backend and PostgreSQL persistence. It organizes security findings into incidents, presents investigation results, recommends response actions, and records approval decisions.

> **Project status:** Active development. Investigation currently uses a mock provider, and response actions are simulated only. The approval audit migration requires validation against a disposable PostgreSQL database before production use.

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Attack Scenarios](#attack-scenarios)
- [Response Actions](#response-actions)
- [Incident Investigation](#incident-investigation)
- [Approval and Audit Workflow](#approval-and-audit-workflow)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Installation and Setup](#installation-and-setup)
- [API Endpoints](#api-endpoints)
- [Testing](#testing)
- [Security and Safety](#security-and-safety)
- [Current Limitations](#current-limitations)
- [Future Enhancements](#future-enhancements)

## Overview

Security Operations Centers must process alerts from multiple sources, identify suspicious activity, investigate incidents, and coordinate appropriate responses.

AI-SOC aims to provide a unified platform for this workflow through:

- Security alert ingestion and incident correlation.
- Incident severity and status tracking.
- MITRE ATT&CK technique references.
- Investigation summaries with evidence and provenance information.
- Recommended response actions with analyst approval requirements.
- Persistent incident status and approval-decision history.
- A web dashboard for incident investigation and monitoring.

The system is designed with human oversight in mind. Recommended actions are not equivalent to executed remediation, and catalogue references are not treated as proof that an attack occurred.

## Key Features

### Incident Management

- Retrieve and display stored security incidents.
- View incident identifiers, severity, status, host, detection type, and timestamps.
- Open incident details for investigation and review.
- Handle loading, empty, and API error states in the frontend.

### Attack Scenario Catalogue

Provides structured scenario metadata, MITRE ATT&CK mappings, detection coverage labels, and investigation context.

Coverage is classified as:
- **Implemented:** Detection logic exists for the stated scope.
- **Partial:** Only some evidence or behaviors are supported.
- **Knowledge-base only:** A reference exists, but corresponding detection is not implemented.

### Investigation

- Generate investigation results through the existing investigation service.
- Persist investigation results for retrieval.
- Present scenario metadata, technique references, recommendations, and provenance where supported.
- Distinguish mock investigation output from genuine model-generated analysis.

### Response Planning

- Recommend scenario-specific response actions.
- Indicate whether an action requires approval.
- Represent response outcomes as simulated.
- Avoid executing disruptive operations on real systems.

### Approval and Audit

- Approve or deny incident response requests through the backend.
- Persist incident status changes through the existing service.
- Record approval decisions using the new approval-audit implementation when its database migration has been applied.
- Return decision history through the API.

Authenticated analyst identity is not currently available. Audit actors therefore remain nullable until authentication is implemented.

## Architecture

```text
Windows Security Monitor
          |
          | Security alerts
          v
Spring Boot REST API
          |
          +----------------------+
          |                      |
          v                      v
Detection and              Incident Management
Correlation                       |
          |                       |
          +-----------+-----------+
                      |
                      v
             Investigation Service
                      |
                      v
             Scenario and ATT&CK
                   Catalogue
                      |
                      v
              Response Planning
                      |
                      v
              Analyst Approval
                      |
                      v
             Decision Audit Trail
                      |
                      v
                 PostgreSQL

React + TypeScript Dashboard
          |
          | REST API
          v
Spring Boot REST API
```

The architecture is intended to separate telemetry collection, detection logic, investigation, response planning, and analyst decision-making.

Actual telemetry coverage depends on the event sources and collectors currently implemented.

## Technology Stack

| Component | Technology |
|---|---|
| Backend | Java, Spring Boot |
| API | Spring WebMVC, REST |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL |
| Database migrations | Flyway migration scripts |
| Frontend | React, TypeScript, Vite |
| Styling | Tailwind CSS |
| Testing | JUnit, Mockito, frontend tests |
| Security knowledge base | MITRE ATT&CK |
| Telemetry source | Windows Security Monitor |

## Attack Scenarios

### 1. SSH Brute Force

**MITRE ATT&CK:** T1110 — Brute Force

| Technique | ID |
|---|---|
| Password Guessing | T1110.001 |
| Password Cracking | T1110.002 |
| Password Spraying | T1110.003 |
| Credential Stuffing | T1110.004 |

Potential detection evidence includes repeated SSH authentication failures, source IP, target account, target host, authentication outcome, and timestamps.

SSH-specific detection requires SSH telemetry. Windows Event ID 4625 alone is not sufficient evidence of an SSH attack.

### 2. Suspicious PowerShell Execution

**MITRE ATT&CK:** T1059.001 — PowerShell

The catalogue covers indicators such as:

- Encoded PowerShell commands.
- Suspicious download cradles.
- Execution policy bypass.
- Hidden-window execution.
- Potential AMSI bypass attempts.
- Attempts to disable security features.

Detection requires suitable telemetry, such as process-creation events, command-line information, or relevant security events.

Individual indicators are evidence for investigation, not automatic proof of malicious activity.

### 3. Linux Privilege Escalation

**MITRE ATT&CK:** T1068 — Exploitation for Privilege Escalation

Related references include:

| Technique or vulnerability | Identifier |
|---|---|
| Setuid and Setgid Abuse | T1548.001 |
| Sudo and Sudo Caching Abuse | T1548.003 |
| Container Escape to Host | T1611 |
| Dirty COW | CVE-2016-5195 |
| Dirty Pipe | CVE-2022-0847 |

These references support investigation and detection-rule development. They do not establish that Linux privilege escalation or a particular CVE exploitation attempt has been detected.

Linux telemetry and suitable detection rules are required for live detection.

## Response Actions

The response catalogue includes the following actions:

| Action | Purpose |
|---|---|
| `block_ip` | Simulated blocking of a suspicious IP |
| `disable_account` | Simulated account disabling |
| `isolate_host` | Simulated host isolation |
| `kill_process` | Simulated process termination |
| `collect_logs` | Recommend or simulate evidence collection |
| `tag_ioc` | Simulated indicator-of-compromise tagging |
| `notify_analyst` | Represent analyst notification |
| Force Password Reset | Simulated password-reset request |
| Revoke Compromised SSH Keys | Simulated SSH key revocation |
| Audit SUID Binaries and Sudo Permissions | Recommend security auditing |
| Remove Persistence Mechanisms | Simulated persistence-removal action |
| Patch Vulnerabilities | Recommend or simulate patching |

**All response actions are simulated in the current prototype.** No real host isolation, account disabling, network blocking, process termination, key revocation, or patching is performed.

## Incident Investigation

The investigation workflow uses the existing backend investigation service and persistence layer.

Investigation responses can include:

- Scenario identification and coverage status.
- Incident summary and severity.
- Evidence and associated alert identifiers, where available.
- MITRE ATT&CK technique identifiers and references.
- Confidence and detection limitations.
- Recommended response actions.
- Approval requirements.
- Investigation provenance.

### Investigation provenance

The currently configured investigation provider is a mock implementation. Its output must not be interpreted as genuine model-generated analysis.

The system should distinguish:

- Mock or demonstration output.
- Deterministic detection and policy results.
- Genuine model-generated analysis, if introduced in a future implementation.

## Approval and Audit Workflow

The current workflow supports incident approval and denial through the backend.

The approval service:
1. Looks up and locks the incident.
2. Evaluates the configured approval policy.
3. Applies the permitted decision to the incident status.
4. Persists the decision and associated audit entry in the same transaction when the audit migration has been applied.
5. Supports retrieval of recorded decision history.

Repeated matching decisions are intended to be idempotent, while conflicting decisions return a conflict response.

### Important limitations

- Authentication and authorization are not yet implemented.
- Decisions cannot currently be attributed to a verified human analyst.
- Existing incident statuses have not been backfilled into audit history because their original actor and decision time are unknown.
- Approval does not execute remediation.
- Database-level audit persistence remains subject to validation of the V3 migration.

## Project Structure

The project uses separate backend and frontend directories.

```text
AI-SOC/
├── Backend/
│   └── aisoc-backend/
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/
│       │   │   │   └── ai_soc_backend/
│       │   │   │       ├── config/
│       │   │   │       └── incident/
│       │   │   │           ├── catalog/
│       │   │   │           ├── controller/
│       │   │   │           ├── dto/
│       │   │   │           ├── entity/
│       │   │   │           ├── repository/
│       │   │   │           └── service/
│       │   │   └── resources/
│       │   │       ├── application.properties
│       │   │       └── db/migration/
│       │   └── test/
│       └── pom.xml
├── Frontend/
│   ├── src/
│   │   ├── features/
│   │   │   ├── incidents/
│   │   │   └── investigations/
│   │   ├── services/
│   │   └── App.tsx
│   ├── package.json
│   └── vite.config.ts
└── README.md
```

The exact files and package layout may evolve as development continues.

## Prerequisites

Install the following software:

- Java JDK compatible with the backend build.
- Maven or the repository's Maven Wrapper.
- PostgreSQL.
- Node.js and npm.
- Git.

The backend must be able to connect to the configured PostgreSQL database before it can start successfully.

## Installation and Setup

### 1. Configure PostgreSQL

Create or configure the intended application database and database user.

The backend's current configuration references:

```text
Host: localhost
Port: 5432
Database: aisoc
```

Configure credentials locally. **Do not commit real database passwords or other secrets to Git.**

Before enabling Flyway or applying new migrations, review the migration history and validate the migration sequence against a disposable database.

### 2. Start the backend

Open PowerShell:

```powershell
Set-Location 'D:\Major Project\Backend\aisoc-backend'
.\mvnw.cmd spring-boot:run
```

Alternatively, use `mvn spring-boot:run` if Maven is installed.

The configured backend port is `8080`.

Base API URL:

```text
http://localhost:8080/api
```

The application requires a reachable PostgreSQL database and a compatible schema.

### 3. Start the frontend

Open a second PowerShell window:

```powershell
Set-Location 'D:\Major Project\Frontend'
npm install
npm run dev
```

Vite normally serves the application at:

```text
http://localhost:5173/
```

The frontend API client defaults to:

```text
http://localhost:8080/api
```

An environment-specific Vite API URL override may be used when needed. Restart Vite after changing environment variables.

### 4. Verify the incident API

Once the backend is running, request:

```text
GET http://localhost:8080/api/incidents
```

The endpoint returns stored incident records when the database and application are operating correctly.

If the endpoint fails, check the backend startup logs and database connectivity before changing frontend routes.

## API Endpoints

The following routes are defined by the current controller implementation.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/incidents` | Retrieve incidents |
| GET | `/api/incidents/{incidentId}` | Retrieve one incident |
| POST | `/api/incidents/{incidentId}/investigate` | Generate and persist investigation output |
| GET | `/api/incidents/{incidentId}/investigation` | Retrieve saved investigation output |
| POST | `/api/incidents/{incidentId}/approve` | Approve an incident |
| POST | `/api/incidents/{incidentId}/deny` | Deny an incident |
| GET | `/api/incidents/{incidentId}/approval-decisions` | Retrieve approval decision history, if exposed by the current controller |
| GET | `/api/alerts` | Alert endpoints; consult the controller for supported operations |
| GET | `/api/catalog/...` | Scenario and response catalogue endpoints; consult `CatalogController` for exact paths |

The API may evolve as implementation progresses. Verify the actual controller mappings before integrating additional clients.

## Testing

### Backend tests

From the backend directory:

```powershell
.\mvnw.cmd test
```

To run selected tests:

```powershell
.\mvnw.cmd "-Dtest=AttackScenarioCatalogTest,ResponseActionCatalogTest,MockApprovalServiceTest,IncidentApprovalControllerTest" test
```

Some Windows/JDK environments may require a compatible Mockito inline-mock-maker agent configuration. Follow the repository's test setup and avoid modifying production dependencies merely to work around a local test-runner issue.

### Frontend tests and checks

From the frontend directory, run the scripts defined in `package.json`. The current workflow includes:

```powershell
npx tsc --noEmit -p tsconfig.app.json
npm run lint
npm run build
```

Run the project's configured frontend test command as well.

### Database migration validation

The V3 approval-audit migration must be validated using a disposable PostgreSQL database before it is applied to the actual application database.

Verify:

- The migration sequence applies successfully.
- JPA entities match the migrated schema.
- Approval and denial persist status and audit records atomically.
- Repeated matching decisions do not create duplicate audit records.
- Conflicting decisions are handled correctly.
- Hibernate schema validation succeeds without relying on automatic schema updates.

Do not use the live database for migration experiments.

## Security and Safety

AI-SOC is a security monitoring and response-planning prototype.

- No real remediation is executed by the response-action catalogue.
- No exploit simulations are required for catalogue validation.
- Windows Event ID 4625 is not treated as evidence of SSH or PowerShell activity by itself.
- Knowledge-base references do not imply that an attack has been detected.
- Approval decisions should not be attributed to a named analyst without authenticated identity.
- Production deployments require authentication, authorization, secure secret management, and a reviewed database migration strategy.
- Audit history must distinguish historical incident status from decisions recorded after audit support was introduced.

## Current Limitations

1. **Mock investigation provider:** Investigation output is simulated rather than generated by a genuine AI model.
2. **Mock policy:** Approval policy is deterministic and hard-coded.
3. **No authentication:** Analyst identity and authorization are not implemented.
4. **Migration validation pending:** V3 has not yet been validated against PostgreSQL.
5. **Simulated remediation:** Response actions do not change real systems.
6. **Partial telemetry coverage:** SSH, PowerShell, and Linux privilege-escalation detection depends on telemetry collectors and rules that may not yet exist.
7. **Audit history gaps:** Earlier decisions cannot be reconstructed reliably from incident status alone.
8. **Production hardening required:** Authentication, authorization, secure configuration, monitoring, and operational controls remain future work.

## Future Enhancements

- Integrate a genuine AI investigation provider with evidence-grounded outputs.
- Add authenticated analyst accounts and role-based authorization.
- Validate and adopt database migrations through a controlled process.
- Expand SSH authentication telemetry and brute-force correlation.
- Add Windows process-creation and PowerShell telemetry support.
- Integrate Linux authentication, privilege-escalation, and host telemetry.
- Strengthen persistent approval history and audit integrity.
- Add isolated integration tests and end-to-end pipeline verification.
- Improve detection coverage metrics and evidence traceability.
- Add production deployment, secrets management, and operational monitoring.

## Project Goal

AI-SOC aims to evolve into a modular, evidence-driven Security Operations Center platform that combines incident correlation, investigation support, MITRE ATT&CK context, and human-supervised response planning.

The central design principle is that **detections must be supported by evidence, AI output must have clear provenance, and simulated actions must never be presented as completed remediation.**
