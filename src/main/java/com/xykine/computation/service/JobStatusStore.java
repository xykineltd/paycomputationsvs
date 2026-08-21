package com.xykine.computation.service;

import com.xykine.computation.domain.JobStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobStatusStore {
    private final Map<String, JobStatus> store = new ConcurrentHashMap<>();

    public void createJob(String jobId) {
        store.put(jobId, new JobStatus(jobId, "QUEUED", "Job queued for execution", ""));
    }

    public void updateJob(String jobId, String status, String message, String reportId) {
        store.computeIfPresent(jobId, (id, job) -> {
            job.setStatus(status);
            job.setMessage(message);
            job.setReportId(reportId);
            job.setReportId(reportId);
            return job;
        });
    }

    public JobStatus getJob(String jobId) {
        return store.get(jobId);
    }
}

