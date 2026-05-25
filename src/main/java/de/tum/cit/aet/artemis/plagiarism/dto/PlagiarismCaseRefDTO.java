package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

/**
 * Lightweight, cycle-free projection of {@link PlagiarismCase} intended for embedding inside
 * {@code PostResponseDTO} and other response DTOs that previously serialized {@code Post.plagiarismCase}
 * via {@code @JsonIncludeProperties({"id"})}.
 *
 * @param id         the plagiarism case id
 * @param exerciseId the id of the exercise the case is for, nullable in legacy data
 * @param studentId  the id of the student the case concerns, nullable in legacy data
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseRefDTO(Long id, @Nullable Long exerciseId, @Nullable Long studentId) {

    /**
     * Build a {@link PlagiarismCaseRefDTO} from a {@link PlagiarismCase} entity. Returns {@code null}
     * if the input is {@code null} so callers can map nullable references without a guard.
     *
     * @param plagiarismCase the entity to project, may be {@code null}
     * @return the projected reference, or {@code null} when input is {@code null}
     */
    public static @Nullable PlagiarismCaseRefDTO from(@Nullable PlagiarismCase plagiarismCase) {
        if (plagiarismCase == null) {
            return null;
        }
        Long exerciseId = plagiarismCase.getExercise() != null ? plagiarismCase.getExercise().getId() : null;
        Long studentId = plagiarismCase.getStudent() != null ? plagiarismCase.getStudent().getId() : null;
        return new PlagiarismCaseRefDTO(plagiarismCase.getId(), exerciseId, studentId);
    }
}
