package com.xykine.computation.service;

import com.xykine.computation.entity.Stage;
import com.xykine.computation.entity.StageEntity;
import com.xykine.computation.repo.StageRepository;
import com.xykine.computation.request.WorkflowDTOs.PAYROLL_ACTIONS;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service @RequiredArgsConstructor
public class StageService {
    private final StageRepository stageRepository;

    public Stage createStage(Stage stage) {
        return stageRepository.save(stage);
    }

    public Stage updateStage(String id, String name, String description, String approverId, List<PAYROLL_ACTIONS> actions) {
        Stage s = stageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + id));
        s.setName(name);
        s.setDescription(description);
        s.setApproverId(approverId);
        s.setActions(actions);
        return stageRepository.save(s);
    }

    public boolean deleteStage(String id) {
        stageRepository.deleteById(id);
        return true;
    }

    public List<Stage> getOrderedStages(StageEntity entity, String companyId) {
        return stageRepository.findByEntityAndCompanyIdOrderByStepNumberAsc(entity, companyId);
    }

    public List<Stage> getStages(StageEntity entity) {
        return stageRepository.findByEntityOrderByStepNumberAsc(entity);
    }

    public Stage getNextStage(StageEntity entity, int afterStep) {
        return stageRepository.findByEntityOrderByStepNumberAsc(entity)
                .stream()
                .filter(st -> st.getStepNumber() > afterStep)
                .findFirst()
                .orElse(null);
    }

    public Stage getById(String stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + stageId));
    }
}
