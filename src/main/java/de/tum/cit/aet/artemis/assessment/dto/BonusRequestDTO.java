package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;

/**
 * DTO accepted by the create and update bonus endpoints.
 * <p>
 * The client sends the source grading scale as a bare {@code {id}} reference; the controller loads the managed grading
 * scale by that id. The bonus strategy is transient on the entity and is applied to the owning grading scale by the
 * controller. The {@code @JsonInclude(NON_EMPTY)} annotation follows the module-wide DTO convention (enforced by
 * {@code AssessmentCodeStyleArchitectureTest}); it is inert for this inbound-only body, because {@code @JsonInclude}
 * affects only serialization and a request DTO is never serialized by the server.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BonusRequestDTO(Long id, double weight, BonusStrategy bonusStrategy, GradingScaleIdDTO sourceGradingScale) {

    /**
     * Bare grading-scale reference carrying only the id the controller uses to load the managed entity.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record GradingScaleIdDTO(Long id) {
    }

    /**
     * @return the id of the referenced source grading scale, or {@code null} if none was provided
     */
    public Long sourceGradingScaleId() {
        return sourceGradingScale == null ? null : sourceGradingScale.id();
    }
}
