package com.xykine.computation.controller;

import lombok.RequiredArgsConstructor;

import com.xykine.computation.service.AuditTrailService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/compute")
@RequiredArgsConstructor
public class Audit {

    private final AuditTrailService auditTrailService;

//    @GetMapping("/user-trail")
//    public ResponseEntity<?> getUserTrail(
//            @RequestParam() String userId,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "3") int size) {
//        Map<String, Object> response = auditTrailService.getUserEvents(userId,page, size);
//        return new ResponseEntity<>(response, HttpStatus.OK);
//    }

    @GetMapping("/user-trail")
    public ResponseEntity<?> getUserTrailByDate(
            @RequestParam() String name,
            @RequestParam() String companyId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {

        // Set default startDate to a very early date (or the earliest date in your data range)
        LocalDate startLocalDate = (startDate != null) ? LocalDate.parse(startDate) : LocalDate.of(1900, 1, 1);

        // Set default endDate to the current date
        LocalDate endLocalDate = (endDate != null) ? LocalDate.parse(endDate) : LocalDate.now();

        Map<String, Object> response = auditTrailService.getUserEvents(name, startLocalDate, endLocalDate, companyId, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/audit-trail")
    public ResponseEntity<?> getAuditTrails(
            @RequestParam() String companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {
        Map<String, Object> response = auditTrailService.getAllEvents(companyId, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
