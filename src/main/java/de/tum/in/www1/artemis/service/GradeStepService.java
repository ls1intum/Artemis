package de.tum.in.www1.artemis.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.repository.GradeStepRepository;

@Service
public class GradeStepService {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(ExerciseService.class);

    private final GradeStepRepository gradeStepRepository;

    public GradeStepService(GradeStepRepository gradeStepRepository) {
        this.gradeStepRepository = gradeStepRepository;
    }

    public List<GradeStep> findAllGradeSteps() {
        return gradeStepRepository.findAll();
    }

    public GradeStep findGradeStepById(Long id) {
        return gradeStepRepository.findById(id).orElseThrow();
    }

    public GradeStep saveGradeStep(GradeStep gradeStep) {
        return gradeStepRepository.saveAndFlush(gradeStep);
    }

    public void deleteGradeStepById(Long id) {
        gradeStepRepository.deleteById(id);
    }

}
