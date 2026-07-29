package com.xykine.computation.service;

import com.xykine.computation.domain.LoanStatus;
import com.xykine.computation.dto.LoanFilter;
import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.entity.Loan;
import com.xykine.computation.entity.PaymentSettingMetaData;
import com.xykine.computation.entity.Tax;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.repo.PaymentSettingMetadataRepo;
import com.xykine.computation.repo.TaxRepo;
import com.xykine.computation.session.PayrollCalculationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a job-scoped {@link PayrollCalculationContext} to avoid N+1 lookups in the calculator.
 */
@Service
@RequiredArgsConstructor
public class PayrollCalculationContextFactory {

    private static final String DEFAULT_TAX_COUNTRY = "NIGERIA";

    private final CompanyMetadataService companyMetadataService;
    private final EmployeeMetadataService employeeMetadataService;
    private final TaxRepo taxRepo;
    private final LoanService loanService;
    private final PaymentSettingMetadataRepo paymentSettingMetadataRepo;

    public PayrollCalculationContext build(String companyId, List<PaymentInfo> paymentInfoList) {
        CompanyMetadata company = companyMetadataService.getByCompanyId(companyId)
                .orElseThrow(() -> new PayrollValidationException(
                        "Company metadata not found for companyId=" + companyId));

        Tax tax = taxRepo.findTaxByCountryAndActiveIsTrue(DEFAULT_TAX_COUNTRY);
        if (tax == null) {
            throw new PayrollValidationException("Active tax configuration not found for " + DEFAULT_TAX_COUNTRY);
        }

        Map<String, EmployeeMetadata> employees = new ConcurrentHashMap<>();
        employeeMetadataService.findByCompanyId(companyId)
                .forEach(e -> employees.put(e.getEmployeeId(), e));

        // Ensure employees in this run are present (and fail fast if missing)
        for (PaymentInfo info : paymentInfoList) {
            if (!employees.containsKey(info.getEmployeeID())) {
                employeeMetadataService.getByEmployeeId(info.getEmployeeID())
                        .ifPresentOrElse(
                                e -> employees.put(e.getEmployeeId(), e),
                                () -> {
                                    throw new PayrollValidationException(
                                            "Employee metadata not found for employeeId=" + info.getEmployeeID());
                                });
            }
        }

        Map<String, List<Loan>> loansByEmployee = new ConcurrentHashMap<>();
        Map<String, List<PaymentSettingMetaData>> settingsByEmployee = new ConcurrentHashMap<>();

        for (PaymentInfo info : paymentInfoList) {
            String employeeId = info.getEmployeeID();
            LoanFilter filter = new LoanFilter();
            filter.setCompanyId(companyId);
            filter.setEmployeeId(employeeId);
            filter.setStatus(LoanStatus.APPROVED);
            List<Loan> loans = loanService
                    .getLoans(filter, java.time.LocalDate.parse(info.getStartDate()), Pageable.unpaged())
                    .getContent();
            loansByEmployee.put(employeeId, loans);

            List<PaymentSettingMetaData> settings =
                    paymentSettingMetadataRepo.findByEmployeeIdAndCompanyId(employeeId, companyId);
            settingsByEmployee.put(employeeId, settings);
        }

        return PayrollCalculationContext.builder()
                .companyId(companyId)
                .companyMetadata(company)
                .tax(tax)
                .salaryFrequency(company.getSalaryFrequency() != null
                        ? company.getSalaryFrequency()
                        : PaymentFrequencyEnum.MONTHLY)
                .paymentEntryMode(company.getPaymentEntryMode() != null
                        ? company.getPaymentEntryMode()
                        : PaymentFrequencyEnum.YEARLY)
                .paymentDistributionJson(company.getPaymentDistribution())
                .employeeMetadataById(employees)
                .loansByEmployeeId(loansByEmployee)
                .paymentSettingsByEmployeeId(settingsByEmployee)
                .build();
    }
}
