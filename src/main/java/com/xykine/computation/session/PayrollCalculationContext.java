package com.xykine.computation.session;

import com.xykine.computation.entity.CompanyMetadata;
import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.entity.Loan;
import com.xykine.computation.entity.PaymentSettingMetaData;
import com.xykine.computation.entity.Tax;
import lombok.Builder;
import lombok.Getter;
import org.xykine.payroll.model.PaymentFrequencyEnum;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Job-scoped calculation inputs shared across parallel employee workers.
 * Company/tax data is loaded once; employee-specific maps are filled during preload.
 */
@Getter
@Builder
public class PayrollCalculationContext {

    private final String companyId;
    private final CompanyMetadata companyMetadata;
    private final Tax tax;
    private final PaymentFrequencyEnum salaryFrequency;
    private final String paymentDistributionJson;
    private final PaymentFrequencyEnum paymentEntryMode;

    @Builder.Default
    private final Map<String, EmployeeMetadata> employeeMetadataById = new ConcurrentHashMap<>();

    @Builder.Default
    private final Map<String, List<Loan>> loansByEmployeeId = new ConcurrentHashMap<>();

    @Builder.Default
    private final Map<String, List<PaymentSettingMetaData>> paymentSettingsByEmployeeId = new ConcurrentHashMap<>();

    public EmployeeMetadata requireEmployeeMetadata(String employeeId) {
        EmployeeMetadata meta = employeeMetadataById.get(employeeId);
        if (meta == null) {
            throw new IllegalStateException("Employee metadata missing from calculation context for " + employeeId);
        }
        return meta;
    }

    public List<Loan> loansFor(String employeeId) {
        return loansByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
    }

    public List<PaymentSettingMetaData> paymentSettingsFor(String employeeId) {
        return paymentSettingsByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
    }
}
