
package de.tum.cit.aet.artemis.assessment.domain;

import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import org.apache.commons.lang3.NotImplementedException;

import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.dto.BonusExampleDTO;

public enum BonusStrategy implements IBonusStrategy {

    GRADES_DISCRETE {

        @Override
        public BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double bonusToAchievedPoints,
                GradingScale sourceGradingScale, Double sourceAchievedPoints, Double sourceReachablePoints, double weight) {
            throw new NotImplementedException("GRADES_DISCRETE bonus strategy not yet implemented");
        }
    },
    GRADES_CONTINUOUS {

        @Override
        public BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double bonusToAchievedPoints,
                GradingScale sourceGradingScale, Double sourceAchievedPoints, Double sourceReachablePoints, double weight) {
            final double bonusToReachablePoints = bonusToGradingScale.getMaxPoints();
            GradeStep bonusGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * sourceAchievedPoints / sourceReachablePoints, sourceGradingScale.getId());
            GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * bonusToAchievedPoints / bonusToReachablePoints, bonusToGradingScale.getId());
            GradeStep maxGradeStep = bonusToGradingScale.maxGrade();

            Double bonusGrade = bonusGradeStep.getNumericValue();
            if (bonusGrade == null) {
                throw new BadRequestAlertException("Bonus source grade names must be numeric", "gradeStep", "invalidGradeName");
            }
            Double bonusToGrade = bonusToRawGradeStep.getNumericValue();
            if (bonusToGrade == null) {
                throw new BadRequestAlertException("Final exam grade names must be numeric for this bonus strategy", "gradeStep", "invalidGradeName");
            }

            double finalGrade = roundScoreSpecifiedByCourseSettings(bonusToGrade + weight * bonusGrade, bonusToGradingScale.getCourseViaExamOrDirectly());

            double maxGrade = maxGradeStep.getNumericValue();
            boolean exceedsMax = doesBonusExceedMax(finalGrade, maxGrade, weight);
            if (exceedsMax) {
                finalGrade = maxGrade;
            }

            return new BonusExampleDTO(bonusToAchievedPoints, sourceAchievedPoints, bonusToRawGradeStep.getGradeName(), bonusGrade, null, // Irrelevant for this bonus strategy.
                    Double.toString(finalGrade), exceedsMax);
        }
    },
    POINTS {

        @Override
        public BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double bonusToAchievedPoints,
                GradingScale sourceGradingScale, Double sourceAchievedPoints, Double sourceReachablePoints, double weight) {
            final double bonusToReachablePoints = bonusToGradingScale.getMaxPoints();
            GradeStep bonusGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * sourceAchievedPoints / sourceReachablePoints, sourceGradingScale.getId());

            Double bonusGrade = bonusGradeStep.getNumericValue();
            if (bonusGrade == null) {
                throw new BadRequestAlertException("Bonus source grade names must be numeric", "gradeStep", "invalidGradeName");
            }

            double finalPoints = roundScoreSpecifiedByCourseSettings(bonusToAchievedPoints + weight * bonusGrade, bonusToGradingScale.getCourseViaExamOrDirectly());

            boolean exceedsMax = doesBonusExceedMax(finalPoints, bonusToGradingScale.getMaxPoints(), weight);
            if (exceedsMax) {
                finalPoints = bonusToGradingScale.getMaxPoints();
            }
            GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * bonusToAchievedPoints / bonusToReachablePoints, bonusToGradingScale.getId());
            GradeStep finalGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * finalPoints / bonusToReachablePoints, bonusToGradingScale.getId());

            return new BonusExampleDTO(bonusToAchievedPoints, sourceAchievedPoints, bonusToRawGradeStep.getGradeName(), bonusGrade, finalPoints, finalGradeStep.getGradeName(),
                    exceedsMax);
        }
    };

    /**
     * Returns true if valueWithBonus exceeds the maxValue in the direction given by weight.
     *
     * @param valueWithBonus achieved points or numeric grade with bonus applied
     * @param maxValue       max points or max grade (numeric)
     * @param weight         a negative or positive number to indicate decreasing or increasing direction, respectively
     */
    private static boolean doesBonusExceedMax(double valueWithBonus, double maxValue, double weight) {
        return (valueWithBonus - maxValue) * weight > 0;
    }

}
