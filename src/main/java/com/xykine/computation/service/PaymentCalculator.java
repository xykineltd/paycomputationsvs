package com.xykine.computation.service;

import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.response.PaymentResponse;


public interface PaymentCalculator {
    PaymentInfo computeGrossPay(PaymentInfo paymentInfo);
    PaymentInfo computeNonTaxableIncomeExempt(PaymentInfo paymentInfo);
    PaymentInfo computePayeeTax(PaymentInfo paymentInfo);
    PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo);
    PaymentInfo computeNetPay(PaymentInfo paymentInfo);
    PaymentInfo prorateEarnings(PaymentInfo paymentInfo);
}
