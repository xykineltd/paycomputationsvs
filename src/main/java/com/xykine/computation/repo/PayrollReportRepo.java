package com.xykine.computation.repo;

import com.xykine.computation.entity.PayrollReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PayrollReportRepo extends JpaRepository<PayrollReport, Long> {
    PayrollReport findPayrollReportByStartDateAndPayrollSimulation(LocalDate startDate, boolean simulation);
    List<PayrollReport> findAllByOrderByCreatedDateAsc();
     void deletePayrollReportByStartDate(LocalDate startDate);
     void deletePayrollReportsByPayrollSimulation(Boolean simulation);
}


//[{
//        payrollPeriod: getFormattedPayrolPeriod(report?.start, report?.end),
//        PayDate: "N/A",
//        totalGrossPay: getTotalGrossPay(report),
//        status: report.payrollApproved ? "Completed" : "Pending",
//        isSimulated: report?.payrollSimulation,
//        isApproved: report?.payrollApproved,
//        startDate: report?.start
//        createdDate: report?.start
//        }]