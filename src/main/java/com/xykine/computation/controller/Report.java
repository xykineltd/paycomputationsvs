package com.xykine.computation.controller;

import com.xykine.computation.request.UpdateReportRequest;
import com.xykine.computation.response.PaymentComputeResponse;
import com.xykine.computation.service.ReportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class Report {

    private final ReportPersistenceService reportPersistenceService;

    @GetMapping("/all")
    public List<PaymentComputeResponse> getReports() {
        return reportPersistenceService.getPayRollReports();
    }

    @PostMapping
    public PaymentComputeResponse getReport(@RequestBody String startDate) {
        return reportPersistenceService.getPayRollReport(startDate);
    }


    @PutMapping
    public boolean updateReport(@RequestBody UpdateReportRequest request) {
        return reportPersistenceService.updateReport(request);
    }
}
