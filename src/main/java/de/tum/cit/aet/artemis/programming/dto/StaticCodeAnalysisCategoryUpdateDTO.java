package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;

/**
 * Request shape of the static-code-analysis category update.
 * <p>
 * The client posts {@code (id, penalty, maxPenalty, state)}; {@code name} is kept because the 400 messages interpolate
 * it, and {@code exercise} is kept as a nested id reference because the 409 {@code scaCategoryExerciseIdError} check
 * compares the exercise id of the REQUEST body against the path variable. Resolving that id from the database instead
 * would change the semantics of the check.
 * <p>
 * The annotation is the bare form so that a request body keeps nulls on the wire.
 *
 * @param id         the id of the category to update; must not be null
 * @param name       the category name, used only for the validation messages
 * @param penalty    the penalty per issue in this category
 * @param maxPenalty the maximum penalty this category can deduct
 * @param state      whether the category is inactive, feedback-only or graded
 * @param exercise   optional reference to the exercise the category belongs to
 */
@JsonIgnoreProperties(ignoreUnknown = true)
// bare @JsonInclude(): request bodies must keep nulls and empty collections on the wire, and the shared
// architecture rule forbids spelling out Include.ALWAYS (only NON_EMPTY or no explicit value are allowed)
@JsonInclude()
public record StaticCodeAnalysisCategoryUpdateDTO(Long id, String name, Double penalty, Double maxPenalty, CategoryState state, ExerciseRefDTO exercise) {

    /**
     * Reference to the programming exercise a category belongs to. Only the id is bound; entity-serialized bodies
     * (used by the server-side tests) carry the full exercise object and everything else is ignored.
     *
     * @param id the exercise id
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    // bare @JsonInclude(): see the enclosing record
    @JsonInclude()
    public record ExerciseRefDTO(Long id) {
    }

    /**
     * Builds the transient category the update service reads the new values off. The exercise back-reference is never
     * set: the service only copies penalty, maxPenalty and state onto the managed category with the matching id.
     *
     * @return the transient category described by this DTO
     */
    public StaticCodeAnalysisCategory toEntity() {
        StaticCodeAnalysisCategory category = new StaticCodeAnalysisCategory();
        category.setId(id);
        category.setName(name);
        category.setPenalty(penalty);
        category.setMaxPenalty(maxPenalty);
        category.setState(state);
        return category;
    }
}
