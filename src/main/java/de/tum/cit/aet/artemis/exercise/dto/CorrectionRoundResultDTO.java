package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;

/**
 * One manual result of a submission together with the correction round it belongs to.
 * <p>
 * The scores overview renders one set of assessment actions per correction round, and it needs to know, per round,
 * whether that round has a result, whether it is finished and whether it has a complaint. Everything else about the
 * result is already covered by the flat fields of {@link ParticipationScoreDTO}, which describe the newest result.
 *
 * @param submissionId    the submission the result belongs to
 * @param resultId        the result
 * @param correctionRound the round the result belongs to, null for a result that is not a correction round
 * @param assessmentType  how the result was produced
 * @param completionDate  when the assessment was finished, null while it is still a draft
 * @param hasComplaint    whether the student complained about this result
 * @param score           the score of the result in percent, null while the assessment is still a draft
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CorrectionRoundResultDTO(long submissionId, long resultId, @Nullable Integer correctionRound, @Nullable AssessmentType assessmentType,
        @Nullable ZonedDateTime completionDate, @Nullable Boolean hasComplaint, @Nullable Double score) {
}
