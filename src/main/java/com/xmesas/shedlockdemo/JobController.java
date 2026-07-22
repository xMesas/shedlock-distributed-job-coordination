package com.xmesas.shedlockdemo;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class JobController {

    private final NaiveReportJob naiveReportJob;
    private final LockedReportJob lockedReportJob;

    public JobController(NaiveReportJob naiveReportJob, LockedReportJob lockedReportJob) {
        this.naiveReportJob = naiveReportJob;
        this.lockedReportJob = lockedReportJob;
    }

    @PostMapping("/jobs/naive/run")
    public void runNaive(@RequestParam String instanceId) {
        naiveReportJob.generateReport(instanceId);
    }

    @PostMapping("/jobs/locked/run")
    public void runLocked(@RequestParam String instanceId) {
        lockedReportJob.generateReport(instanceId);
    }

    @GetMapping("/jobs/counts")
    public Map<String, Integer> counts() {
        return Map.of(
                "naiveExecutionCount", naiveReportJob.executionCount(),
                "lockedExecutionCount", lockedReportJob.executionCount()
        );
    }
}
