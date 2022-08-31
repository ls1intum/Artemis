
package de.tum.in.www1.artemis.domain;

import org.apache.commons.lang.NotImplementedException;

import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.dto.BonusExampleDTO;

public enum BonusStrategy implements IBonusStrategy {

    GRADES_DISCRETE {

        @Override
        public BonusExampleDTO calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo,
                GradingScale sourceGradingScale, Double achievedPointsOfSource, double weight) {
            throw new NotImplementedException("GRADES_DISCRETE bonus strategy not yet implemented");
        }
    },
    GRADES_CONTINUOUS {

        @Override
        public BonusExampleDTO calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo,
                GradingScale sourceGradingScale, Double achievedPointsOfSource, double weight) {
            GradeStep bonusGradeStep = gradingScaleRepository.matchPointsToGradeStep(achievedPointsOfSource, sourceGradingScale);
            GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPointsToGradeStep(achievedPointsOfBonusTo, bonusToGradingScale);
            GradeStep maxGradeStep = bonusToGradingScale.maxGrade();

            double bonusGrade = bonusGradeStep.getNumericValue();
            double finalGrade = bonusToRawGradeStep.getNumericValue() + weight * bonusGrade;

            double maxGrade = maxGradeStep.getNumericValue();
            boolean exceedsMax = false;
            if (doesBonusExceedMax(finalGrade, maxGrade, weight)) {
                exceedsMax = true;
                finalGrade = maxGrade;
            }

            return new BonusExampleDTO(achievedPointsOfBonusTo, achievedPointsOfSource, bonusToRawGradeStep.getGradeName(), bonusGrade, null, // Irrelevant for this bonus strategy.
                    Double.toString(finalGrade), exceedsMax);
        }
    },
    POINTS {

        @Override
        public BonusExampleDTO calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo,
                GradingScale sourceGradingScale, Double achievedPointsOfSource, double weight) {
            GradeStep bonusGradeStep = gradingScaleRepository.matchPointsToGradeStep(achievedPointsOfSource, sourceGradingScale);

            double bonusGrade = bonusGradeStep.getNumericValue();
            double finalPoints = achievedPointsOfBonusTo + weight * bonusGrade;

            boolean exceedsMax = false;
            if (doesBonusExceedMax(finalPoints, bonusToGradingScale.getMaxPoints(), weight)) {
                exceedsMax = true;
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
