
package de.tum.in.www1.artemis.domain;

import org.apache.commons.lang.NotImplementedException;

import de.tum.in.www1.artemis.repository.GradingScaleRepository;

public enum BonusStrategy implements IBonusStrategy {

    GRADES_DISCRETE {

        @Override
        public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double basePoints, GradingScale sourceGradingScale,
                Double sourcePoints, double calculationSign) {
            throw new NotImplementedException("GRADES_DISCRETE bonus strategy not yet implemented");
        }
    },
    GRADES_CONTINUOUS {

        @Override
        public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double basePoints, GradingScale sourceGradingScale,
                Double sourcePoints, double calculationSign) {
            int sourceMaxPoints = getSourceGradingScaleMaxPoints(sourceGradingScale);
            double sourcePercentage = sourcePoints / sourceMaxPoints * 100.0;

            GradeStep bonusGradeStep = gradingScaleRepository.matchPercentageToGradeStep(sourcePercentage, sourceGradingScale.getId());

            int bonusToMaxPoints = bonusToGradingScale.getExam().getMaxPoints();
            double bonusToPercentage = basePoints / bonusToMaxPoints * 100.0;
            GradeStep bonusToRawGradeStep = gradingScaleRepository.matchPercentageToGradeStep(bonusToPercentage, bonusToGradingScale.getId());

            return Double.toString(bonusToRawGradeStep.getNumericValue() + calculationSign * bonusGradeStep.getNumericValue());
        }
    },
    POINTS {

        @Override
        public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double basePoints, GradingScale sourceGradingScale,
                Double sourcePoints, double calculationSign) {
            int sourceMaxPoints = getSourceGradingScaleMaxPoints(sourceGradingScale);
            double sourcePercentage = sourcePoints / sourceMaxPoints * 100.0;

            GradeStep bonusGradeStep = gradingScaleRepository.matchPercentageToGradeStep(sourcePercentage, sourceGradingScale.getId());

            double bonusToPointsWithBonus = basePoints + calculationSign * bonusGradeStep.getNumericValue();
            int bonusToMaxPoints = bonusToGradingScale.getExam().getMaxPoints();

            double bonusToPercentage = bonusToPointsWithBonus / bonusToMaxPoints * 100.0;
            return gradingScaleRepository.matchPercentageToGradeStep(bonusToPercentage, bonusToGradingScale.getId()).getGradeName();
        }
    };

    private static int getSourceGradingScaleMaxPoints(GradingScale sourceGradingScale) {
        if (sourceGradingScale.getCourse() != null) {
            return sourceGradingScale.getCourse().getMaxPoints();
        }
        else {
            return sourceGradingScale.getExam().getMaxPoints();
        }
    }

}
