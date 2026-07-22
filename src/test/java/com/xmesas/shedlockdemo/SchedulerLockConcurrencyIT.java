package com.xmesas.shedlockdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Simulated instances" are separate threads racing to call the SAME real Postgres-backed lock -
 * this genuinely proves ShedLock's guarantee, since the mechanism being tested (a row in a real
 * "shedlock" table) works identically whether the concurrent callers are threads in one JVM or
 * separate app instances on separate machines. A CountDownLatch-based rendezvous makes sure all
 * "instances" actually race at the same real moment, not just run one after another.
 */
@SpringBootTest
@Testcontainers
class SchedulerLockConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("shedlockdemo")
            .withUsername("shedlock")
            .withPassword("shedlock");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    NaiveReportJob naiveReportJob;

    @Autowired
    LockedReportJob lockedReportJob;

    @Test
    void naiveJobRunsOnceForEverySimulatedInstance_theBug() throws Exception {
        int simulatedInstances = 10;
        runConcurrently(simulatedInstances, naiveReportJob::generateReport);

        assertThat(naiveReportJob.executionCount()).isEqualTo(simulatedInstances);
    }

    @Test
    void lockedJobRunsExactlyOnceAcrossAllSimulatedInstances_theFix() throws Exception {
        int simulatedInstances = 10;
        runConcurrently(simulatedInstances, lockedReportJob::generateReport);

        assertThat(lockedReportJob.executionCount()).isEqualTo(1);
    }

    private void runConcurrently(int instanceCount, Consumer<String> task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(instanceCount);
        CountDownLatch ready = new CountDownLatch(instanceCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(instanceCount);

        for (int i = 0; i < instanceCount; i++) {
            String instanceId = "instance-" + i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.accept(instanceId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }
}
