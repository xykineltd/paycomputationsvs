package com.xykine.computation.reconciliation.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykine.computation.reconciliation.mapping.*;
import com.xykine.computation.repo.PayrollReportDetailRepo;
import com.xykine.computation.service.AdminService;
import com.xykine.computation.service.ReportGeneratorService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollReconciliationUpdatedSystemTest {
    @Test
    void retainsAppliedFieldsAcrossUpdatesWithoutInflatingFailures() throws Exception {
        var mappings = mock(ReconciliationMappingService.class);
        var runs = mock(PayrollReconciliationTempRepository.class);
        var rows = mock(PayrollReconciliationTempRowRepository.class);
        var diffs = mock(PayrollReconciliationDiffRepository.class);
        var admin = mock(AdminService.class);
        var reports = mock(ReportGeneratorService.class);
        var service = new PayrollReconciliationRunService(mappings, mock(ReconciliationExcelParser.class),
                runs, rows, diffs, mock(PayrollReportDetailRepo.class), admin, reports, new ObjectMapper());
        var run = PayrollReconciliationTemp.builder().id("r").companyId("c").reportId("p")
                .status("INPUT_DONE").build();
        when(runs.findById("r")).thenReturn(Optional.of(run));
        var columns = List.of("Salary", "Allowance").stream().map(field -> ReconciliationColumnMapping.builder()
                .excelHeader(field).systemPath(field).stage("input").severity("hard").enabled(true)
                .valueType("number").build()).toList();
        when(mappings.getByCompanyId("c")).thenReturn(ReconciliationMapping.builder().columnMappings(columns).build());
        when(rows.findByReconciliationId("r")).thenReturn(List.of(PayrollReconciliationTempRow.builder()
                .matchKeyValue("E1").cells(Map.of("Salary", 100, "Allowance", 20)).build()));
        when(reports.loadPaymentInfoRowsForReport("c", "p", "token"))
                .thenReturn(List.of(Map.of("EMP ID", "E1", "Salary", 100, "Allowance", 0)));
        when(admin.applyExcelValues(any(), eq("token"))).thenReturn(ApplyExcelReconciliationResponse.builder()
                .cellsUpdated(1).employeesUpdated(1).fieldsApplied(List.of("Salary")).build());
        var request = UpdateSystemRequest.builder().employeeCode("E1").fields(List.of("Salary", "Allowance")).build();
        var result = service.updateSystem("r", request, "token");
        assertEquals(List.of("Salary"), run.getUpdatedSystemFields().get("E1"));
        assertEquals(1, result.getInput().getHardFailures());
        assertEquals(1, result.getInput().getTotalDiffRows());
        verify(diffs).saveAll(argThat(saved -> {
            var list = new ArrayList<PayrollReconciliationDiff>();
            saved.forEach(list::add);
            return list.size() == 2 && list.get(0).isUpdatedSystem()
                    && "MATCH".equals(list.get(0).getStatus()) && !list.get(1).isUpdatedSystem();
        }));
        when(admin.applyExcelValues(any(), eq("token"))).thenReturn(ApplyExcelReconciliationResponse.builder()
                .cellsUpdated(1).employeesUpdated(1).fieldsApplied(List.of("Allowance")).build());
        service.updateSystem("r", request, "token");
        assertEquals(List.of("Salary", "Allowance"), run.getUpdatedSystemFields().get("E1"));
        // A successful write may still mismatch until the report is recomputed.
        verify(diffs, times(1)).saveAll(argThat(saved -> {
            for (var diff : saved) {
                if ("Allowance".equals(diff.getField()) && diff.isUpdatedSystem()
                        && "MISMATCH".equals(diff.getStatus())) return true;
            }
            return false;
        }));
    }
}
