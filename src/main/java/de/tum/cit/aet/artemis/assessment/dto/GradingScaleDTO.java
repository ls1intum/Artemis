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
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * DTO representing a {@link GradingScale}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingScaleDTO(@NotNull Long id, @NotNull GradeStepsDTO gradeSteps, BonusStrategy bonusStrategy, Set<BonusDTO> bonusFrom) {

    public GradingScaleDTO {
        bonusFrom = bonusFrom == null ? Set.of() : bonusFrom;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record BonusDTO(@NotNull Long id, double weight, @NotNull Long sourceGradingScaleId) {

        /**
         * Creates a {@link BonusDTO} from a {@link Bonus} entity.
         *
         * @param bonus the entity to convert
         * @return a DTO representation of the bonus
         */
        public static BonusDTO of(Bonus bonus) {
            Objects.requireNonNull(bonus, "bonus must exist");

            if (bonus.getSourceGradingScale() == null || bonus.getSourceGradingScale().getId() == null) {
                throw new BadRequestAlertException("Bonus source grading scale must exist", "Bonus", "invalidSourceGradingScale");
            }
            return new BonusDTO(bonus.getId(), bonus.getWeight(), bonus.getSourceGradingScale().getId());
        }
    }

    /**
     * Creates a {@link GradingScaleDTO} from a {@link GradingScale} entity.
     *
     * @param scale the grading scale entity
     * @return a DTO representing the given grading scale
     */
    public static GradingScaleDTO of(GradingScale scale) {
        Objects.requireNonNull(scale, "grading scale must exist");

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
