# shedlock-distributed-job-coordination

![CI](https://github.com/xMesas/shedlock-distributed-job-coordination/actions/workflows/ci.yml/badge.svg)

## In plain English

Any Spring Boot app with a `@Scheduled` job that gets deployed as more than one
instance (multiple pods behind a load balancer, an autoscaled instance group,
even just two instances during a rolling deploy) has a real, easy-to-miss problem:
**every instance runs the job, on every tick.** For something idempotent that
might be harmless. For a nightly report, a batch email send, or a billing run,
it means duplicates. This project reproduces that bug on demand - real concurrent
callers, not a thought experiment - and fixes it with
[ShedLock](https://github.com/lukas-krecan/shedlock), backed by a real Postgres
table that any number of instances can safely race against.

## What actually got measured

Real run: started the app against a real `docker-compose`-launched Postgres, then
fired **10 real concurrent HTTP requests** at each endpoint (`for i in 1..10; do
curl ... & done; wait` - genuine parallel requests, not a sequential loop):

```bash
$ for i in $(seq 1 10); do curl -s -X POST "localhost:8080/jobs/naive/run?instanceId=instance-$i" & done; wait
$ curl -s localhost:8080/jobs/counts
{"lockedExecutionCount":0,"naiveExecutionCount":10}

$ for i in $(seq 1 10); do curl -s -X POST "localhost:8080/jobs/locked/run?instanceId=instance-$i" & done; wait
$ curl -s localhost:8080/jobs/counts
{"lockedExecutionCount":1,"naiveExecutionCount":10}
```

**10 simulated instances, calling the naive job: all 10 execute.** The exact same
10 simulated instances, calling the ShedLock-protected job: **exactly 1 executes**.
Confirmed directly against the database, not just the counters:

```
                          shedlock table
      name      |       lock_until        |        locked_at        | locked_by
-----------------+-------------------------+-------------------------+-----------
 generateReport  | 2026-07-22 13:43:24.899 | 2026-07-22 13:43:23.904 | MesPc

                        report_log rows (grouped)
 executed_by | count
-------------+-------
 instance-1  |     2   <- once from the naive run, once as the ONE winner of the locked race
 instance-2  |     1   <- naive only
 ...         |     1   <- (9 more instances, naive only)
```

11 total rows for 11 total calls that actually ran their body (10 naive + 1
locked) - `instance-1` happened to also win the locked race, which is exactly
why it shows count 2 while every other instance shows count 1.

## Approach

- `V1__create_shedlock_table.sql` - the exact schema ShedLock's JDBC provider
  requires (from ShedLock's own docs): a `shedlock` table where a real Postgres row
  is the actual coordination mechanism, not an in-JVM lock.
- `ShedLockConfig` - `@EnableSchedulerLock` plus a `JdbcTemplateLockProvider` bean
  backed by the app's real `DataSource`.
- `NaiveReportJob` - no coordination at all. Stands in for what a `@Scheduled`
  method looks like today, before ShedLock is added.
- `LockedReportJob` - the identical job, wrapped with
  `@SchedulerLock(name = "generateReport", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")`.
  Every concurrent caller that doesn't win the lock is simply **skipped** - the
  method body never runs for them - not queued, not blocked, not retried.
- `NightlyCleanupJob` - a real `@Scheduled` + `@SchedulerLock` method, included
  purely to show the idiomatic production usage pattern a reader would actually
  copy (the concurrency proof itself calls `LockedReportJob`/`NaiveReportJob`
  directly, so the race condition is genuinely deterministic rather than waiting
  on a real scheduler tick).
- `SchedulerLockConcurrencyIT` - real Postgres via Testcontainers, 10 threads
  rendezvoused with a `CountDownLatch` so all "simulated instances" race at the
  same real moment, calling the naive job (asserting all 10 execute) and the
  locked job (asserting exactly 1 does).

## Architecture decisions

- **"Instances" are simulated as concurrent threads calling the same Spring bean,
  not separate JVMs or Docker containers.** ShedLock's actual guarantee comes from
  a row in a real, shared Postgres table - that guarantee is identical whether the
  concurrent callers are threads in one process or genuinely separate app
  instances on separate machines. Simulating it with threads keeps the proof fast
  and deterministic without needing to actually orchestrate multiple containers.
- **`lockAtLeastFor = "PT1S"` on the locked job**, even though the job itself
  finishes near-instantly. Without a minimum hold time, a job that finishes fast
  enough could theoretically let a second "instance" acquire the lock again
  almost immediately after - `lockAtLeastFor` guarantees the lock is held for a
  sensible minimum window regardless of how fast the job itself completes,
  which is exactly what you want for a job that might run on a schedule shorter
  than its own real execution time in production.
- **A separate `@Scheduled` example (`NightlyCleanupJob`) purely for illustration.**
  The automated proof needs deterministic, on-demand execution (not "wait for the
  next real scheduler tick"), so it calls the job methods directly - but a reader
  copying this pattern into their own app needs to see the real annotation-based
  usage too.

## Stack

Java 21, Spring Boot 3.4.2, ShedLock 6.3.0 (`shedlock-spring` +
`shedlock-provider-jdbc-template`), PostgreSQL 16, Flyway, Testcontainers.

## Running it

```bash
docker compose up -d
./mvnw spring-boot:run
# in another shell:
for i in $(seq 1 10); do curl -s -X POST "localhost:8080/jobs/naive/run?instanceId=instance-$i" & done; wait
for i in $(seq 1 10); do curl -s -X POST "localhost:8080/jobs/locked/run?instanceId=instance-$i" & done; wait
curl localhost:8080/jobs/counts
```

**Note on Testcontainers locally**: this machine's Docker Desktop has a known
npipe-related incompatibility with the Testcontainers Java client (documented
elsewhere across this portfolio) - confirmed again here
(`SchedulerLockConcurrencyIT` fails locally with
`Could not find a valid Docker environment`). Validated manually via
`docker compose` + concurrent curl above instead; the real Testcontainers run
happens in GitHub Actions CI.

## Status

- [x] Real, reproducible duplicate execution across simulated instances (the bug)
- [x] Real ShedLock fix, verified to run exactly once, confirmed at the database
      level (not just the in-memory counter)
- [x] A real `@Scheduled` + `@SchedulerLock` example for the idiomatic usage pattern
- [x] CI green with a real Testcontainers-backed Postgres

## Notes / next steps

- Only the "skip if not lock owner" semantics are demonstrated (ShedLock's
  default and most common mode). ShedLock also supports lock extension for
  long-running jobs, which isn't covered here.
