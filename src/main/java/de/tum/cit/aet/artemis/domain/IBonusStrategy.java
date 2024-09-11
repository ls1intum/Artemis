package de.tum.cit.aet.artemis.domain;

import de.tum.cit.aet.artemis.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.web.rest.dto.BonusExampleDTO;

public interface IBonusStrategy {

    BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double bonusToAchievedPoints,
            GradingScale sourceGradingScale, Double sourceAchievedPoints, Double sourceReachablePoints, double weight);
}
