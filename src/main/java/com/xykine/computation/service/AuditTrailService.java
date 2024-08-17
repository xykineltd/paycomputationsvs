package com.xykine.computation.service;

import java.util.Map;

import org.xykine.payroll.model.AuditTrailEvents;

public interface AuditTrailService {
    public void logEvent(AuditTrailEvents eventType, String detail);
    public Map<String, Object> getUserEvents(String userId, int page, int size);
    public Map<String, Object> getAllEvents(int page, int size);
}
