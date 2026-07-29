package com.xykine.computation.service.calculator;

import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.entity.EmployeeType;
import com.xykine.computation.session.PayrollSessionHolder;
import com.xykine.computation.utils.ComputationUtils;
import com.xykine.computation.utils.PayrollMapKeys;
import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes pensionable base, employee/employer/voluntary pension, and NHF.
 */
public final class PensionNhfCalculator {

    private PensionNhfCalculator() {}

    public static BigDecimal resolveAnnualBasicSalary(PaymentInfo paymentInfo) {
        return paymentInfo.getPaymentSettings().stream()
                .filter(x -> x.getType() == PaymentTypeEnum.BASIC_SALARY_ANNUAL)
                .map(PaymentSettingsResponse::getValue)
                .findFirst()
                .orElse(paymentInfo.getBasicSalary() != null ? paymentInfo.getBasicSalary() : BigDecimal.ZERO);
    }

    /**
     * Pensionable base = annual basic + pensionable allowances (incl. housing/transport).
     */
    public static BigDecimal resolvePensionableBase(PaymentInfo paymentInfo, BigDecimal annualBasic) {
        return getAllowanceForEmployee(paymentInfo).stream()
                .filter(x -> x.isPensionable()
                        || x.getType() == PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING
                        || x.getType() == PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT)
                .map(PaymentSettingsResponse::getValue)
                .reduce(annualBasic, BigDecimal::add);
    }

    public static Set<PaymentSettingsResponse> getAllowanceForEmployee(PaymentInfo paymentInfo) {
        return paymentInfo.getPaymentSettings().stream()
                .filter(setting -> setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL)
                        || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_TRANSPORT)
                        || setting.getType().equals(PaymentTypeEnum.ALLOWANCE_ANNUAL_HOUSING)
                        || setting.getType().equals(PaymentTypeEnum.BASIC_SALARY_ANNUAL)
                        || setting.getType().equals(PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT))
                .collect(Collectors.toSet());
    }

    public static Result compute(PaymentInfo paymentInfo, EmployeeMetadata meta, PaymentFrequencyEnum salaryFrequency) {
        Map<String, BigDecimal> pension = new HashMap<>();
        Map<String, BigDecimal> nhf = new HashMap<>();

        boolean isPensioned = meta.isPensioned();
        boolean isIntern = meta.getEmployeeType() == EmployeeType.INTERN;
        BigDecimal annualBasic = resolveAnnualBasicSalary(paymentInfo);
        BigDecimal pensionableBase = isPensioned && !isIntern
                ? resolvePensionableBase(paymentInfo, annualBasic)
                : BigDecimal.ZERO;

        BigDecimal pensionPercent = PayrollSessionHolder.get().getComputationConstants().get("pensionFundPercent");
        BigDecimal employerPercent = PayrollSessionHolder.get().getComputationConstants()
                .get("employerPensionContributionPercent");
        BigDecimal nhfPercent = PayrollSessionHolder.get().getComputationConstants()
                .get("nationalHousingFundPercent");

        BigDecimal annualEmployeePension = isPensioned && !isIntern
                ? ComputationUtils.roundToTwoDecimalPlaces(pensionPercent.multiply(pensionableBase))
                : BigDecimal.ZERO;

        // Period amount for payroll deduction (same scale as other monthly items after proration)
        BigDecimal periodEmployeePension = ComputationUtils.prorate(
                annualEmployeePension, 0, salaryFrequency, paymentInfo.getStartDate());

        BigDecimal voluntary = meta.getVoluntaryPensionContribution() != null
                ? meta.getVoluntaryPensionContribution()
                : BigDecimal.ZERO;

        BigDecimal annualEmployerPension = isPensioned && !isIntern
                ? ComputationUtils.roundToTwoDecimalPlaces(employerPercent.multiply(pensionableBase))
                : BigDecimal.ZERO;
        BigDecimal periodEmployerPension = ComputationUtils.prorate(
                annualEmployerPension, 0, salaryFrequency, paymentInfo.getStartDate());

        pension.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, periodEmployeePension);
        pension.put(PayrollMapKeys.VOLUNTARY_PENSION, voluntary);
        pension.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, periodEmployerPension);
        pension.put(MapKeys.TOTAL_PENSION_FOR_EMPLOYEE,
                ComputationUtils.roundToTwoDecimalPlaces(
                        periodEmployeePension.add(voluntary).add(periodEmployerPension)));

        ComputationUtils.updateReportSummary(paymentInfo, PayrollSessionHolder.get(),
                MapKeys.TOTAL_EMPLOYER_PENSION_CONTRIBUTION, periodEmployerPension);

        boolean nhfSubscribed = meta.isNHFSubscribed();
        BigDecimal annualNhf = nhfSubscribed
                ? ComputationUtils.roundToTwoDecimalPlaces(nhfPercent.multiply(annualBasic))
                : BigDecimal.ZERO;
        BigDecimal periodNhf = ComputationUtils.prorate(annualNhf, 0, salaryFrequency, paymentInfo.getStartDate());
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, periodNhf);

        return new Result(pension, nhf, annualEmployeePension, annualNhf, annualBasic, pensionableBase);
    }

    public record Result(
            Map<String, BigDecimal> pension,
            Map<String, BigDecimal> nhf,
            BigDecimal annualEmployeePension,
            BigDecimal annualNhf,
            BigDecimal annualBasic,
            BigDecimal pensionableBase
    ) {}
}
