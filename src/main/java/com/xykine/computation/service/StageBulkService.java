package com.xykine.computation.service;

import com.xykine.computation.entity.Stage;
import com.xykine.computation.entity.StageEntity;
import com.xykine.computation.repo.StageRepository;
import com.xykine.computation.request.WorkflowDTOs.CreateStageRequest;
import com.xykine.computation.request.WorkflowDTOs.UpdateStageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StageBulkService {

    private final StageRepository stageRepository;

    @Transactional
    public List<Stage> createStagesBulk(List<CreateStageRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) return List.of();

        // In-request duplicate check on (entity, companyId, stepNumber)
        Set<String> seen = new HashSet<>();
        for (CreateStageRequest r : reqs) {
            String key = key(r.getEntity(), r.getCompanyId(), r.getStepNumber());
            if (!seen.add(key)) {
                throw new IllegalArgumentException("Duplicate stage in request for " + key);
            }
        }

        // DB conflict check
        List<String> conflicts = reqs.stream()
                .filter(r -> stageRepository.existsByEntityAndCompanyIdAndStepNumber(
                        StageEntity.valueOf(r.getEntity()), r.getCompanyId(), r.getStepNumber()))
                .map(r -> key(r.getEntity(), r.getCompanyId(), r.getStepNumber()))
                .toList();

        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Stage(s) already exist for: " + conflicts);
        }

        Instant now = Instant.now();
        List<Stage> toSave = reqs.stream().map(r ->
                Stage.builder()
                        .entity(StageEntity.valueOf(r.getEntity()))
                        .companyId(r.getCompanyId())
                        .stepNumber(r.getStepNumber())
                        .name(r.getName())
                        .description(r.getDescription())
                        .approverId(r.getApproverId())
                        .createdAt(now)
                        .build()
        ).toList();

        return stageRepository.saveAll(toSave);
    }

    @Transactional
    public List<Stage> updateStagesBulk(List<UpdateStageRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) return List.of();

        // Load all referenced stages (by id or composite key)
        Map<String, Stage> toUpdate = new LinkedHashMap<>();
        for (UpdateStageRequest r : reqs) {
            Stage stage;
            if (r.getId() != null && !r.getId().isBlank()) {
                stage = stageRepository.findById(r.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Stage not found by id: " + r.getId()));
            } else {
                if (r.getEntity() == null || r.getCompanyId() == null || r.getStepNumber() == null) {
                    throw new IllegalArgumentException("For update without id, entity/companyId/stepNumber are required.");
                }
                stage = stageRepository.findByEntityAndCompanyIdAndStepNumber(
                                StageEntity.valueOf(r.getEntity()), r.getCompanyId(), r.getStepNumber())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Stage not found for " + key(r.getEntity(), r.getCompanyId(), r.getStepNumber())));
            }

            // Apply changes if provided
            if (r.getName() != null) stage.setName(r.getName());
            if (r.getDescription() != null) stage.setDescription(r.getDescription());
            if (r.getApproverId() != null) stage.setApproverId(r.getApproverId());

            // Handle step move with conflict detection
            if (r.getNewStepNumber() != null && !Objects.equals(r.getNewStepNumber(), stage.getStepNumber())) {
                StageEntity entity = stage.getEntity();
                String companyId = stage.getCompanyId();
                Integer newStep = r.getNewStepNumber();

                if (stageRepository.existsByEntityAndCompanyIdAndStepNumber(entity, companyId, newStep)) {
                    throw new IllegalStateException("Cannot move stage; target step already exists for "
                            + key(entity.name(), companyId, newStep));
                }
                stage.setStepNumber(newStep);
            }

            toUpdate.put(stage.getId(), stage);
        }

        return stageRepository.saveAll(toUpdate.values());
    }

    private String key(String entity, String companyId, Integer step) {
        return "(entity=" + entity + ", companyId=" + companyId + ", step=" + step + ")";
    }
}
