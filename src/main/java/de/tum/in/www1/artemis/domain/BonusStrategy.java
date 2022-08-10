
package de.tum.in.www1.artemis.domain;

import de.tum.in.www1.artemis.repository.GradingScaleRepository;

public enum BonusStrategy implements IBonusStrategy {

    GRADES_DISCRETE {

        @Override
        public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale targetGradingScale, Double targetPoints, GradingScale sourceGradingScale,
                Double sourcePoints, double calculationSign) {
            // TODO: Ata Implement.

            return null;
        }
    },
    GRADES_CONTINUOUS {

        @Override
        public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale targetGradingScale, Double targetPoints, GradingScale sourceGradingScale,
                Double sourcePoints, double calculationSign) {
            int sourceMaxPoints = getSourceGradingScaleMaxPoints(sourceGradingScale);
            double sourcePercentage = sourcePoints / sourceMaxPoints * 100.0;

            GradeStep bonusGradeStep = gradingScaleRepository.matchPercentageToGradeStep(sourcePercentage, sourceGradingScale.getId());

            int targetMaxPoints = targetGradingScale.getExam().getMaxPoints();
            double targetPercentage = targetPoints / targetMaxPoints * 100.0;
            GradeStep targetRawGradeStep = gradingScaleRepository.matchPercentageToGradeStep(targetPercentage, targetGradingScale.getId());

            return Double.toString(targetRawGradeStep.getNumericValue() + calculationSign * bonusGradeStep.getNumericValue());
        }
    },
    POINTS {

        @Override
        public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale targetGradingScale, Double targetPoints, GradingScale sourceGradingScale,
                Double sourcePoints, double calculationSign) {
            int sourceMaxPoints = getSourceGradingScaleMaxPoints(sourceGradingScale);
            double sourcePercentage = sourcePoints / sourceMaxPoints * 100.0;

            GradeStep bonusGradeStep = gradingScaleRepository.matchPercentageToGradeStep(sourcePercentage, sourceGradingScale.getId());

            double targetPointsWithBonus = targetPoints + calculationSign * bonusGradeStep.getNumericValue();
            int targetMaxPoints = targetGradingScale.getExam().getMaxPoints();

            double targetPercentage = targetPointsWithBonus / targetMaxPoints * 100.0;
            return gradingScaleRepository.matchPercentageToGradeStep(targetPercentage, targetGradingScale.getId()).getGradeName();
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
