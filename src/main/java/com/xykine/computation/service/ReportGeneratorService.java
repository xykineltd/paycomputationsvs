package com.xykine.computation.service;

import com.xykine.computation.request.ReportRequestPayload;

public interface ReportGeneratorService {

    void generateReport(ReportRequestPayload reportRequestPayload);

}
