package de.tum.cit.aet.artemis.assessment.domain;

import de.tum.cit.aet.artemis.assessment.dto.BonusExampleDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;

public interface IBonusStrategy {

    BonusExampleDTO calculateBonusForStrategy(GradingScaleRepository gradingScaleRepository, GradingScale bonusToGradingScale, Double bonusToAchievedPoints,
            GradingScale sourceGradingScale, Double sourceAchievedPoints, Double sourceReachablePoints, double weight);
}
