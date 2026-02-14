package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Bonus;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;

/**
 * Represents a grading scale with the relevant parameters.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingScaleDTO(Long id, @NotNull GradeStepsDTO gradeSteps, @NotNull BonusStrategy bonusStrategy, @NotNull Set<BonusDTO> bonusFrom) {

    public GradingScaleDTO {
        bonusFrom = bonusFrom == null ? Set.of() : bonusFrom;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BonusDTO(Long id, double weight, Long sourceGradingScaleId) {

        public static BonusDTO of(Bonus bonus) {
            return new BonusDTO(bonus.getId(), bonus.getWeight(), bonus.getSourceGradingScale() != null ? bonus.getSourceGradingScale().getId() : null);
        }
    }

    public static GradingScaleDTO of(GradingScale scale) {
        Objects.requireNonNull(scale);

        Set<GradeStep> gradeSteps = Set.of();
        if (scale.getGradeSteps() != null && Hibernate.isInitialized(scale.getGradeSteps())) {
            gradeSteps = scale.getGradeSteps();
        }
        GradeStepsDTO steps = new GradeStepsDTO(scale.getTitle(), scale.getGradeType(), gradeSteps, scale.getMaxPoints(), scale.getPlagiarismGrade(),
                scale.getNoParticipationGrade(), scale.getPresentationsNumber(), scale.getPresentationsWeight());

        Set<BonusDTO> bonusDTOs = Set.of();
        if (scale.getBonusFrom() != null && Hibernate.isInitialized(scale.getBonusFrom())) {
            bonusDTOs = scale.getBonusFrom().stream().map(BonusDTO::of).collect(Collectors.toSet());
        }

        return new GradingScaleDTO(scale.getId(), steps, scale.getBonusStrategy(), bonusDTOs);
    }
}
