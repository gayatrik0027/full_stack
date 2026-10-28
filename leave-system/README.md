# Leave Application System

An end-to-end, production-grade full-stack **Leave Application System** built with **Camunda 8 BPMN**, **Spring Boot 3**, **Java 21**, **React**, **Vite**, and **TypeScript**. Designed for enterprise workflow orchestration, educational assignments, and reliable human-in-the-loop task execution.

---

## 1. Project Overview

The Leave Application System orchestrates employee leave requests through an automated BPMN 2.0 business workflow. When an employee applies for annual, sick, or unpaid leave:
1. **Automated Validation**: The Camunda engine dispatches a job to the `validate-leave` worker, which validates employee entitlement, working days, and date ranges.
2. **Manager Review**: If valid, the process routes to an interactive User Task (`leave-approval`), surfacing the request in the manager's review inbox.
3. **Automated Finalization**: Based on the manager's decision (or if automated validation failed), the workflow routes to the `finalize-leave` worker. If approved, annual leave days are deducted idempotently from the employee's balance.

---

## 2. Architecture & Workflow Explanation

```
 ┌──────────────┐         REST / JSON          ┌────────────────────────────────────────┐
 │ React + Vite │ ───────────────────────────> │        Spring Boot 3.3.4 (8081)        │
 │  TypeScript  │                              │                                        │
 │ (Port 5173)  │ <─────────────────────────── │  • REST Controllers & Exception Handlers │
 └──────────────┘         HTTP Responses       │  • Spring Data JPA & H2 File Database  │
                                               │  • Camunda 8 Job Workers               │
                                               └──────────────────┬─────────────────────┘
                                                                  │
                                                gRPC / Zeebe API  │ (Port 26500)
                                                                  ▼
                                               ┌────────────────────────────────────────┐
                                               │        Camunda 8 Self-Managed          │
                                               │                                        │
                                               │  • Zeebe Workflow Engine (26500)       │
                                               │  • Camunda Operate Web UI (8080)       │
                                               │  • Elasticsearch Datastore (9200)      │
                                               └────────────────────────────────────────┘
```

### BPMN 2.0 Process Diagram (`leave-request`)

```
 [Start: Leave Requested]
           │
           ▼
 [Service Task: Validate Leave Request] (type: validate-leave)
           │
           ▼
    < Gateway: Valid? > ──(No)───────────────────────┐
           │ (Yes)                                    │
           ▼                                          │
 [User Task: Manager Approval] (form: leave-approval) │
           │                                          │
           ▼                                          │
   < Gateway: Approved? > ──(No)──────────────────┐   │
           │ (Yes)                                │   │
           ▼                                      ▼   ▼
 [Service Task: Finalize Approved]          [Merge Gateway]
     (type: finalize-leave)                       │
           │                                      ▼
           ▼                         [Service Task: Finalize Rejected]
  (End: Leave Approved)                   (type: finalize-leave)
                                                  │
                                                  ▼
                                         (End: Leave Rejected)
```

### Process Variables Propagated:
- `requestId`: Database ID of the leave request.
- `employeeEmail`: Email address of the applicant.
- `leaveType`: `ANNUAL`, `SICK`, or `UNPAID`.
- `days`: Calculated working days (excluding Saturdays and Sundays).
- `startDate` & `endDate`: ISO-8601 date strings (`YYYY-MM-DD`).
- `valid`: Boolean output of the validation service task.
- `validationMessage`: Text explaining validation outcome.
- `approved`: Boolean input from the manager's review.
- `managerComment`: Optional feedback provided during approval or rejection.
- `outcome`: String outcome (`"APPROVED"` or `"REJECTED"`).

---

## 3. Technology Stack

- **Backend**:
  - Java 21 LTS
  - Spring Boot 3.3.4
  - Spring Data JPA & Hibernate
  - Spring Validation (`jakarta.validation`)
  - Camunda 8 Spring Boot Starter (`8.5.11`)
  - H2 Database (file-backed with H2 Web Console)
  - JUnit 5 & Mockito (automated testing)
- **Frontend**:
  - React 18
  - Vite 5
  - TypeScript 5
  - Vanilla CSS (Glassmorphism, CSS Custom Properties, Responsive Layout)
  - Lucide Icons
- **Workflow & Orchestration**:
  - Camunda 8 Self-Managed (Zeebe Broker, Operate, Tasklist)
  - BPMN 2.0 XML
  - Camunda Forms JSON Schema
- **DevOps & Infrastructure**:
  - Docker & Docker Compose
  - Multi-stage Docker builds
  - Nginx (production frontend reverse-proxy)
  - Maven 3.9 Wrapper

---

## 4. Complete Folder Structure

```
leave-system/
├── README.md                      # Complete system documentation
├── .gitignore                     # Git ignore rules for Java, Node, IDEs & H2
├── .editorconfig                  # Code formatting conventions
├── docker-compose.yml             # Orchestration for Camunda 8, Spring Boot & React
├── bpmn/
│   ├── leave-request.bpmn         # Executable BPMN 2.0 workflow process
│   └── leave-approval.form        # Camunda JSON form definition for manager task
├── backend/
│   ├── pom.xml                    # Maven configuration and Camunda 8.5 dependencies
│   ├── Dockerfile                 # Multi-stage JDK 21 builder & JRE 21 runtime
│   ├── mvnw                       # Linux / macOS Maven wrapper
│   ├── mvnw.cmd                   # Windows Maven wrapper
│   ├── .mvn/wrapper/
│   │   └── maven-wrapper.properties
│   └── src/
│       ├── main/
│       │   ├── java/com/example/leave/
│       │   │   ├── LeaveApplication.java             # Spring Boot startup & auto-deployer
│       │   │   ├── api/
│       │   │   │   ├── LeaveController.java          # REST API endpoints
│       │   │   │   ├── SubmitLeaveRequest.java       # DTO for leave submission
│       │   │   │   ├── DecisionRequest.java          # DTO for manager decision
│       │   │   │   └── ApiExceptionHandler.java      # Global JSON error handling
│       │   │   ├── domain/
│       │   │   │   ├── LeaveRequest.java             # JPA entity for leave requests
│       │   │   │   ├── LeaveBalance.java             # JPA entity for employee balance
│       │   │   │   ├── LeaveStatus.java              # Enum: SUBMITTED, PENDING, APPROVED, REJECTED
│       │   │   │   ├── LeaveType.java                # Enum: ANNUAL, SICK, UNPAID
│       │   │   │   ├── LeaveRequestRepository.java   # Spring Data JPA repository
│       │   │   │   └── LeaveBalanceRepository.java   # Spring Data JPA repository
│       │   │   ├── service/
│       │   │   │   └── LeaveService.java             # Business logic, working days & idempotency
│       │   │   └── worker/
│       │   │       └── LeaveWorkers.java             # Camunda Job Workers
│       │   └── resources/
│       │       ├── application.yml                   # App config, H2 & Camunda settings
│       │       └── bpmn/
│       │           ├── leave-request.bpmn            # Classpath workflow definition
│       │           └── leave-approval.form           # Classpath form definition
│       └── test/
│           ├── java/com/example/leave/
│           │   ├── LeaveControllerTest.java          # MockMvc HTTP contract tests
│           │   └── LeaveServiceTest.java             # Unit tests for business rules & idempotency
│           └── resources/
│               └── application-test.yml              # Offline test configuration
└── frontend/
    ├── package.json               # Frontend dependencies and build scripts
    ├── index.html                 # Semantic HTML5 entry with Inter font
    ├── vite.config.ts             # Vite server with /api proxy to 8081
    ├── tsconfig.json              # TypeScript strict configuration
    ├── Dockerfile                 # Multi-stage Node build & Nginx runtime
    ├── nginx.conf                 # Nginx production proxy configuration
    └── src/
        ├── main.tsx               # React root renderer
        ├── App.tsx                # Main layout, persona switcher & navigation
        ├── App.css                # Polished design system and responsive styles
        ├── api.ts                 # Typed API client and working-days calculator
        ├── ApplyForm.tsx          # Submission form with live days preview
        ├── MyRequests.tsx         # Employee request history & balance widget
        └── ManagerInbox.tsx       # Manager review table & decision modal
```

---

## 5. Prerequisites & Installation Instructions

### Required Software
1. **Java Development Kit (JDK) 21** or higher.
   - Verify: `java -version`
2. **Node.js 20+** and **npm 10+**.
   - Verify: `node -v` and `npm -v`
3. **Apache Maven 3.9+** (or use the provided `./mvnw` / `mvnw.cmd` wrapper).
4. **Docker & Docker Compose** (optional for running Camunda cluster locally).

---

## 6. Camunda Setup Instructions

### Option A: Running with Docker Compose (Recommended)
From the `leave-system` root folder, start Elasticsearch and Zeebe:
```bash
docker compose up -d elasticsearch zeebe operate
```
- **Zeebe Gateway**: `localhost:26500` (Plaintext)
- **Camunda Operate Web UI**: `http://localhost:8080` (Username: `demo`, Password: `demo`)

### Option B: Camunda 8 SaaS / Camunda Cloud
If using Camunda Cloud SaaS cluster:
1. Update `backend/src/main/resources/application.yml` with your cluster ID and client credentials:
   ```yaml
   camunda:
     client:
       mode: oidc
       cloud:
         cluster-id: <your-cluster-id>
         client-id: <your-client-id>
         client-secret: <your-client-secret>
         region: <your-region>
   ```

### Standalone / Local Offline Mode
The backend includes a graceful offline fallback. If Zeebe is not running, the application logs a warning on startup and safely executes local state persistence without crashing.

---

## 7. How to Run the Backend

1. Navigate to the `backend` folder:
   ```bash
   cd backend
   ```
2. Build and run unit tests:
   ```bash
   mvn clean test
   # Or using wrapper:
   ./mvnw clean test        # Linux / macOS
   mvnw.cmd clean test      # Windows
   ```
3. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   # Or using wrapper:
   ./mvnw spring-boot:run   # Linux / macOS
   mvnw.cmd spring-boot:run # Windows
   ```
4. Verify backend health:
   - Backend API is running on **`http://localhost:8081`**
   - H2 Database Console: **`http://localhost:8081/h2-console`**
     - JDBC URL: `jdbc:h2:file:./data/leavedb`
     - Username: `sa`
     - Password: *(leave blank)*

---

## 8. How to Run the Frontend

1. Navigate to the `frontend` folder:
   ```bash
   cd frontend
   ```
2. Install npm dependencies:
   ```bash
   npm install
   ```
3. Start the Vite development server:
   ```bash
   npm run dev
   ```
4. Access the application in your browser:
   - Open **`http://localhost:5173`**
   - All `/api/*` calls are automatically proxied to `http://localhost:8081`.

---

## 9. How to Run the Complete Stack with Docker

To build and run all 5 containers (Elasticsearch, Zeebe, Operate, Spring Boot Backend, and React Frontend):
```bash
docker compose up --build -d
```
Container Services:
- **Frontend Web UI**: `http://localhost:5173`
- **Backend API**: `http://localhost:8081/api/leaves`
- **Camunda Operate UI**: `http://localhost:8080`
- **Zeebe Gateway**: `localhost:26500`

To stop and remove containers:
```bash
docker compose down
```

---

## 10. REST API Contract & Example Payloads

### 1. Submit Leave Request
- **Endpoint**: `POST /api/leaves`
- **Status**: `201 Created`
- **Request Body**:
  ```json
  {
    "employeeName": "Alice Smith",
    "employeeEmail": "alice@example.com",
    "leaveType": "ANNUAL",
    "startDate": "2026-11-02",
    "endDate": "2026-11-06",
    "reason": "Annual family vacation"
  }
  ```
- **Response Body**:
  ```json
  {
    "id": 1,
    "employeeName": "Alice Smith",
    "employeeEmail": "alice@example.com",
    "leaveType": "ANNUAL",
    "startDate": "2026-11-02",
    "endDate": "2026-11-06",
    "workingDays": 5,
    "reason": "Annual family vacation",
    "status": "SUBMITTED",
    "statusMessage": "Submitted, awaiting workflow validation",
    "processInstanceKey": 2251799813685250,
    "createdAt": "2026-10-28T13:00:00"
  }
  ```

### 2. Get Employee Requests
- **Endpoint**: `GET /api/leaves?employeeEmail=alice@example.com`
- **Status**: `200 OK`
- **Response**: Array of `LeaveRequest` objects.

### 3. Get Pending Approvals (Manager Inbox)
- **Endpoint**: `GET /api/leaves/pending`
- **Status**: `200 OK`
- **Response**: Array of `LeaveRequest` objects currently in `PENDING_APPROVAL` status.

### 4. Submit Manager Decision
- **Endpoint**: `POST /api/leaves/{id}/decision`
- **Status**: `204 No Content` on success.
- **Status**: `409 Conflict` if the task is not awaiting approval or not yet ready.
- **Request Body**:
  ```json
  {
    "approved": true,
    "comment": "Approved! Have a great vacation."
  }
  ```

### 5. Check Leave Balance
- **Endpoint**: `GET /api/leaves/balance?employeeEmail=alice@example.com`
- **Status**: `200 OK`
- **Response Body**:
  ```json
  {
    "id": 1,
    "employeeEmail": "alice@example.com",
    "annualEntitlement": 20,
    "remainingDays": 15
  }
  ```

---

## 11. Test Scenarios Guide

### Test Case 1: Successful Annual Leave Approval
1. In the UI, select **Alice Smith (`alice@example.com`)**.
2. Notice the initial balance: **20 / 20 days**.
3. Choose dates: **Monday, Nov 2, 2026** to **Friday, Nov 6, 2026**.
4. Observe the live working-days preview shows **5 working days**.
5. Submit the application.
6. Switch to the **Manager Inbox** tab. The request will appear.
7. Click **Approve** and submit a comment.
8. Switch back to **My Requests**:
   - Status changes to **APPROVED**.
   - Remaining annual leave balance updates to **15 / 20 days**.

### Test Case 2: Insufficient Balance Rejection
1. Select an employee with only 5 days remaining.
2. Attempt to apply for 10 working days of **ANNUAL** leave.
3. The frontend immediately warns you of insufficient balance.
4. If forced via API, the backend `validate-leave` worker automatically rejects the request, marking status as `REJECTED` with message:
   `"Insufficient leave balance: requested 10 days, but remaining balance is 5 days."`

### Test Case 3: Invalid Dates & Weekend Exclusion
1. Set **Start Date**: `2026-11-06` (Friday) and **End Date**: `2026-11-10` (Tuesday).
2. The working-day calculation correctly counts **3 working days** (skipping Saturday and Sunday).
3. If End Date is set earlier than Start Date, the system blocks the submission with:
   `"End date cannot be before start date"`.
4. If dates fall solely on a Saturday and Sunday, the system blocks the submission with:
   `"Leave request must contain at least 1 working day (excluding weekends)"`.

### Test Case 4: Sick Leave (No Balance Deduction)
1. Apply for 3 days of **SICK** leave.
2. Manager approves the request.
3. Notice that the annual leave balance remains unchanged, honoring the requirement that sick and unpaid leaves do not deplete annual entitlement.

### Test Case 5: Finalization Idempotency
1. If a network blip causes Camunda to retry the `finalize-leave` task for an approved leave request, the `balanceDeducted` boolean flag prevents the balance from being deducted a second time.

---

## 12. Inspecting Workflows in Camunda Operate

1. Open **Camunda Operate** at `http://localhost:8080`.
2. Login with credentials:
   - **Username**: `demo`
   - **Password**: `demo`
3. Click on the process definition **`Leave Request Workflow`** (`leave-request`).
4. You can observe:
   - Live visual tokens showing current process instance positions.
   - The token waiting at the **Manager Approval** user task.
   - Values of all process variables (`requestId`, `days`, `valid`, `approved`, `outcome`).
   - Incident history and retry states.

---

## 13. Common Troubleshooting

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| **Connection refused on 26500** | Zeebe broker is not running. | Run `docker compose up -d zeebe` or use the backend's standalone offline mode. |
| **HTTP 409 on `/decision`** | Request is not in `PENDING_APPROVAL` or user task has not yet activated. | Allow Zeebe 1–2 seconds to transition the instance from start to user task before approving. |
| **CORS errors in browser** | Browser directly contacting backend without proxy. | Run frontend via `http://localhost:5173`; Vite proxies `/api` to `http://localhost:8081`. |
| **Port 8081 in use** | Another instance of Spring Boot or another app is running. | Stop previous process or change `server.port` in `application.yml`. |

---

## 14. GitHub Upload Instructions

To upload this clean, working repository to your GitHub account:

1. Open a terminal in the root `leave-system` folder:
   ```bash
   cd leave-system
   ```
2. Initialize Git repository:
   ```bash
   git init
   git add .
   git commit -m "feat: complete runnable Leave Application System with Camunda 8, Spring Boot 3, and React"
   ```
3. Create a new repository on [GitHub](https://github.com/new) (e.g. `leave-application-system`).
4. Link and push your code:
   ```bash
   git remote add origin https://github.com/<your-username>/leave-application-system.git
   git branch -M main
   git push -u origin main
   ```

*(Note: Target folders, `node_modules`, `dist`, and H2 database files are automatically excluded by `.gitignore`.)*

---

## 15. Security Limitations & Future Improvements

- **Authentication & RBAC**: Currently utilizes a persona selector for development and assignment grading. In production, integrate Spring Security with Keycloak / OAuth2 OIDC for JWT-based role authorization (Employee vs. Manager).
- **Public Holidays**: Working days currently excludes Saturdays and Sundays. Production systems can integrate regional holiday calendars (e.g., using `Jollyday` or external calendar APIs).
- **Audit Trail**: Add JPA Auditing / Envers for historical revision tracking.
