package com.xmesas.shedlockdemo;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Same job, same table, but @SchedulerLock wraps the call in a real Postgres-backed lock
 * acquisition first. Whichever simulated instance wins the race actually runs the method; every
 * other concurrent caller is simply skipped (the method body never executes for them) rather than
 * queued or blocked - exactly the semantics a real scheduled job wants: run once per tick, not
 * once per instance.
 */
@Component
public class LockedReportJob {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger executionCount = new AtomicInteger();

    public LockedReportJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @SchedulerLock(name = "generateReport", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
    public void generateReport(String instanceId) {
        jdbcTemplate.update("INSERT INTO report_log(executed_by) VALUES (?)", instanceId);
        executionCount.incrementAndGet();
    }

    public int executionCount() {
        return executionCount.get();
    }
}
