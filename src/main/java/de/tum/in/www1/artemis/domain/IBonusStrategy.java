package de.tum.in.www1.artemis.domain;

import de.tum.in.www1.artemis.repository.GradingScaleRepository;

public interface IBonusStrategy {

    String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double basePoints, GradingScale sourceGradingScale,
            Double sourcePoints, double calculationSign);
}
