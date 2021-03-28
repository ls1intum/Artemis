package de.tum.in.www1.artemis.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;

@Service
public class GradeStepService {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(ExerciseService.class);

    private final GradeStepRepository gradeStepRepository;

    private final GradingScaleRepository gradingScaleRepository;

    public GradeStepService(GradeStepRepository gradeStepRepository, GradingScaleRepository gradingScaleRepository) {
        this.gradeStepRepository = gradeStepRepository;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    public List<GradeStep> findAllGradeStepsForGradingScaleById(Long gradingScaleId) {
        return gradeStepRepository.findGradeStepByGradingScale_Id(gradingScaleId);
    }

    public GradeStep findGradeStepByIdForGradingScaleByGradingScaleId(Long gradeStepId, Long gradingScaleId) {
        return gradeStepRepository.findByIdAndGradingScale_Id(gradeStepId, gradingScaleId).orElseThrow();
    }

    public GradeStep saveGradeStepForGradingScaleById(GradeStep gradeStep, Long gradingScaleId) {
        GradingScale gradingScale = gradingScaleRepository.findById(gradingScaleId).orElseThrow();
        gradeStep.setGradingScale(gradingScale);
        return gradeStepRepository.saveAndFlush(gradeStep);
    }

    public void deleteGradeStepById(Long id) {
        gradeStepRepository.deleteById(id);
    }

}
