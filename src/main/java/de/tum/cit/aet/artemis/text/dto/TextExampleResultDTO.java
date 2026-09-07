package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.core.domain.Language;

/**
 * Read DTO for the example-result endpoint used in the example-assessment tutorial. It carries the (possibly masked)
 * result feedbacks together with the example text submission (text and blocks) the tutor needs to display and assess.
 * A {@code null} {@link #id()} signals a restricted/masked result to the client, mirroring the previous behavior.
 *
 * @param id         the result id, or {@code null} when the result is masked for the tutorial
 * @param feedbacks  the (masked or full) feedbacks
 * @param submission the example text submission with its text and blocks
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExampleResultDTO(Long id, List<FeedbackDTO> feedbacks, ExampleTextSubmissionDTO submission) implements Serializable {

    /**
     * The example text submission carrying the text and the text blocks the client renders.
     *
     * @param id       the submission id
     * @param text     the submission text
     * @param language the submission language
     * @param blocks   the text blocks
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExampleTextSubmissionDTO(Long id, String text, Language language, List<TextBlockDTO> blocks) implements Serializable {
    }
}
