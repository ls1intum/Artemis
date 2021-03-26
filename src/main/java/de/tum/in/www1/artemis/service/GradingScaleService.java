package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;

@Service
public class GradingScaleService {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(ExerciseService.class);

    private final GradingScaleRepository gradingScaleRepository;

    private final GradeStepRepository gradeStepRepository;

    private final GradeStepService gradeStepService;

    public GradingScaleService(GradingScaleRepository gradingScaleRepository, GradeStepRepository gradeStepRepository, GradeStepService gradeStepService) {
        this.gradeStepRepository = gradeStepRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.gradeStepService = gradeStepService;
    }

    public List<GradingScale> findAllGradingScales() {
        return gradingScaleRepository.findAll();
    }

    public GradingScale findGradingScaleById(Long id) {
        return gradingScaleRepository.findById(id).orElseThrow();
    }

    public GradingScale createGradingScale(GradingScale gradingScale) {
        return gradingScaleRepository.saveAndFlush(gradingScale);
    }

    public GradingScale saveGradingScale(GradingScale gradingScale) {
        Set<GradeStep> gradeSteps = gradingScale.getGradeSteps();

        for (GradeStep gradeStep : gradeSteps) {
            gradeStepRepository.saveAndFlush(gradeStep);
        }
        return gradingScaleRepository.saveAndFlush(gradingScale);
    }

    public void deleteGradingScaleById(Long id) {
        gradeStepRepository.deleteById(id);
    }

}
