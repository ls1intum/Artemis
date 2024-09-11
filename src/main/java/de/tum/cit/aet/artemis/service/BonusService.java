package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Bonus;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.IBonusStrategy;
import de.tum.cit.aet.artemis.assessment.repository.BonusRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.web.rest.dto.BonusExampleDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@Service
public class BonusService {

    private final BonusRepository bonusRepository;

    private final GradingScaleRepository gradingScaleRepository;

    public BonusService(BonusRepository bonusRepository, GradingScaleRepository gradingScaleRepository) {
        this.bonusRepository = bonusRepository;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    /**
     * Saves a bonus source to the database if it is valid
     *
     * @param bonus                     the bonus source to be saved
     * @param isSourceGradeScaleUpdated used to skip source grading scale validation if it is not updated
     * @return the saved bonus source
     */
    public Bonus saveBonus(Bonus bonus, boolean isSourceGradeScaleUpdated) {
        if (isSourceGradeScaleUpdated && !bonus.getSourceGradingScale().getGradeType().equals(GradeType.BONUS)) {
            throw new BadRequestAlertException("Source grade scale should have bonus type.", "bonus", "invalidSourceGradingScale");
        }
        if (!bonus.getBonusToGradingScale().getGradeType().equals(GradeType.GRADE)) {
            throw new BadRequestAlertException("BonusTo grade scale should have grade type.", "bonus", "invalidBonusToGradingScale");
        }
        return bonusRepository.save(bonus);
    }

    /**
     * Applies bonus from sourceGradingScale to bonusToGradingScale so that the student's final grade in bonusToGradingScale is improved.
     *
     * @param bonusStrategy         bonus strategy (together with the weight) determines the formula for bonus calculation
     * @param bonusToGradingScale   the grading scale that the bonus will be applied to (e.g. a final exam)
     * @param bonusToAchievedPoints points received by the student from bonusTo exam before bonus calculations
     * @param sourceGradingScale    the grading scale that will determine how much bonus will be added (e.g. a course with exercises)
     * @param sourceAchievedPoints  points received by the student from source exam/course
     * @param sourceReachablePoints points that can be achieved by the student in the source exam/course
     * @param calculationSign       weight of the bonus, currently can be either -1.0 or 1.0
     * @return a record containing the final grade and points along
     */
    public BonusExampleDTO calculateGradeWithBonus(IBonusStrategy bonusStrategy, GradingScale bonusToGradingScale, Double bonusToAchievedPoints, GradingScale sourceGradingScale,
            Double sourceAchievedPoints, Double sourceReachablePoints, double calculationSign) {
        double bonusToReachablePoints = bonusToGradingScale.getMaxPoints();
        GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * bonusToAchievedPoints / bonusToReachablePoints, bonusToGradingScale.getId());

        if (!bonusToRawGradeStep.getIsPassingGrade() || sourceGradingScale == null) {
            return new BonusExampleDTO(bonusToAchievedPoints, sourceAchievedPoints, bonusToRawGradeStep.getGradeName(), 0.0, bonusToAchievedPoints,
                    bonusToRawGradeStep.getGradeName(), false);
        }
        return bonusStrategy.calculateBonusForStrategy(gradingScaleRepository, bonusToGradingScale, bonusToAchievedPoints, sourceGradingScale, sourceAchievedPoints,
                sourceReachablePoints, calculationSign);
    }

    /**
     * Improves the points and/or grade of the bonusTo exam by applying bonus according to the parameters defined in {@code bonus}.
     * This method is a wrapper for {@link #calculateGradeWithBonus(IBonusStrategy, GradingScale, Double, GradingScale, Double, Double, double)}.
     *
     * @param bonus                 the bonus instance determining the bonus calculation strategy and weight
     * @param bonusToAchievedPoints points received by the student from bonusTo exam before bonus calculations
     * @param sourceAchievedPoints  points received by the student from source exam/course
     * @param sourceReachablePoints points that can be achieved by the student in the source exam/course
     * @return bonus strategy, weight, points and grades achieved from the bonusTo exam, source course/exam and the final points and grade
     */
    public BonusExampleDTO calculateGradeWithBonus(Bonus bonus, Double bonusToAchievedPoints, Double sourceAchievedPoints, Double sourceReachablePoints) {
        return calculateGradeWithBonus(bonus.getBonusToGradingScale().getBonusStrategy(), bonus.getBonusToGradingScale(), bonusToAchievedPoints, bonus.getSourceGradingScale(),
                sourceAchievedPoints, sourceReachablePoints, bonus.getWeight());
    }
}
