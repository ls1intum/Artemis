package de.tum.in.www1.artemis.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Maps a grade percentage to a valid grade step within the grading scale or throws an exception if no match was found
     *
     * @param percentage the grade percentage to be mapped
     * @param gradingScaleId the identifier for the grading scale
     * @return grade step corresponding to the given percentage
     */
    public GradeStep matchPercentageToGradeStep(double percentage, Long gradingScaleId) {
        if (percentage < 0 || percentage > 100) {
            throw new BadRequestAlertException("Grade percentages must be between 0 and 100", "gradeStep", "invalidGradePercentage");
        }
        List<GradeStep> gradeSteps = gradeStepRepository.findByGradingScale_Id(gradingScaleId);
        Optional<GradeStep> matchingGradeStep = gradeSteps.stream().filter(gradeStep -> gradeStep.matchingGradePercentage(percentage)).findFirst();
        if (matchingGradeStep.isPresent()) {
            return matchingGradeStep.get();
        }
        else {
            throw new EntityNotFoundException("No grade step in selected grading scale matches given percentage");
        }
    }

    /**
     * Saves a grading scale to the database if it is valid
     *
     * @param gradingScale the grading scale to be saved
     * @return the saved grading scale
     */
    @Transactional
    public GradingScale saveGradingScale(GradingScale gradingScale) {
        Set<GradeStep> gradeSteps = gradingScale.getGradeSteps();
        checkGradeStepValidity(gradeSteps);
        for (GradeStep gradeStep : gradeSteps) {
            gradeStep.setGradingScale(gradingScale);
        }
        gradingScale.setGradeSteps(gradeSteps);
        return gradingScaleRepository.save(gradingScale);
    }

    /**
     * Checks the validity of a grade step set and throws an exception if one of the following conditions is not fulfilled
     * - all individuals grade steps should be in a valid format
     * - the grade steps set should form a valid and congruent grading scale
     *
     * @param gradeSteps the grade steps to be checked
     */
    private void checkGradeStepValidity(Set<GradeStep> gradeSteps) {
        if (gradeSteps != null) {
            if (!gradeSteps.stream().allMatch(GradeStep::isValid)) {
                throw new BadRequestAlertException("Not all grade steps are following the correct format.", "gradeStep", "invalidFormat");
            }
            else if (!gradeStepSetMapsToValidGradingScale(gradeSteps)) {
                throw new BadRequestAlertException("Grade step set can't match to a valid grading scale.", "gradeStep", "invalidFormat");
            }
        }
    }

    /**
     * Checks if the grade steps map to a valid grading scale
     * - the grade names should all be unique for the grading scale
     * - when ordered, all steps should fulfill valid adjacency
     * - the first and the last element should fulfill the boundary conditions (start with 0% and end with 100%)
     *
     * @param gradeSteps the grade steps to be checked
     * @return true if the grade steps map to a valid grading scale and false otherwise
     */
    private boolean gradeStepSetMapsToValidGradingScale(Set<GradeStep> gradeSteps) {
        if (gradeSteps.stream().map(GradeStep::getGradeName).distinct().count() != gradeSteps.size()) {
            return false;
        }
        List<GradeStep> sortedGradeSteps = gradeSteps.stream().sorted(Comparator.comparingDouble(GradeStep::getLowerBoundPercentage)).collect(Collectors.toList());
        boolean validAdjacency = IntStream.range(0, sortedGradeSteps.size() - 2).allMatch(i -> GradeStep.checkValidAdjacency(sortedGradeSteps.get(i), sortedGradeSteps.get(i + 1)));
        boolean validFirstElement = sortedGradeSteps.get(0).isLowerBoundInclusive() && sortedGradeSteps.get(0).getLowerBoundPercentage() == 0;
        boolean validLastElement = sortedGradeSteps.get(sortedGradeSteps.size() - 1).isUpperBoundInclusive()
                && sortedGradeSteps.get(sortedGradeSteps.size() - 1).getUpperBoundPercentage() == 100;
        return validAdjacency && validFirstElement && validLastElement;
    }

}
