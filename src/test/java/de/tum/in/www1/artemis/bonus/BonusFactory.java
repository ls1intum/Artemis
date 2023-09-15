package de.tum.in.www1.artemis.bonus;

import de.tum.in.www1.artemis.domain.Bonus;
import de.tum.in.www1.artemis.domain.BonusStrategy;
import de.tum.in.www1.artemis.domain.GradingScale;

/**
 * Factory for creating Bonuses and related objects.
 */
public class BonusFactory {

    /**
     * Generates a Bonus instance with given arguments.
     *
     * @param bonusStrategy         of bonus
     * @param weight                of bonus
     * @param sourceGradingScaleId  of sourceGradingScale of bonus
     * @param bonusToGradingScaleId of bonusToGradingScale bonus
     * @return a new Bonus instance associated with the grading scales corresponding to ids bonusToGradingScaleId and bonusToGradingScaleId.
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
