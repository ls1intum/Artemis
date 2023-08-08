
package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import org.apache.commons.lang3.NotImplementedException;

import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.dto.BonusExampleDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

public enum BonusStrategy implements IBonusStrategy {

    GRADES_DISCRETE {

        @Override
        public BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo,
                Double reachablePointsOfBonusTo, GradingScale sourceGradingScale, Double achievedPointsOfSource, Double reachablePointsOfSource, double weight) {
            throw new NotImplementedException("GRADES_DISCRETE bonus strategy not yet implemented");
        }
    },
    GRADES_CONTINUOUS {

        @Override
        public BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo,
                Double reachablePointsOfBonusTo, GradingScale sourceGradingScale, Double achievedPointsOfSource, Double reachablePointsOfSource, double weight) {
            GradeStep bonusGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * achievedPointsOfSource / reachablePointsOfSource, sourceGradingScale.getId());
            GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * achievedPointsOfBonusTo / reachablePointsOfBonusTo,
                    bonusToGradingScale.getId());
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

            return new BonusExampleDTO(achievedPointsOfBonusTo, achievedPointsOfSource, bonusToRawGradeStep.getGradeName(), bonusGrade, null, // Irrelevant for this bonus strategy.
                    Double.toString(finalGrade), exceedsMax);
        }
    },
    POINTS {

        @Override
        public BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo,
                Double reachablePointsOfBonusTo, GradingScale sourceGradingScale, Double achievedPointsOfSource, Double reachablePointsOfSource, double weight) {
            GradeStep bonusGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * achievedPointsOfSource / reachablePointsOfSource, sourceGradingScale.getId());

            Double bonusGrade = bonusGradeStep.getNumericValue();
            if (bonusGrade == null) {
                throw new BadRequestAlertException("Bonus source grade names must be numeric", "gradeStep", "invalidGradeName");
            }

            double finalPoints = roundScoreSpecifiedByCourseSettings(achievedPointsOfBonusTo + weight * bonusGrade, bonusToGradingScale.getCourseViaExamOrDirectly());

            boolean exceedsMax = doesBonusExceedMax(finalPoints, bonusToGradingScale.getMaxPoints(), weight);
            if (exceedsMax) {
                finalPoints = bonusToGradingScale.getMaxPoints();
            }
            GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * achievedPointsOfBonusTo / reachablePointsOfBonusTo,
                    bonusToGradingScale.getId());
            GradeStep finalGradeStep = gradingScaleRepository.matchPercentageToGradeStep(100. * finalPoints / reachablePointsOfBonusTo, bonusToGradingScale.getId());

            return new BonusExampleDTO(achievedPointsOfBonusTo, achievedPointsOfSource, bonusToRawGradeStep.getGradeName(), bonusGrade, finalPoints, finalGradeStep.getGradeName(),
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
