package com.xykine.computation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.domain.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Job status store backed by Redis when available, with an in-memory fallback
 * for local/test environments without Redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobStatusStore {

    private static final String KEY_PREFIX = "payroll:job:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JobStatus> localFallback = new ConcurrentHashMap<>();

    @Value("${xykine.job-status.ttl-hours:24}")
    private long ttlHours;

    public void createJob(String jobId) {
        createJob(jobId, null);
    }

    public void createJob(String jobId, String companyId) {
        JobStatus status = new JobStatus(jobId, "QUEUED", "Job queued for execution", "", companyId);
        persist(jobId, status);
    }

    public void updateJob(String jobId, String status, String message, String reportId) {
        JobStatus existing = getJob(jobId);
        if (existing == null) {
            return;
        }
        existing.setStatus(status);
        existing.setMessage(message);
        existing.setReportId(reportId);
        persist(jobId, existing);
    }

    public JobStatus getJob(String jobId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + jobId);
            if (json != null) {
                return objectMapper.readValue(json, JobStatus.class);
            }
        } catch (Exception e) {
            log.debug("Redis job status read failed, using local fallback: {}", e.getMessage());
        }
        return localFallback.get(jobId);
    }

    private void persist(String jobId, JobStatus status) {
        localFallback.put(jobId, status);
        try {
            stringRedisTemplate.opsForValue().set(
                    KEY_PREFIX + jobId,
                    objectMapper.writeValueAsString(status),
                    Duration.ofHours(ttlHours)
            );
        } catch (Exception e) {
            log.debug("Redis job status write failed, using local fallback only: {}", e.getMessage());
        }
    }
}
