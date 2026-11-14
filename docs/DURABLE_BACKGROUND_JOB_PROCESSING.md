# Durable Background Job Processing Architecture

## 1. Executive Summary and Operational Guarantees

Originality Desk decouples interactive HTTP request handling from compute-intensive operations (document text extraction, OCR rendering, deep embedding inference, and n-gram shingle comparison) through an asynchronous, crash-resilient background processing subsystem.

### Core Architectural Guarantees
- **Durability across Restarts**: Job states and metadata are persisted in the SQL database (`jobs` table). Worker restarts never drop submitted tasks.
- **Zero Duplicate Execution**: Jobs are claimed atomically via conditional updates (`UPDATE ... WHERE status='QUEUED'`), ensuring that multiple workers or cluster nodes never process the same job concurrently.
- **Heartbeat Monitoring & Dead Worker Recovery**: Active workers hold a time-bounded lease. If a worker crashes or hangs, stale jobs are automatically identified and safely re-queued.
- **Exponential Backoff with Jitter**: Transient lock contention or I/O timeouts trigger non-linear retry intervals with randomized jitter, preventing thundering-herd spikes.
- **Dead-Letter Handling**: Tasks exceeding maximum execution attempts are routed to terminal `FAILED` / dead-letter status with comprehensive failure diagnostics.

---

## 2. Job Lifecycle State Machine

```
      [ Enqueue ]
           │
           ▼
     ┌───────────┐
     │  QUEUED   │◄───────────────────────┐
     └─────┬─────┘                        │
           │ Atomic Claim                 │ Transient Error
           ▼                              │ (attempts < max)
     ┌───────────┐                        │
     │  RUNNING  ├─ Heartbeat Renew ──┐   │
     └─────┬─────┘                    │   │
           │                          └───┘
     ┌─────┴──────────────────┬─────────────────┐
     ▼                        ▼                 ▼
┌───────────┐          ┌──────────────┐   ┌───────────┐
│ COMPLETED │          │ FAILED (DLQ) │   │ CANCELLED │
└───────────┘          └──────────────┘   └───────────┘
```

| State | Classification | Description |
|---|---|---|
| `QUEUED` | Active | Job is pending in queue, ready to be claimed once `available_at <= now()`. |
| `RUNNING` | Active | Job has been atomically claimed by a worker with an active lease. |
| `RETRYING` | Active | Job encountered a transient error and is waiting for its exponential backoff delay to elapse. |
| `COMPLETED`| Terminal | Job finished successfully; output snapshot persisted. |
| `FAILED` | Terminal | Permanent failure or exceeded maximum retry attempts (Dead-Letter Queue). |
| `CANCELLED`| Terminal | Job was explicitly cancelled by user or invalidated by document deletion. |

---

## 3. Worker Lease and Heartbeat Model

1. **Atomic Claim**:
   ```sql
   UPDATE jobs SET status='RUNNING', attempts=attempts+1, error=NULL
   WHERE id=? AND workspace_id=? AND status='QUEUED'
   ```
2. **Heartbeat Renewal**: While long-running computations proceed, workers periodically touch their lease:
   ```sql
   UPDATE jobs SET available_at=? WHERE id=? AND workspace_id=? AND status='RUNNING'
   ```
3. **Stale Recovery**: On startup and periodic maintenance ticks, `recoverStaleJobs(staleThresholdMillis)` checks for running jobs without recent heartbeats. Abandoned jobs are safely re-queued or routed to `FAILED`.

---

## 4. Exponential Backoff Policy

Implemented by `com.writingassistant.jobs.BackoffPolicy`:

$$\Delta t = \min(t_{\text{max}}, t_{\text{base}} \times \mu^{\text{attempt}-1}) + \text{jitter}$$

- $t_{\text{base}} = 1000\text{ ms}$
- $t_{\text{max}} = 300,000\text{ ms}$ (5 minutes)
- $\mu = 2.0$
- $\text{jitter} = 10\%$ randomization

---

## 5. Verification and Test Suite

Verified via `DurableJobQueueTest`:
1. `backoffPolicyCalculations`: Confirms exponential progression ($1\text{s} \to 2\text{s} \to 4\text{s} \dots$) and strict adherence to maximum delay caps.
2. `jobLeaseLifecycle`: Verifies lease acquisition, heartbeat renewal, and expiration calculation.
3. `enqueueClaimAndCompleteJob`: Verifies end-to-end enqueue, atomic single-worker claim, heartbeat, and successful completion.
4. `retryAndTerminalFailure`: Simulates transient failure, verifies exponential delay scheduling, and confirms terminal failure once attempts reach the maximum threshold.
5. `staleJobRecovery`: Simulates a worker crash leaving a job in `RUNNING`, confirming automatic detection and recovery back to `QUEUED`.
