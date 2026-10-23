# AI-SOC — AI-Augmented Security Operations Center

**Autonomous Threat Investigation, Policy-Aware Response, and Bounded Autonomous Remediation**

AI-SOC is a security operations platform designed to collect security events, detect suspicious activity, generate incidents, investigate threats using AI, and recommend policy-controlled responses. The long-term objective is to build an AI-augmented Security Operations Center that combines evidence-based investigation, deterministic security policies, human approval, and safely bounded automation.

> **Core principle:** AI proposes. Evidence supports. Rules verify. Safety controls constrain. Humans approve high-risk actions.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Problem Statement](#2-problem-statement)
3. [Project Objectives](#3-project-objectives)
4. [Key Features](#4-key-features)
5. [System Architecture](#5-system-architecture)
6. [Technology Stack](#6-technology-stack)
7. [Project Structure](#7-project-structure)
8. [Implemented Modules](#8-implemented-modules)
9. [Current AI Investigation](#9-current-ai-investigation)
10. [Installation and Setup](#10-installation-and-setup)
11. [Running the Project](#11-running-the-project)
12. [API Endpoints](#12-api-endpoints)
13. [Verification and Testing](#13-verification-and-testing)
14. [Current Limitations](#14-current-limitations)
15. [Future Development Roadmap](#15-future-development-roadmap)
16. [Potential Advanced Features](#16-potential-advanced-features)
17. [Security and Safety Principles](#17-security-and-safety-principles)
18. [Project Status](#18-project-status)
19. [Learning Outcomes](#19-learning-outcomes)
20. [Disclaimer](#20-disclaimer)

---

## 1. Project Overview

Traditional Security Operations Centers (SOCs) rely on security analysts to review alerts, investigate suspicious activity, determine severity, and decide on an appropriate response.

AI-SOC aims to improve this workflow by combining conventional security monitoring with AI-assisted threat investigation and controlled response planning.

The system currently demonstrates a working pipeline that collects Windows failed-login events, sends alerts to a Spring Boot backend, detects suspicious authentication patterns, stores incidents in PostgreSQL, and displays investigation results through a React dashboard.

A local Large Language Model (LLM) using Ollama has also been installed and tested independently. Connecting it to the backend is a planned development step.

### Project vision

Build a modular, extensible, AI-augmented SOC platform capable of:

- Collecting security events from monitored systems.
- Detecting suspicious activity using configurable rules.
- Correlating alerts into meaningful security incidents.
- Investigating incidents using supporting evidence and AI.
- Mapping detected behavior to MITRE ATT&CK.
- Recommending risk-aware response actions.
- Enforcing deterministic security policies.
- Requiring human approval for high-risk actions.
- Executing only authorized, bounded remediation actions.
- Recording investigation decisions and response activities in an audit trail.

## 2. Problem Statement

Security teams can receive large numbers of alerts from operating systems, applications, networks, and security monitoring tools. Manually investigating every alert can consume significant time and delay responses to genuine threats.

AI-generated analysis introduces additional challenges: models can produce unsupported conclusions, misclassify incidents, or recommend actions that are inappropriate for the available evidence.

AI-SOC addresses these challenges through a layered approach in which event collection, detection, persistence, investigation, policy verification, approval, and remediation are separate responsibilities.

The goal is not to give an AI model unrestricted control over security infrastructure. Instead, the system is designed to use AI as an investigation assistant within explicit technical and operational safety boundaries.

## 3. Project Objectives

1. Develop an end-to-end security event ingestion pipeline.
2. Detect suspicious authentication activity using backend detection rules.
3. Store alerts and incidents in a relational database.
4. Build a dashboard for reviewing incidents and investigation results.
5. Persist investigation results so they survive application restarts.
6. Integrate a locally running LLM for security investigation.
7. Validate AI outputs against structured schemas and security rules.
8. Implement policy-based response decisions and approval workflows.
9. Design bounded remediation with explicit authorization.
10. Maintain an auditable record of security decisions and actions.
11. Build a modular architecture that can support additional detection sources and investigation capabilities.

## 4. Key Features

### Implemented and verified

- Windows Security Event Log monitoring for Event ID `4625`.
- Python-based security event collection.
- HTTP alert submission to a Spring Boot REST API.
- Alert persistence using PostgreSQL.
- Rule-based failed-login incident detection.
- Automatic creation of incidents when the configured threshold is reached.
- React dashboard for viewing incidents.
- Investigation response persistence in PostgreSQL.
- API for retrieving saved investigation results.
- Investigation details displayed in the frontend.
- Investigation results preserved after restarting the backend.
- Local Ollama installation and model execution tested independently.

### Planned

- Direct integration of Ollama with the Spring Boot investigation service.
- Evidence-grounded AI analysis with structured output validation.
- Improved alert correlation and duplicate incident prevention.
- Policy verification and risk-based approval workflows.
- Actual, controlled remediation execution.
- Audit logging for investigation, approval, and response decisions.
- Additional event sources, detection rules, and security analytics.

## 5. System Architecture

### Current working pipeline

```text
+--------------------------------+
| Windows Security Event Log     |
| Event ID 4625: Failed Logon     |
+---------------+----------------+
                |
                v
+--------------------------------+
| Python Security Monitor        |
| Collects and formats events    |
+---------------+----------------+
                |
                | HTTP POST
                v
+--------------------------------+
| Spring Boot Backend            |
| AlertController                |
| AlertService                   |
+---------------+----------------+
                |
                v
+--------------------------------+
| PostgreSQL                     |
| Stores security alerts         |
+---------------+----------------+
                |
                v
+--------------------------------+
| Detection Service              |
| Failed-login threshold rule    |
+---------------+----------------+
                |
                v
+--------------------------------+
| Incident Management            |
| Creates and stores incidents   |
+---------------+----------------+
                |
                v
+--------------------------------+
| Investigation Service          |
| Current mock-based analysis    |
+---------------+----------------+
                |
                v
+--------------------------------+
| Investigation Persistence      |
| Stores structured results      |
+---------------+----------------+
                |
                v
+--------------------------------+
| React + TypeScript Dashboard   |
| Displays incidents and results |
+--------------------------------+
```

### Planned AI-assisted investigation and response architecture

```text
Security Event Sources
        |
        v
Event Collection and Normalization
        |
        v
Alert Storage and Correlation
        |
        v
Detection Rules and Incident Creation
        |
        v
Evidence Collection
        |
        v
AI Investigation Service
        |
        v
Structured Investigation Result
        |
        v
Deterministic Policy Verification
        |
        v
Risk and Approval Evaluation
        |
        v
Human Approval / Authorized Automation
        |
        v
Bounded Remediation Execution
        |
        v
Verification and Audit Logging
```

The second architecture represents the intended direction of development. Its future components should not be interpreted as already implemented.

## 6. Technology Stack

| Layer               | Technology                  | Purpose                                      |
| ------------------- | --------------------------- | -------------------------------------------- |
| Backend             | Java                        | Application and business logic               |
| Backend framework   | Spring Boot                 | REST APIs and dependency management          |
| Persistence         | Spring Data JPA / Hibernate | Database interaction                         |
| Database            | PostgreSQL                  | Alerts, incidents, and investigation records |
| Frontend            | React                       | Dashboard interface                          |
| Frontend language   | TypeScript                  | Typed application code                       |
| Build tool          | Vite                        | Frontend development and builds              |
| Styling             | Tailwind CSS                | Dashboard styling                            |
| HTTP client         | Axios                       | Frontend API communication                   |
| Security monitoring | Python                      | Windows event collection                     |
| Windows integration | pywin32                     | Access to Windows Event Log                  |
| Local AI runtime    | Ollama                      | Local LLM execution                          |
| Tested local model  | Qwen2.5 3B                  | Candidate for AI investigation integration   |
| Version control     | Git and GitHub              | Source control and collaboration             |
| Development IDE     | IntelliJ IDEA               | Backend development                          |

### Planned technology and integration options

Depending on project requirements, future versions may incorporate:

- MITRE ATT&CK data and technique mappings.
- Retrieval-Augmented Generation (RAG) for trusted security knowledge.
- A vector database for document retrieval.
- SIEM integrations for broader event collection.
- Docker for reproducible deployment.
- A cloud LLM as an optional second-opinion reviewer.
- Metrics, centralized logging, and automated integration testing.

These are potential extensions, not requirements for the current working pipeline.

## 7. Project Structure

The following is a logical overview of the existing repository and its important components. Exact files may evolve as the implementation grows.

```text
Major Project/
|
+-- Backend/
|   |
|   +-- aisoc-backend/
|       |
|       +-- src/
|       |   |
|       |   +-- main/
|       |   |   |
|       |   |   +-- java/ai_soc_backend/
|       |   |       |
|       |   |       +-- incident/
|       |   |           |
|       |   |           +-- controller/
|       |   |           |   +-- AlertController.java
|       |   |           |   +-- IncidentController.java
|       |   |           |
|       |   |           +-- dto/
|       |   |           |
|       |   |           +-- entity/
|       |   |           |   +-- Alert.java
|       |   |           |   +-- Incident.java
|       |   |           |   +-- AIInvestigation.java
|       |   |           |
|       |   |           +-- repository/
|       |   |           |   +-- AlertRepository.java
|       |   |           |   +-- IncidentRepository.java
|       |   |           |   +-- AIInvestigationRepository.java
|       |   |           |
|       |   |           +-- service/
|       |   |               +-- AlertService.java
|       |   |               +-- AlertServiceImpl.java
|       |   |               +-- DetectionService.java
|       |   |               +-- DetectionServiceImpl.java
|       |   |               +-- AIInvestigationService.java
|       |   |               +-- MockAIInvestigationService.java
|       |   |               +-- AIInvestigationPersistenceService.java
|       |   |               +-- ResponsePlanService.java
|       |   |               +-- PolicyService.java
|       |   |               +-- ApprovalService.java
|       |   |
|       |   +-- test/
|       |
|       +-- pom.xml
|
+-- Frontend/
|   |
|   +-- src/
|   |   +-- App.tsx
|   |   +-- services/
|   |       +-- incidentService.ts
|   |
|   +-- package.json
|
+-- SecurityMonitor/
|   +-- main.py
|   +-- requirements.txt
|
+-- README.md
```

This structure is representative. Consult the repository for the complete current file list.

## 8. Implemented Modules

### 8.1 Windows Security Monitor

**Location:** `SecurityMonitor/`

The Python monitor reads Windows Security Event Log entries and detects failed-logon events using Event ID `4625`.

Its current responsibilities include:

- Reading Windows security events.
- Extracting relevant event information.
- Formatting an alert payload.
- Sending the payload to the backend.
- Reporting event detection and submission status.

The monitor has successfully detected failed-login events and submitted them to the local Spring Boot API during controlled testing.

### 8.2 Alert Ingestion API

**Components:** `AlertController`, `AlertService`, and `AlertServiceImpl`

The backend accepts alert payloads over HTTP and stores the resulting alert records in PostgreSQL.

The alert service then passes the alert to the detection service.

### 8.3 Detection Service

**Components:** `DetectionService` and `DetectionServiceImpl`

The current detection rule looks for at least five `FAILED_LOGON` alerts within a five-minute timestamp window.

When the threshold is reached, the backend creates an incident with details such as:

- Title: `Possible Brute Force Attack`
- Severity: `HIGH`
- Status: `OPEN`
- Detection name: `Multiple Failed Logins`

**Current limitation:** the rule uses global failed-login counting rather than fully correlating events by host, source IP, or account. This can lead to unrelated events being grouped together and duplicate incidents being generated.

### 8.4 Incident Management

The incident API exposes incident data to the React dashboard.

Incidents include fields such as their identifier, title, severity, and status. The current dashboard displays incidents generated through the backend pipeline.

### 8.5 AI Investigation Service

The existing investigation service interface separates the investigation contract from its implementation.

A mock investigation service currently returns structured results, allowing the rest of the system to be developed and tested without requiring an LLM.

The investigation result includes:

- Classification
- Severity
- Confidence
- MITRE ATT&CK technique
- Supporting evidence
- Summary
- Recommended action
- Risk
- Approval requirement

### 8.6 Investigation Persistence

The `AIInvestigation` entity, repository, and persistence service store investigation results in PostgreSQL.

The implemented behavior includes:

- Saving structured investigation results against incidents.
- Updating an existing result when an incident is investigated again.
- Retrieving a saved investigation through a dedicated endpoint.
- Returning a not-found response when an investigation has not been saved.
- Persisting investigation results across backend restarts.

### 8.7 React Dashboard

The frontend provides an interface for viewing incidents and their investigation details.

The investigation display supports fields including classification, severity, confidence, attack technique, evidence, summary, recommended action, risk, and approval requirement.

It also handles investigation loading, missing results, and request failures.

## 9. Current AI Investigation

### Existing mock investigation

The currently integrated investigation path uses the mock service rather than Ollama.

This is intentional: it allows the event pipeline, database persistence, API, and frontend to work independently of the LLM integration.

### Ollama installation and independent testing

Ollama has been installed on the Windows development machine.

| Item                  | Current value        |
| --------------------- | -------------------- |
| Ollama version        | `0.40.8`             |
| Model                 | `qwen2.5:3b`         |
| Downloaded model size | Approximately 1.9 GB |
| Local execution       | Tested successfully  |
| Backend integration   | Not yet completed    |

The model was tested using a synthetic failed-login investigation scenario. It produced a structured response with a classification, severity, confidence, findings, recommended action, and approval requirement.

However, the test also demonstrated why model output must be validated:

- It made an unsupported claim about the absence of known malicious activity.
- It supplied an incorrect MITRE ATT&CK identifier for brute-force activity.
- Its severity and risk assessments were not fully consistent.

The correct brute-force technique reference is `T1110 — Brute Force`. Future integration should enforce valid technique mappings and prevent the model from presenting unavailable threat-intelligence information as fact.

### Planned LLM integration

The next AI development stage is to connect Ollama to the existing investigation service.

The intended flow is:

1. Receive an incident and its associated evidence.
2. Build a structured investigation request.
3. Send the request to the local Ollama API.
4. Parse and validate the model response.
5. Reject or flag malformed and unsupported fields.
6. Apply deterministic policy checks.
7. Persist the validated investigation result.
8. Display the result in the dashboard.
9. Preserve the existing mock implementation as a fallback.

The LLM must not directly execute privileged actions or override policy decisions.

## 10. Installation and Setup

### Prerequisites

Install the following tools:

- Java 21
- Maven, or use the project's Maven wrapper if present
- Node.js and npm
- PostgreSQL
- Python
- Windows with access to the Security Event Log for monitor testing
- Git
- Ollama, for future local LLM integration

### Step 1: Clone the repository

```powershell
git clone <YOUR_GITHUB_REPOSITORY_URL>
cd "Major Project"
```

Replace the placeholder with the actual repository URL.

### Step 2: Configure PostgreSQL

Create a database named:

```sql
CREATE DATABASE aisoc;
```

Configure the backend's local database connection in `application.properties`, using your own PostgreSQL credentials.

The expected database URL in the current development setup is:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/aisoc
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

The username and password above are placeholders. Configure them using your local environment or an appropriate local secrets configuration.

The project currently uses Hibernate schema updates in its development configuration. A production deployment should use controlled database migrations.

**Never commit real database passwords, API keys, or other secrets to Git.**

### Step 3: Start the backend

Open the backend project in IntelliJ IDEA and run:

`AisocBackendApplication.java`

The backend currently starts on:

```text
http://localhost:8080
```

Confirm that Spring Boot starts successfully and connects to PostgreSQL.

### Step 4: Start the frontend

Open a terminal:

```powershell
cd "D:\Major Project\Frontend"
npm install
npm run dev
```

Open the local URL printed by Vite, usually:

```text
http://localhost:5173
```

Ensure the frontend is configured to call the backend at `http://localhost:8080`.

### Step 5: Set up the Windows Security Monitor

Install the Python dependencies:

```powershell
cd "D:\Major Project\SecurityMonitor"
pip install -r requirements.txt
```

Run the monitor in an appropriately elevated terminal when required by Windows permissions:

```powershell
python main.py
```

The monitor should be able to reach the backend alert endpoint.

**Testing caution:** Use a dedicated test machine or account and controlled test events. Do not deliberately generate repeated failed logins against production accounts or external systems.

### Step 6: Verify Ollama

Ollama is installed on the development machine, and the `qwen2.5:3b` model has been downloaded.

Check the installation:

```powershell
ollama --version
ollama list
```

Test the model independently:

```powershell
ollama run qwen2.5:3b
```

The local model has been tested independently. The current backend does not yet rely on Ollama for its investigation responses.

## 11. Running the Project

For the current end-to-end demonstration:

1. Start PostgreSQL.
2. Start the Spring Boot backend from IntelliJ IDEA.
3. Start the React development server.
4. Start the Windows Security Monitor with the required permissions.
5. Generate controlled failed-login test events in a safe test environment.
6. Confirm that alerts reach the backend.
7. Confirm that the detection threshold creates an incident.
8. Open the incident in the dashboard.
9. View the associated investigation result.
10. Restart the backend and verify that the saved investigation remains available.

The LLM integration, real IP blocking, and complete autonomous remediation workflow are not part of this verified demonstration yet.

## 12. API Endpoints

The following endpoints are confirmed by the current implementation or its verified behavior.

| Method | Endpoint                                    | Purpose                          |
| ------ | ------------------------------------------- | -------------------------------- |
| `POST` | `/api/alerts`                               | Submit a security alert          |
| `GET`  | `/api/incidents`                            | Retrieve the incident list       |
| `GET`  | `/api/incidents/{incidentId}/investigation` | Retrieve the saved investigation |

Example investigation request:

```http
GET /api/incidents/INC-002/investigation
```

Example response:

```json
{
  "incidentId": "INC-002",
  "classification": "Brute Force Authentication Attempt",
  "severity": "MEDIUM",
  "confidence": 0.87,
  "attackTechnique": "T1110 - Brute Force",
  "evidence": [
    "Multiple failed login attempts detected",
    "Login attempts originated from the same external IP",
    "Authentication attempts targeted multiple accounts"
  ],
  "summary": "The incident shows a repeated authentication pattern consistent with a possible brute-force attack against the organization's accounts.",
  "recommendedAction": "BLOCK_SOURCE_IP",
  "risk": "MEDIUM",
  "approvalRequired": false
}
```

This example represents a previously verified mock investigation response, not a result generated by the integrated Ollama model.

Additional endpoints may exist for manual investigation, response plans, policy evaluation, and approval workflows. Consult the controllers and DTOs in the repository for their current routes, request formats, and behavior.

## 13. Verification and Testing

The following behaviors have been verified during development.

| Test                                                        | Result  |
| ----------------------------------------------------------- | ------- |
| Python monitor detects Windows Event ID 4625                | Passed  |
| Monitor submits alerts to the backend                       | Passed  |
| Backend connects to PostgreSQL                              | Passed  |
| Failed-login threshold creates an incident                  | Passed  |
| Incident appears in the React dashboard                     | Passed  |
| Investigation result is persisted                           | Passed  |
| Investigation API returns saved JSON                        | Passed  |
| Investigation details appear in the frontend                | Passed  |
| Saved investigation survives backend restart                | Passed  |
| Ollama starts and runs Qwen2.5 3B independently             | Passed  |
| Ollama is integrated into Spring Boot investigation service | Pending |
| AI output validation and policy enforcement                 | Pending |
| Actual remediation execution and verification               | Pending |

Backend tests covering investigation persistence and investigation-controller behavior have previously passed. The frontend production build also passed during the implementation review. These are recorded development results and should be rerun after subsequent changes.

## 14. Current Limitations

### Detection

- The current failed-login rule uses global counting rather than robust source-specific correlation.
- Duplicate incidents can be generated when subsequent alerts continue to satisfy the threshold.
- Detection rules need stronger handling of missing timestamps and event-delivery failures.
- The current implementation primarily demonstrates Windows failed-logon monitoring.

### AI investigation

- The integrated backend currently uses mock investigation results.
- Ollama has only been tested independently.
- Model-generated results require schema validation and evidence-grounding checks.
- MITRE ATT&CK mappings need deterministic validation.

### Response and remediation

- A recommendation such as `BLOCK_SOURCE_IP` is not proof that an IP address was blocked.
- Actual remediation execution has not been verified.
- Policy evaluation, approval workflows, and action execution need to be integrated and tested end to end.
- The system needs safeguards against unauthorized, duplicate, or unsafe actions.

### Production readiness

- Authentication and role-based authorization require security review.
- Secrets must be handled outside committed source code.
- Database migrations and deployment configuration need hardening.
- API error handling, auditability, monitoring, and integration testing should be expanded.

## 15. Future Development Roadmap

### Phase 1 — Stabilize the existing pipeline

- [x] Implement Windows event collection.
- [x] Implement alert ingestion.
- [x] Implement basic failed-login detection.
- [x] Persist incidents and investigations.
- [x] Display investigation results.
- [x] Verify persistence across backend restarts.
- [ ] Improve correlation by source IP, account, host, and time window.
- [ ] Prevent duplicate incidents for the same active attack.
- [ ] Improve delivery reliability and retry behavior.
- [ ] Expand automated tests for detection edge cases.

### Phase 2 — Integrate local AI investigation

- [x] Install Ollama.
- [x] Download Qwen2.5 3B.
- [x] Test the model independently.
- [ ] Implement an Ollama-backed investigation service.
- [ ] Preserve the existing mock service as a fallback.
- [ ] Define a strict JSON response schema.
- [ ] Validate severity, confidence, risk, and recommended actions.
- [ ] Validate MITRE ATT&CK technique identifiers.
- [ ] Prevent unsupported factual claims.
- [ ] Add timeouts, failure handling, and safe fallback behavior.
- [ ] Persist and display validated LLM results.

### Phase 3 — Evidence enrichment and explainability

- [ ] Associate alerts with their originating incident.
- [ ] Collect relevant event details and context.
- [ ] Add evidence provenance and timestamps.
- [ ] Explain why each detection rule triggered.
- [ ] Distinguish observed facts from model-generated hypotheses.
- [ ] Add confidence thresholds and uncertainty handling.
- [ ] Evaluate model outputs against a curated test dataset.

### Phase 4 — Deterministic policy engine

- [ ] Define explicit response policies.
- [ ] Map severity and risk to permitted response actions.
- [ ] Validate model recommendations against policy.
- [ ] Reject unsupported or prohibited actions.
- [ ] Require human approval for high-risk operations.
- [ ] Record policy decisions and reasons.
- [ ] Make policy evaluation independent of the LLM.

### Phase 5 — Approval and bounded remediation

- [ ] Implement approval requests and approval decisions.
- [ ] Enforce role-based authorization.
- [ ] Implement a controlled action-execution service.
- [ ] Add timeouts, action limits, and duplicate-action protection.
- [ ] Support dry-run execution before enabling real actions.
- [ ] Verify whether an action succeeded.
- [ ] Record action results and failures.
- [ ] Add rollback or recovery procedures where possible.
- [ ] Test in an isolated environment before real remediation.

### Phase 6 — Advanced SOC capabilities

- [ ] Add additional Windows event types.
- [ ] Add Linux and application log ingestion.
- [ ] Evaluate SIEM integrations.
- [ ] Add incident timelines and investigation history.
- [ ] Add detection-rule management.
- [ ] Add operational metrics and alerting.
- [ ] Add RAG over trusted security documentation.
- [ ] Evaluate an optional cloud LLM reviewer.
- [ ] Add deployment automation and containerization.
- [ ] Perform security, performance, and reliability testing.

## 16. Potential Advanced Features

The following ideas could differentiate AI-SOC from a basic alert dashboard.

### 16.1 Threat investigation with RAG

Retrieve relevant content from trusted security documentation, MITRE ATT&CK references, internal runbooks, and approved incident-response procedures.

The model can use retrieved material to support its analysis, while the system records the source of each important claim.

### 16.2 Incident correlation and attack timelines

Group related alerts into a single incident based on time, source, target, host, and behavior.

Display a timeline showing the initial event, subsequent activity, detection reason, investigation findings, and response decisions.

### 16.3 Policy-aware response planning

Translate an investigation into a proposed response plan, then evaluate it using deterministic rules.

For example:

- Low risk: monitor or gather additional evidence.
- Medium risk: investigate further or request analyst review.
- High risk: require approval before disruptive actions.
- Critical risk: follow an explicitly configured emergency-response policy.

These are illustrative policies. Actual severity thresholds and permitted actions should be configurable and tested.

### 16.4 Safe automated remediation

Potential actions include disabling a test account, isolating a test host, or adding a temporary firewall rule.

Every action should be limited by authorization, scope, duration, policy, and verification. High-impact actions should require approval unless an explicitly authorized emergency policy says otherwise.

### 16.5 AI investigation comparison

Run the same incident through a local model and an optional second reviewer.

Compare classifications, evidence usage, severity, confidence, and recommendations. A deterministic policy engine should make the final authorization decision rather than simply accepting the majority opinion.

### 16.6 SOC analytics dashboard

Add metrics such as:

- Total alerts and incidents.
- Incident counts by severity.
- Mean time to detect.
- Mean time to investigate.
- Mean time to respond.
- False-positive rate.
- AI recommendation acceptance rate.
- Policy rejection rate.
- Remediation success and failure rate.

These metrics should be calculated from recorded events and outcomes rather than invented by the LLM.

### 16.7 Analyst feedback loop

Allow analysts to mark an investigation as accurate, incomplete, or incorrect. Record corrections and use them to improve prompts, detection rules, evaluation datasets, and investigation quality.

Analyst feedback should not automatically retrain a model or change security policies without a controlled review process.

## 17. Security and Safety Principles

AI-SOC follows a defense-in-depth approach.

1. **Evidence first:** The model must distinguish observed evidence from assumptions.
2. **Structured output:** Investigation results must follow a defined schema.
3. **Deterministic verification:** Critical fields and permitted actions must be checked by backend rules.
4. **Least privilege:** Components should receive only the permissions they require.
5. **Human oversight:** High-risk and disruptive actions require appropriate authorization.
6. **Bounded autonomy:** The model cannot execute arbitrary commands or bypass response policies.
7. **Auditability:** Decisions, approvals, actions, and outcomes should be recorded.
8. **Failure safety:** Model timeouts, malformed responses, and unavailable services must not cause unauthorized actions.
9. **Privacy:** Security logs and incident evidence must be handled as sensitive information.
10. **Verification:** A proposed response is not considered successful until its outcome has been checked.

The LLM is an advisory component, not the authority that grants permission to perform a security action.

## 18. Project Status

**Current development stage:** Working end-to-end prototype with persistent mock investigation results and independently tested local LLM.

| Capability                          | Status                      |
| ----------------------------------- | --------------------------- |
| Windows security event ingestion    | Implemented and tested      |
| REST alert ingestion                | Implemented and tested      |
| PostgreSQL persistence              | Implemented and tested      |
| Basic incident detection            | Implemented and tested      |
| Investigation persistence           | Implemented and tested      |
| React investigation display         | Implemented and tested      |
| Local Ollama installation           | Complete                    |
| Qwen2.5 3B independent model test   | Complete                    |
| Ollama backend integration          | Planned                     |
| Structured AI response validation   | Planned                     |
| RAG and evidence enrichment         | Planned                     |
| Deterministic policy enforcement    | Planned                     |
| Complete human approval workflow    | Not yet verified end to end |
| Actual remediation execution        | Not yet verified            |
| Production deployment and hardening | Future work                 |

## 19. Learning Outcomes

This project provides practical experience in:

- Security event monitoring on Windows.
- REST API design and backend development.
- Java and Spring Boot service architecture.
- Relational database design and persistence.
- React and TypeScript dashboard development.
- Rule-based security detection.
- Local LLM deployment and evaluation.
- AI output validation and structured response handling.
- Security policy design and human-in-the-loop workflows.
- Testing, debugging, and end-to-end integration.
- Secure design for bounded autonomous systems.

## 20. Disclaimer

AI-SOC is a security research and educational project. Its current detection capabilities are limited and should not be considered a complete enterprise SOC or a substitute for established security monitoring systems.

AI-generated investigation results may be inaccurate or incomplete. Do not enable real-world blocking, account modification, host isolation, or other disruptive remediation until authorization controls, deterministic policy enforcement, action verification, audit logging, and recovery mechanisms have been implemented and tested.

---

**Project:** AI-SOC — AI-Augmented Security Operations Center

**Primary objective:** Evidence-based AI investigation with policy-controlled response and bounded autonomy.

**Development approach:** Build and verify the event pipeline first, integrate local AI second, and introduce controlled response automation only after the required safety mechanisms are in place.
