package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Bonus;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;

/**
 * Represents a grading scale with the relevant parameters.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingScaleDTO(Long id, @NotNull GradeStepsDTO gradeSteps, @NotNull BonusStrategy bonusStrategy, @NotNull Set<BonusDTO> bonusFrom) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BonusDTO(Long id, double weight, Long sourceGradingScaleId) {

        public static BonusDTO of(Bonus bonus) {
            return new BonusDTO(bonus.getId(), bonus.getWeight(), bonus.getSourceGradingScale() != null ? bonus.getSourceGradingScale().getId() : null);
        }
    }

    public static GradingScaleDTO of(GradingScale scale) {
        Objects.requireNonNull(scale);

        GradeStepsDTO steps = new GradeStepsDTO(scale.getTitle(), scale.getGradeType(), scale.getGradeSteps(), scale.getMaxPoints(), scale.getPlagiarismGrade(),
                scale.getNoParticipationGrade(), scale.getPresentationsNumber(), scale.getPresentationsWeight());

        Set<BonusDTO> bonusDTOs = scale.getBonusFrom().stream().map(BonusDTO::of).collect(Collectors.toSet());

        return new GradingScaleDTO(scale.getId(), steps, scale.getBonusStrategy(), bonusDTOs);
    }
}
