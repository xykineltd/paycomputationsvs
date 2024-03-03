package com.xykine.computation.service;

import com.xykine.computation.model.PaymentInfo;
import com.xykine.computation.response.PaymentResponse;


public interface PaymentCalculator {
    PaymentInfo computeTotalEarning(PaymentInfo paymentInfo);
    PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo);
    PaymentInfo computeOthers(PaymentInfo paymentInfo);
    PaymentInfo computeAmountDue(PaymentInfo paymentInfo);
}
