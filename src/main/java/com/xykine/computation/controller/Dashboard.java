package com.xykine.computation.controller;

import com.xykine.computation.response.DashboardCardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xykine.computation.service.DashboardDataService;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.util.Map;


@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class Dashboard {

    private final DashboardDataService dashboardDataService;

    @GetMapping("/card")
    public ResponseEntity<?> getUserTrail() {
        DashboardCardResponse response = dashboardDataService.retrieveDashboardData();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/graph")
    public ResponseEntity<?> getPaymentDetails(
            @RequestParam(defaultValue = "") PaymentFrequencyEnum paymentFrequency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Map<String, Object> response = dashboardDataService.getDashboardGraph(paymentFrequency, page, size);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}