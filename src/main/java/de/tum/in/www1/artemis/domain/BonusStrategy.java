
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
                GradingScale sourceGradingScale, Double achievedPointsOfSource, double weight) {
            throw new NotImplementedException("GRADES_DISCRETE bonus strategy not yet implemented");
        }
    },
    GRADES_CONTINUOUS {

        @Override
        public BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo,
                GradingScale sourceGradingScale, Double achievedPointsOfSource, double weight) {
            GradeStep bonusGradeStep = gradingScaleRepository.matchPointsToGradeStep(achievedPointsOfSource, sourceGradingScale);
            GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPointsToGradeStep(achievedPointsOfBonusTo, bonusToGradingScale);
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
                GradingScale sourceGradingScale, Double achievedPointsOfSource, double weight) {
            GradeStep bonusGradeStep = gradingScaleRepository.matchPointsToGradeStep(achievedPointsOfSource, sourceGradingScale);

            Double bonusGrade = bonusGradeStep.getNumericValue();
            if (bonusGrade == null) {
                throw new BadRequestAlertException("Bonus source grade names must be numeric", "gradeStep", "invalidGradeName");
            }

            double finalPoints = roundScoreSpecifiedByCourseSettings(achievedPointsOfBonusTo + weight * bonusGrade, bonusToGradingScale.getCourseViaExamOrDirectly());

            boolean exceedsMax = doesBonusExceedMax(finalPoints, bonusToGradingScale.getMaxPoints(), weight);
            if (exceedsMax) {
                finalPoints = bonusToGradingScale.getMaxPoints();
            }
            GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPointsToGradeStep(achievedPointsOfBonusTo, bonusToGradingScale);
            GradeStep finalGradeStep = gradingScaleRepository.matchPointsToGradeStep(finalPoints, bonusToGradingScale);

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
