package com.xykine.computation.service.calculator;

import com.xykine.computation.entity.EmployeeMetadata;
import com.xykine.computation.entity.EmployeeType;
import com.xykine.computation.entity.PaymentSettingMetaData;
import com.xykine.computation.entity.Tax;
import com.xykine.computation.exceptions.PayrollValidationException;
import com.xykine.computation.session.PayrollSessionHolder;
import com.xykine.computation.utils.ComputationUtils;
import com.xykine.computation.utils.PayrollMapKeys;
import org.xykine.payroll.model.MapKeys;
import org.xykine.payroll.model.PaymentFrequencyEnum;
import org.xykine.payroll.model.PaymentInfo;
import org.xykine.payroll.model.PaymentSettingsResponse;
import org.xykine.payroll.model.enums.PaymentTypeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xykine.computation.utils.ComputationUtils.prorate;
import static com.xykine.computation.utils.ComputationUtils.roundToTwoDecimalPlaces;

/**
 * Tax relief (regular MFB / off-cycle CRA) and PAYE computation.
 */
public final class TaxReliefAndPayeEngine {

    private TaxReliefAndPayeEngine() {}

    public static PaymentInfo applyRegularRelief(
            PaymentInfo paymentInfo,
            EmployeeMetadata meta,
            BigDecimal annualEmployeePension,
            BigDecimal annualNhf,
            BigDecimal monthlyTaxFreeAllowance) {

        Map<String, BigDecimal> taxRelief = new HashMap<>();

        BigDecimal grossPay = requireGross(paymentInfo);
        BigDecimal annualGrossSalary = grossPay.multiply(BigDecimal.valueOf(12L));

        BigDecimal voluntary = nz(meta.getVoluntaryPensionContribution());
        BigDecimal annualVoluntary = voluntary.multiply(BigDecimal.valueOf(12));
        BigDecimal customRelief = nz(meta.getCustomTaxReliefApplicable());
        BigDecimal rentAllowance = nz(meta.getRentAllowance());

        BigDecimal reliefPension = (meta.getEmployeeType() == EmployeeType.INTERN || !meta.isPensioned())
                ? BigDecimal.ZERO
                : annualEmployeePension;

        // All components annual
        BigDecimal reliefAllowance = annualNhf
                .add(reliefPension)
                .add(annualVoluntary)
                .add(rentAllowance)
                .add(customRelief);

        BigDecimal chargeableIncome = annualGrossSalary.subtract(reliefAllowance);
        BigDecimal callAllowance = paymentInfo.getGrossPay()
                .getOrDefault(PayrollMapKeys.CALL_DATA_ALLOWANCE, BigDecimal.ZERO);
        BigDecimal monthlyChargeable = chargeableIncome
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                .subtract(callAllowance)
                .subtract(monthlyTaxFreeAllowance);

        taxRelief.put(PayrollMapKeys.ANNUAL_EMPLOYEE_PENSION_8, reliefPension);
        taxRelief.put(PayrollMapKeys.RENT_RELIEF, rentAllowance);
        taxRelief.put(PayrollMapKeys.ANNUAL_NHF_ALLOWANCE, annualNhf);
        taxRelief.put(PayrollMapKeys.MONTHLY_CHARGEABLE_INCOME, monthlyChargeable);
        taxRelief.put(PayrollMapKeys.ANNUAL_VOLUNTARY_PENSION, annualVoluntary);
        taxRelief.put(PayrollMapKeys.MONTHLY_RELIEF,
                reliefAllowance.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));
        paymentInfo.setTaxRelief(taxRelief);
        return paymentInfo;
    }

    public static PaymentInfo applyOffCycleRelief(
            PaymentInfo paymentInfo,
            PaymentFrequencyEnum salaryFrequency,
            List<PaymentSettingMetaData> employeePaymentSettings) {

        Map<String, BigDecimal> taxRelief = new HashMap<>();
        Map<String, BigDecimal> nhf = new HashMap<>();
        nhf.put(MapKeys.NATIONAL_HOUSING_FUND, BigDecimal.ZERO);
        Map<String, BigDecimal> pension = new HashMap<>();
        pension.put(MapKeys.EMPLOYER_PENSION_CONTRIBUTION, BigDecimal.ZERO);
        pension.put(MapKeys.EMPLOYEE_PENSION_CONTRIBUTION, BigDecimal.ZERO);

        BigDecimal gross = requireGross(paymentInfo);
        BigDecimal craFraction = PayrollSessionHolder.get().getComputationConstants().get("craFraction");
        BigDecimal craCutOff = PayrollSessionHolder.get().getComputationConstants().get("craCutOff");
        BigDecimal variableCraFraction = PayrollSessionHolder.get().getComputationConstants()
                .get("variableCRAFraction");

        BigDecimal rawFXR = roundToTwoDecimalPlaces(craFraction.multiply(gross));
        if (rawFXR.compareTo(craCutOff) > 0) {
            taxRelief.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE,
                    prorate(rawFXR, 0, salaryFrequency, paymentInfo.getStartDate()));
        } else {
            taxRelief.put(MapKeys.FIXED_CONSOLIDATED_RELIEF_ALLOWANCE,
                    prorate(BigDecimal.valueOf(200000), 0, salaryFrequency, paymentInfo.getStartDate()));
        }

        BigDecimal variableCRA = roundToTwoDecimalPlaces(variableCraFraction.multiply(gross));
        taxRelief.put(MapKeys.VARIABLE_CONSOLIDATED_RELIEF_ALLOWANCE,
                roundToTwoDecimalPlaces(prorate(variableCRA, 0, salaryFrequency, paymentInfo.getStartDate())));

        BigDecimal totalRelief = taxRelief.values().stream()
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        taxRelief.put(MapKeys.TOTAL_TAX_RELIEF, roundToTwoDecimalPlaces(totalRelief));

        LocalDate start = LocalDate.parse(paymentInfo.getStartDate());
        List<String> nonTaxableNames = employeePaymentSettings.stream()
                .filter(x -> Boolean.FALSE.equals(x.getTaxable()))
                .filter(x -> !x.getStartDate().isAfter(start) && !x.getEndDate().isBefore(start))
                .map(PaymentSettingMetaData::getPaymentName)
                .toList();

        BigDecimal nonTaxableValue = paymentInfo.getPaymentSettings().stream()
                .filter(x -> nonTaxableNames.contains(x.getName()))
                .map(PaymentSettingsResponse::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalChargeable = gross.subtract(nonTaxableValue);
        taxRelief.put(PayrollMapKeys.MONTHLY_CHARGEABLE_INCOME,
                totalChargeable.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));

        paymentInfo.setNhf(nhf);
        paymentInfo.setPension(pension);
        paymentInfo.setTaxRelief(taxRelief);
        return paymentInfo;
    }

    public static PaymentInfo computePayeeTax(
            PaymentInfo paymentInfo,
            Tax taxInfo,
            PaymentFrequencyEnum salaryFrequency,
            boolean offCycleTaxable) {

        Map<String, BigDecimal> payeeTax = new HashMap<>();

        if (paymentInfo.isOffCycle()) {
            PaymentSettingsResponse offCyclePayment = paymentInfo.getPaymentSettings().stream()
                    .filter(p -> p.getType() == PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)
                    .findFirst()
                    .orElse(null);
            if (offCyclePayment == null) {
                return paymentInfo;
            }
            String key = PayrollMapKeys.offCyclePayeKey(offCyclePayment.getName());
            if (!offCycleTaxable) {
                payeeTax.put(key, BigDecimal.ZERO);
                paymentInfo.setPayeeTax(payeeTax);
                return paymentInfo;
            }
        }

        if (taxInfo == null) {
            throw new PayrollValidationException("Active tax configuration not found for NIGERIA");
        }
        if (paymentInfo.getTaxRelief() == null
                || paymentInfo.getTaxRelief().get(PayrollMapKeys.MONTHLY_CHARGEABLE_INCOME) == null) {
            throw new PayrollValidationException(
                    "MONTHLY CHARGEABLE INCOME missing for employeeId=" + paymentInfo.getEmployeeID());
        }

        BigDecimal chargeableAnnual = paymentInfo.getTaxRelief()
                .get(PayrollMapKeys.MONTHLY_CHARGEABLE_INCOME)
                .multiply(BigDecimal.valueOf(12L));
        payeeTax.put(MapKeys.TAXABLE_INCOME, chargeableAnnual);

        BigDecimal monthlyPayeeTax;
        String payeKey;
        if (!paymentInfo.isOffCycle()) {
            BigDecimal annualPaye = ComputationUtils.getAnnualTaxAmount(chargeableAnnual, taxInfo);
            payeeTax.put(PayrollMapKeys.ANNUAL_PAYE_TAX, annualPaye);
            monthlyPayeeTax = ComputationUtils.prorate(annualPaye, 0, salaryFrequency, paymentInfo.getStartDate());
            payeKey = PayrollMapKeys.PAYE;
        } else {
            monthlyPayeeTax = ComputationUtils.getTaxAmount(requireGross(paymentInfo), taxInfo);
            payeKey = PayrollMapKeys.offCyclePayeKey(
                    paymentInfo.getPaymentSettings().stream()
                            .filter(p -> p.getType() == PaymentTypeEnum.OFF_CYCLE_PAYMENT_AMOUNT)
                            .map(PaymentSettingsResponse::getName)
                            .findFirst()
                            .orElse("Off-Cycle"));
        }

        payeeTax.put(payeKey, monthlyPayeeTax);
        paymentInfo.setPayeeTax(payeeTax);
        ComputationUtils.updateReportSummary(
                paymentInfo, PayrollSessionHolder.get(), PayrollMapKeys.PAYE_DISPLAY, monthlyPayeeTax);
        return paymentInfo;
    }

    private static BigDecimal requireGross(PaymentInfo paymentInfo) {
        if (paymentInfo.getGrossPay() == null || paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY) == null) {
            throw new PayrollValidationException(
                    "Gross pay missing for employeeId=" + paymentInfo.getEmployeeID());
        }
        return paymentInfo.getGrossPay().get(MapKeys.GROSS_PAY);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
