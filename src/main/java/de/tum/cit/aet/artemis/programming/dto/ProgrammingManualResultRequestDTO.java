package de.tum.cit.aet.artemis.programming.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentNoteDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;

/**
 * The body of a manual programming assessment save/submit.
 * <p>
 * The tutor editor builds this from the assessment it loaded, concatenating referenced, unreferenced and
 * <em>automatic</em> feedback. Saving replaces {@code Result.feedbacks} (cascade ALL + orphanRemoval), so anything
 * this record cannot express is deleted from the database — which is why the automatic feedback's {@code testCase}
 * reference and {@code hasLongFeedbackText} are part of the contract and not "read-only" fields:
 * <ul>
 * <li>{@code testCase}: a missing reference clears {@code feedback.test_case_id} for every automatic feedback.</li>
 * <li>{@code hasLongFeedbackText}: the server re-attaches an existing long feedback row only when the incoming
 * feedback has an id and this flag; defaulting it to false silently drops the stored long feedback text.</li>
 * </ul>
 * The bare {@code @JsonInclude()} is deliberate: a request record must never drop empty collections
 * ({@code NON_EMPTY} would erase {@code feedbacks: []}, which the server reads to detect "assessment without
 * feedback"). Unknown properties are ignored so the client can keep posting the full result object graph
 * (submission, participation, assessor, ...) it holds; the server takes those from the database instead.
 *
 * @param id             the id of the result being written; the server overwrites it with the locked result's id
 * @param score          the score in percent computed by the client
 * @param successful     whether the result is successful; derived from the score when a score is present
 * @param rated          whether the result counts towards the score; the server rejects an unrated result
 * @param feedbacks      the complete feedback list that replaces the stored one
 * @param assessmentNote the internal assessment note
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude()
public record ProgrammingManualResultRequestDTO(Long id, Double score, Boolean successful, Boolean rated, List<ProgrammingManualFeedbackDTO> feedbacks,
        AssessmentNoteDTO assessmentNote) {

    /**
     * A reference to an existing grading instruction. Only the id is written back; every other property of the
     * instruction belongs to the exercise and is never edited through an assessment.
     *
     * @param id the grading instruction id
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude()
    public record GradingInstructionRefDTO(Long id) {
    }

    /**
     * A reference to an existing programming exercise test case. Only the id is written back; it exists so the
     * automatic feedback keeps its test-case link across the assessment round trip.
     *
     * @param id the test case id
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude()
    public record TestCaseRefDTO(Long id) {
    }

    /**
     * One feedback item of a manual assessment.
     *
     * @param id                  the feedback id; {@code null} for newly added feedback
     * @param text                the short feedback text
     * @param detailText          the detail text (the full text, even when a long feedback row exists)
     * @param hasLongFeedbackText whether a long feedback row belongs to this feedback
     * @param reference           the reference to the assessed element
     * @param credits             the points awarded by this feedback
     * @param positive            whether the feedback is positive
     * @param type                automatic, manual, unreferenced manual or adapted
     * @param visibility          when the feedback becomes visible to the student
     * @param gradingInstruction  the referenced grading instruction
     * @param testCase            the referenced test case
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude()
    public record ProgrammingManualFeedbackDTO(Long id, String text, String detailText, boolean hasLongFeedbackText, String reference, Double credits, Boolean positive,
            FeedbackType type, Visibility visibility, GradingInstructionRefDTO gradingInstruction, TestCaseRefDTO testCase) {

        /**
         * Builds the feedback entity this item describes.
         * <p>
         * {@code setDetailText} is called before {@code setHasLongFeedbackText} because the former resets the flag
         * (and creates a long feedback row for an oversized text). That is exactly the order in which Jackson applied
         * the two setters when this endpoint still bound the entity directly, so the long-feedback behavior is
         * unchanged.
         *
         * @return the feedback entity
         */
        public Feedback toEntity() {
            Feedback feedback = new Feedback();
            feedback.setId(id);
            feedback.setText(text);
            feedback.setDetailText(detailText);
            feedback.setHasLongFeedbackText(hasLongFeedbackText);
            feedback.setReference(reference);
            feedback.setCredits(credits);
            feedback.setPositive(positive);
            feedback.setType(type);
            feedback.setVisibility(visibility);
            if (gradingInstruction != null) {
                GradingInstruction instruction = new GradingInstruction();
                instruction.setId(gradingInstruction.id());
                feedback.setGradingInstruction(instruction);
            }
            if (testCase != null) {
                ProgrammingExerciseTestCase referencedTestCase = new ProgrammingExerciseTestCase();
                referencedTestCase.setId(testCase.id());
                feedback.setTestCase(referencedTestCase);
            }
            return feedback;
        }
    }

    /**
     * Builds the result entity this request describes. The submission, the assessor, the assessment type and the
     * result id are taken from the database by the endpoint, never from the request.
     *
     * @return the result entity carrying the client-supplied assessment
     */
    public Result toEntity() {
        Result result = new Result();
        result.setId(id);
        // Order matters and mirrors what Jackson did when this endpoint bound the entity directly: Result#setScore
        // derives `successful` from the score, and it ran last because the client serializes `successful` before
        // `score`. Setting the score last therefore keeps the derived value authoritative — reversing this makes a
        // stale `successful: true` on a below-100 score trip the scoreAndSuccessfulNotMatching check that the client
        // never hit before.
        result.setSuccessful(successful);
        result.setScore(score);
        result.setRated(Boolean.TRUE.equals(rated));
        if (assessmentNote != null) {
            AssessmentNote note = new AssessmentNote();
            note.setId(assessmentNote.id());
            note.setNote(assessmentNote.note());
            result.setAssessmentNote(note);
        }
        // Never null: the endpoint iterates the feedback list for its validation checks and the save path replaces
        // the stored collection with it.
        List<Feedback> feedbackEntities = new ArrayList<>();
        if (feedbacks != null) {
            feedbacks.stream().filter(Objects::nonNull).map(ProgrammingManualFeedbackDTO::toEntity).forEach(feedbackEntities::add);
        }
        result.setFeedbacks(feedbackEntities);
        return result;
    }
}
