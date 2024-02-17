package com.xykine.computation.service;

import com.xykine.computation.model.PaymentInfo;


public interface PaymentCalculator {
    PaymentInfo computeTotalEarning(PaymentInfo paymentInfo);
    PaymentInfo computeTotalDeduction(PaymentInfo paymentInfo);
    PaymentInfo computeOthers(PaymentInfo paymentInfo);
}
