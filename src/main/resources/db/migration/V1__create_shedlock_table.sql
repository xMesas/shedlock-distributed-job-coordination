-- ShedLock's own required schema for its JDBC lock provider (from ShedLock's own docs) -
-- a real Postgres row is what actually enforces mutual exclusion here, not an in-JVM mutex.
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

CREATE TABLE report_log (
    id BIGSERIAL PRIMARY KEY,
    executed_by VARCHAR(255) NOT NULL,
    executed_at TIMESTAMP NOT NULL DEFAULT now()
);
