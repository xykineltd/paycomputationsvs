package com.xykine.computation.controller;

import com.xykine.computation.entity.Stage;
import com.xykine.computation.request.WorkflowDTOs.CreateStageRequest;
import com.xykine.computation.request.WorkflowDTOs.UpdateStageRequest;
import com.xykine.computation.service.StageBulkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stages")
@RequiredArgsConstructor
public class StageBulkController {

    private final StageBulkService bulkService;

    // BULK CREATE: POST /api/stages/bulk
    @PostMapping("/bulk")
    public List<Stage> createStages(@Valid @RequestBody List<@Valid CreateStageRequest> reqs) {
        return bulkService.createStagesBulk(reqs);
    }

    // BULK UPDATE: PUT /api/stages/bulk
    @PutMapping("/bulk")
    public List<Stage> updateStages(@Valid @RequestBody List<@Valid UpdateStageRequest> reqs) {
        return bulkService.updateStagesBulk(reqs);
    }
}
