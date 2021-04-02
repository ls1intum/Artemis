package de.tum.in.www1.artemis.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class GradingScaleService {

    private final GradingScaleRepository gradingScaleRepository;

    private final GradeStepRepository gradeStepRepository;

    public GradingScaleService(GradingScaleRepository gradingScaleRepository, GradeStepRepository gradeStepRepository) {
        this.gradeStepRepository = gradeStepRepository;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    public GradeStep matchPercentageToGradeStep(int percentage, Long gradingScaleId) {
        List<GradeStep> gradeSteps = gradeStepRepository.findByGradingScale_Id(gradingScaleId);
        Optional<GradeStep> matchingGradeStep = gradeSteps.stream().filter(gradeStep -> gradeStep.matchingGradePercentage(percentage)).findFirst();
        if (matchingGradeStep.isPresent()) {
            return matchingGradeStep.get();
        }
        else {
            throw new EntityNotFoundException("No grade step in selected grading scale matches given percentage");
        }
    }

    public GradingScale saveGradingScale(GradingScale gradingScale, boolean update) {
        Set<GradeStep> gradeSteps = gradingScale.getGradeSteps();
        gradingScale.setGradeSteps(null);
        gradingScaleRepository.saveAndFlush(gradingScale);
        return saveGradeStepsForGradingScale(gradingScale, gradeSteps, update);
    }

    private GradingScale saveGradeStepsForGradingScale(GradingScale gradingScale, Set<GradeStep> gradeSteps, boolean update) {
        if (gradeSteps != null) {
            if (gradeSteps.stream().allMatch(GradeStep::isValid)) {
                throw new BadRequestAlertException("Not all grade steps are follow the correct format.", "gradeStep", "invalidFormat");
            }
            if (!gradeStepSetMapsToValidGradingScale(gradeSteps)) {
                throw new BadRequestAlertException("Grade step set can't match to a valid grading scale.", "gradeStep", "invalidFormat");
            }

            if (update) {
                gradeStepRepository.deleteAllGradeStepsForGradingScaleById(gradingScale.getId());
            }

            for (GradeStep gradeStep : gradeSteps) {
                gradeStep.setGradingScale(gradingScale);
                gradeStepRepository.save(gradeStep);
            }
            gradeStepRepository.flush();
        }
        return gradingScaleRepository.findById(gradingScale.getId()).orElseThrow();
    }

    private boolean gradeStepSetMapsToValidGradingScale(Set<GradeStep> gradeSteps) {
        if (gradeSteps.stream().map(GradeStep::getGradeName).distinct().count() != gradeSteps.size()) {
            return false;
        }
        List<GradeStep> sortedGradeSteps = gradeSteps.stream().sorted(Comparator.comparingDouble(GradeStep::getLowerBoundPercentage)).collect(Collectors.toList());
        return IntStream.range(0, sortedGradeSteps.size() - 2).allMatch(i -> GradeStep.checkValidAdjacency(sortedGradeSteps.get(i), sortedGradeSteps.get(i + 1)));
    }

}
