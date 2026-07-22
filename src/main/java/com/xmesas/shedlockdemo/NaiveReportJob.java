package com.xmesas.shedlockdemo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * No coordination at all - stands in for what happens today if this same @Scheduled-shaped job
 * ran on N instances of the same app (e.g. N pods behind a load balancer) with no distributed
 * lock: every single instance runs it, on every tick. "instanceId" is just a label distinguishing
 * simulated callers in this demo/test - the bug is real regardless of how many instances exist.
 */
@Component
public class NaiveReportJob {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger executionCount = new AtomicInteger();

    public NaiveReportJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void generateReport(String instanceId) {
        jdbcTemplate.update("INSERT INTO report_log(executed_by) VALUES (?)", instanceId);
        executionCount.incrementAndGet();
    }

    public int executionCount() {
        return executionCount.get();
    }
}
