package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Bonus;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;

/**
 * DTO returned by the bonus endpoints.
 * <p>
 * Mirrors the previous {@link Bonus} entity wire shape: the weight, the (transient) bonus strategy, and the two grading
 * scales. The {@code bonusToGradingScale} never carries its grade steps (matching the previous response filtering),
 * while the {@code sourceGradingScale} carries them only when requested via {@code includeSourceGradeSteps}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BonusResponseDTO(Long id, double weight, BonusStrategy bonusStrategy, GradingScaleForBonusDTO sourceGradingScale, GradingScaleForBonusDTO bonusToGradingScale) {

    /**
     * Creates a {@link BonusResponseDTO} from a {@link Bonus} entity.
     *
     * @param bonus                   the bonus entity to convert
     * @param includeSourceGradeSteps whether the grade steps of the source grading scale should be serialized
     * @return a DTO representation of the bonus
     */
    public static BonusResponseDTO of(Bonus bonus, boolean includeSourceGradeSteps) {
        Objects.requireNonNull(bonus, "bonus must exist");

        GradingScaleForBonusDTO source = bonus.getSourceGradingScale() == null ? null : GradingScaleForBonusDTO.of(bonus.getSourceGradingScale(), includeSourceGradeSteps);
        GradingScaleForBonusDTO bonusTo = bonus.getBonusToGradingScale() == null ? null : GradingScaleForBonusDTO.of(bonus.getBonusToGradingScale(), false);

        return new BonusResponseDTO(bonus.getId(), bonus.getWeight(), bonus.getBonusStrategy(), source, bonusTo);
    }
}
