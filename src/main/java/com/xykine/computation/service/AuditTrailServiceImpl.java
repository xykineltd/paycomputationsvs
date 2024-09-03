package com.xykine.computation.service;


import com.xykine.computation.response.AuditTrailResponse;
import com.xykine.computation.utils.ReportUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.xykine.computation.entity.AuditTrail;
import com.xykine.computation.repo.AuditTrailRepo;
import com.xykine.computation.utils.AuthUtil;
import org.xykine.payroll.model.AuditTrailEvents;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditTrailServiceImpl implements AuditTrailService{

    private final AuditTrailRepo auditTrailRepo;
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCalculatorImpl.class);

    @Override
    public void logEvent(AuditTrailEvents eventType, String detail, String companyId) {
        String employeeId = AuthUtil.getCurrentUser();
        String name = AuthUtil.getUserName();
//        String companyId = AuthUtil.getCompanyId();
        AuditTrail auditTrail = AuditTrail.builder()
                .id(UUID.randomUUID().toString())
                .companyId(companyId)
                .employeeId(employeeId)
                .name(name)
                .event(eventType)
                .details(detail)
                .dateTime(LocalDateTime.now())
                .build();
        auditTrailRepo.save(auditTrail);
    }

//    @Override
//    public Map<String, Object> getUserEvents(String userId, int page, int size) {
//        Pageable paging = PageRequest.of(page, size);
////        Page<AuditTrail> auditTrailPage = auditTrailRepo.findAuditTrailByUserId(userId, paging);
////        Page<AuditTrail> auditTrailPage = auditTrailRepo.findAuditTrailByUserIdContaining(userId, paging);
//        List<AuditTrail> auditTrails = auditTrailPage.getContent();
//        List<AuditTrailResponse> auditTrailResponses = ReportUtils.transformAuditTrail(auditTrails);
//        Map<String, Object> response = new HashMap<>();
//        response.put("auditTrailDetails", auditTrailResponses);
//        response.put("currentPage", auditTrailPage.getNumber());
//        response.put("totalItems", auditTrailPage.getTotalElements());
//        response.put("totalPages", auditTrailPage.getTotalPages());
//        return response;
//    }

    @Override
    public Map<String, Object> getUserEvents(String name, LocalDate startDate, LocalDate endDate, String companyId, int page, int size) {
        Pageable paging = PageRequest.of(page, size);
//        Page<AuditTrail> auditTrailPage = auditTrailRepo.findAuditTrailByUserId(userId, paging);
        LocalDateTime startDateTime = startDate.atStartOfDay(); // Start of the day (00:00:00)
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX); //

        Page<AuditTrail> auditTrailPage = auditTrailRepo.findByNameContainingIgnoreCaseAndDateTimeBetweenAndCompanyId(name, startDateTime, endDateTime, companyId, paging);
        List<AuditTrail> auditTrails = auditTrailPage.getContent();
        List<AuditTrailResponse> auditTrailResponses = ReportUtils.transformAuditTrail(auditTrails);
        Map<String, Object> response = new HashMap<>();
        response.put("auditTrailDetails", auditTrailResponses);
        response.put("currentPage", auditTrailPage.getNumber());
        response.put("totalItems", auditTrailPage.getTotalElements());
        response.put("totalPages", auditTrailPage.getTotalPages());
        return response;
    }

    @Override
    public Map<String, Object> getAllEvents(String companyId,int page, int size) {
        Pageable paging = PageRequest.of(page, size);
        Page<AuditTrail> auditTrailPage = auditTrailRepo.findByCompanyIdOrderByDateTimeDesc(companyId, paging);
        List<AuditTrail> auditTrails = auditTrailPage.getContent();
        List<AuditTrailResponse> auditTrailResponses = ReportUtils.transformAuditTrail(auditTrails);
        Map<String, Object> response = new HashMap<>();
        response.put("auditTrailDetails", auditTrailResponses);
        response.put("currentPage", auditTrailPage.getNumber());
        response.put("totalItems", auditTrailPage.getTotalElements());
        response.put("totalPages", auditTrailPage.getTotalPages());
        return response;
    }
}
