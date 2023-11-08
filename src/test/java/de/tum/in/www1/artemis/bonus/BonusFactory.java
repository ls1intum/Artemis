package de.tum.in.www1.artemis.bonus;

import de.tum.in.www1.artemis.domain.Bonus;
import de.tum.in.www1.artemis.domain.BonusStrategy;
import de.tum.in.www1.artemis.domain.GradingScale;

/**
 * Factory for creating Bonuses and related objects.
 */
public class BonusFactory {

    /**
     * Generates a Bonus with the given arguments.
     *
     * @param bonusStrategy         The bonus strategy of the bonus
     * @param weight                The weight of the bonus
     * @param sourceGradingScaleId  The id of the grading scale that is used as the source for the bonus
     * @param bonusToGradingScaleId The id of the grading scale that is used as the target for the bonus
     * @return The generated Bonus
     */
    public static Bonus generateBonus(BonusStrategy bonusStrategy, Double weight, long sourceGradingScaleId, long bonusToGradingScaleId) {
        Bonus bonus = new Bonus();
        bonus.setBonusStrategy(bonusStrategy);
        bonus.setWeight(weight);
        // New object is created to avoid circular dependency on json serialization.
        var sourceGradingScale = new GradingScale();
        sourceGradingScale.setId(sourceGradingScaleId);
        bonus.setSourceGradingScale(sourceGradingScale);

        // New object is created to avoid circular dependency on json serialization.
        var bonusToGradingScale = new GradingScale();
        bonusToGradingScale.setId(bonusToGradingScaleId);
        bonus.setBonusToGradingScale(bonusToGradingScale);

        return bonus;

    }
}
