package com.xykine.computation.repo;

import com.xykine.computation.entity.YTDReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface YTDReportRepo extends MongoRepository<YTDReport,String> {
    Optional<YTDReport> findYTDReportByEmployeeIdAndCompanyId(String employeeId, String companyId);
    List<YTDReport> findYTDReportByEmployeeIdInAndCompanyId(List<String> employeeId, String companyId);
}
