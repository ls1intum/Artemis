package de.tum.cit.aet.artemis.exam.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Request DTO for creating a new exercise group.
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} lets the existing Angular client keep posting a full
 * entity-shaped body (it sets a nested {@code exam} and possibly {@code exercises}); properties beyond the ones
 * declared here are silently ignored, so no client change is required. The {@code id} and {@code exam} components are
 * accepted for validation only, preserving the previous endpoint behavior: a non-null id is rejected, and the nested
 * exam reference must be present and match the path exam. The persisted entity is always built from {@code title} and
 * {@code isMandatory} with the exam taken from the path.
 *
 * @param id          must be null; a new exercise group cannot already have an id (validated, never persisted)
 * @param title       the title of the new exercise group (may be blank / omitted)
 * @param isMandatory whether the exercise group must be included when generating student exams; defaults to
 *                        {@code true} when omitted, matching the entity default
 * @param exam        reference to the exam the group is created in; must match the path exam (validated, never
 *                        persisted)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseGroupCreateDTO(@Nullable Long id, @Nullable String title, @Nullable Boolean isMandatory, @Nullable ExamReferenceDTO exam) {

    /**
     * Slim exam reference carried in the create request for the exam-id consistency check. All other properties of the
     * entity-shaped exam the client sends are ignored.
     *
     * @param id the id of the exam the exercise group is created in
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExamReferenceDTO(@Nullable Long id) {
    }

    /**
     * Builds a create DTO from an existing exercise group (used to construct request bodies).
     *
     * @param exerciseGroup the exercise group to convert
     * @return the create DTO carrying its title, mandatory flag, and exam reference
     */
    public static ExerciseGroupCreateDTO of(ExerciseGroup exerciseGroup) {
        ExamReferenceDTO examReference = exerciseGroup.getExam() == null ? null : new ExamReferenceDTO(exerciseGroup.getExam().getId());
        return new ExerciseGroupCreateDTO(null, exerciseGroup.getTitle(), exerciseGroup.getIsMandatory(), examReference);
    }

    /**
     * Builds a new (transient) {@link ExerciseGroup} entity from this DTO. Only the title and the mandatory flag are
     * copied; the mandatory flag falls back to the entity default ({@code true}) when omitted in the request.
     *
     * @return the new exercise group entity, without an exam back-reference (set by the caller)
     */
    public ExerciseGroup toEntity() {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle(title);
        if (isMandatory != null) {
            exerciseGroup.setIsMandatory(isMandatory);
        }
        return exerciseGroup;
    }
}
