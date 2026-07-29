package com.xykine.computation.controller;

import com.xykine.computation.request.CreateLoanRequest;
import com.xykine.computation.service.LoanService;
import com.xykine.computation.utils.CompanyAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import com.xykine.computation.exceptions.CompanyAccessDeniedException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class LoanControllerAccessTest {

    @Mock
    private LoanService loanService;
    @Mock
    private CompanyAccessGuard companyAccessGuard;
    @InjectMocks
    private LoanController loanController;

    @Test
    void createLoanEnforcesCompanyAccess() {
        CreateLoanRequest req = new CreateLoanRequest();
        req.setCompanyId("company-1");
        req.setEmployeeId("emp-1");
        req.setPrincipalAmount(BigDecimal.TEN);
        req.setScheduledRepaymentAmount(BigDecimal.ONE);
        req.setDescription("test");

        doThrow(new CompanyAccessDeniedException("denied"))
                .when(companyAccessGuard).requireCompanyAccess("company-1");

        assertThatThrownBy(() -> loanController.createLoan(req))
                .isInstanceOf(CompanyAccessDeniedException.class);
        verify(loanService, never()).createLoan(any());
    }
}
