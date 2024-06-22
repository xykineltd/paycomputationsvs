package com.xykine.computation.service;

import org.xykine.payroll.model.PaymentInfo;

public interface PaymentCalculator {
    PaymentInfo applyExchange(PaymentInfo paymentInfo);
    PaymentInfo harmoniseToAnnual(PaymentInfo paymentInfo);
    PaymentInfo computeGrossPay(PaymentInfo paymentInfo);
    PaymentInfo computeNonTaxableIncomeExempt(PaymentInfo paymentInfo);
    PaymentInfo computePayeeTax(PaymentInfo paymentInfo);
    PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo);
    PaymentInfo computeNetPay(PaymentInfo paymentInfo);
    PaymentInfo computeTotalNHF(PaymentInfo paymentInfo);
    PaymentInfo prorateEarnings(PaymentInfo paymentInfo);
}
