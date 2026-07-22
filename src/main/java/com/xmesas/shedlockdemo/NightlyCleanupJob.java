package com.xmesas.shedlockdemo;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The idiomatic, real-world usage pattern this whole project exists to demonstrate: a normal
 * Spring @Scheduled method, with @SchedulerLock added so that if this app is ever deployed as
 * more than one instance (the entire point of a distributed lock), only one instance's tick
 * actually runs the job. This class isn't exercised by the automated concurrency proof (that
 * lives in LockedReportJob/NaiveReportJob, called directly to make the race condition
 * deterministic) - it's here purely to show the annotation-based pattern a reader would copy.
 */
@Component
public class NightlyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyCleanupJob.class);

    @Scheduled(fixedDelayString = "PT1H")
    @SchedulerLock(name = "nightlyCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() {
        log.info("Running nightly cleanup - only one instance will ever log this per tick");
    }
}
