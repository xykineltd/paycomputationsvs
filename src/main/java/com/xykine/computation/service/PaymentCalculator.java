package com.xykine.computation.service;

import com.xykine.computation.model.PaymentInfo;


public interface PaymentCalculator {
    PaymentInfo computeGrossPay(PaymentInfo paymentInfo);
    PaymentInfo computeNonTaxableIncomeExempt(PaymentInfo paymentInfo);
    PaymentInfo computePayeeTax(PaymentInfo paymentInfo);
    PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo);
    PaymentInfo computeNetPay(PaymentInfo paymentInfo);
    PaymentInfo computeTotalNHF(PaymentInfo paymentInfo);
    PaymentInfo prorateEarnings(PaymentInfo paymentInfo);
}
