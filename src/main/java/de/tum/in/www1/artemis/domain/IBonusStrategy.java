package de.tum.in.www1.artemis.domain;

import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.dto.BonusExampleDTO;

public interface IBonusStrategy {

    BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo,
            GradingScale sourceGradingScale, Double achievedPointsOfSource, double weight);
}
