package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Pyris DTO mapping of a {@code ProgrammingSubmission}
 *
 * @param completionDate date of submission
 * @param successful     whether the submission was successful
 * @param feedbacks      the feedback on the submission
 */
// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisResultDTO(
        Instant completionDate,
        boolean successful,
        List<PyrisFeedbackDTO> feedbacks
) {}
// @formatter:on
