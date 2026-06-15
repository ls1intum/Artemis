package de.tum.cit.aet.artemis.assessment.dto;

import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;

/**
 * DTO accepted by the create and update bonus endpoints.
 * <p>
 * The client sends the source grading scale as a bare {@code {id}} reference; the controller loads the managed grading
 * scale by that id. The bonus strategy is transient on the entity and is applied to the owning grading scale by the
 * controller. No {@code @JsonInclude} is set on request DTOs so the client contract stays explicit.
 */
public record BonusRequestDTO(Long id, double weight, BonusStrategy bonusStrategy, GradingScaleIdDTO sourceGradingScale) {

    /**
     * Bare grading-scale reference carrying only the id the controller uses to load the managed entity.
     */
    public record GradingScaleIdDTO(Long id) {
    }

    /**
     * @return the id of the referenced source grading scale, or {@code null} if none was provided
     */
    public Long sourceGradingScaleId() {
        return sourceGradingScale == null ? null : sourceGradingScale.id();
    }
}
