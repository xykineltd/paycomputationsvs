package com.xykine.computation.controller;

import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.service.ReportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class Report {

    private final ReportPersistenceService reportPersistenceService;

    @GetMapping("/startDate/{startDate}")
    public PaymentComputeResponse computePayroll(@PathVariable String startDate) throws IOException, ClassNotFoundException {
        return reportPersistenceService.getPayRollReport(startDate);
    }
}
