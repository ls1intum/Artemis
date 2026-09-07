package de.tum.cit.aet.artemis.exam.dto.submit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Language;

/**
 * The text-submission variant of {@link SubmitExamSubmissionDTO}: carries the existing submission id, the student's
 * latest answer text and the client-detected {@link Language}.
 * <p>
 * The language is part of the wire contract on purpose. The downstream save
 * ({@code StudentExamService.saveSubmissionTextExercise}) persists the client submission through a JPA merge, which
 * overwrites <i>every</i> mapped column of the existing row from the posted entity. Omitting the language here would
 * therefore null it out on every hand-in text edit — a regression against the legacy full-entity body, which round-tripped
 * the language the client detects in {@code text-exam-submission.component.ts}. The submission {@code type} and
 * {@code exampleSubmission} columns are the other merge-overwritten fields, but they are always {@code null} for exam
 * submissions (initialized with a {@code null} type in {@code ParticipationService}, never example submissions), so
 * dropping them is a no-op and they are intentionally not part of this DTO.
 *
 * @param id       the id of the existing text submission the answer belongs to
 * @param text     the submitted answer text (may be {@code null} if the student left it empty)
 * @param language the client-detected language of the answer text (may be {@code null})
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextExamSubmissionDTO(Long id, String text, Language language) implements SubmitExamSubmissionDTO {
}
