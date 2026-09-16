package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;

/**
 * The text submission a team member sends over the team sync topic while they work on it.
 *
 * @param id        the id of the submission being edited, or {@code null} for the team's first submission
 * @param text      the text the member wrote
 * @param language  the language the client detected for that text
 * @param submitted whether the member submitted rather than auto-saved
 * @param results   the results the client holds for this submission; only whether there are any is read, to keep the
 *                      existing behaviour of starting a new submission once a submission carries feedback
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamTextSubmissionUpdateDTO(@Nullable Long id, @Nullable String text, @Nullable Language language, @Nullable Boolean submitted, @Nullable List<ResultIdDTO> results)
        implements Serializable {

    /**
     * Whether the client holds any result for this submission.
     *
     * @return true when the client sent at least one result
     */
    public boolean hasResults() {
        return results != null && !results.isEmpty();
    }

    /**
     * The identity of a result the client holds, which is all the sync path reads of it.
     *
     * @param id the id of the result
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ResultIdDTO(@Nullable Long id) implements Serializable {
    }
}
