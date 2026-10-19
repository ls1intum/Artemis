package de.tum.cit.aet.artemis.exam.dto.submit;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single student participation as sent by the exam hand-in. Only the database id (matched against the existing
 * participation loaded from the DB) and the submissions are bound; the participant is deliberately dropped and
 * re-derived server-side from the authenticated user.
 * <p>
 * {@code submissions} is kept as a {@link List} (never a {@code Set}) so the server's exact
 * {@code submissions.size() != 1 -> skip} semantics are preserved by construction.
 *
 * @param id          the id of the existing student participation
 * @param submissions the participation's submissions (the server only acts when exactly one is present)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitExamParticipationDTO(Long id, List<SubmitExamSubmissionDTO> submissions) {
}
