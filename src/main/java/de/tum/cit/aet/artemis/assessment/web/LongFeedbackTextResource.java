package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
public class LongFeedbackTextResource {

    private static final Logger log = LoggerFactory.getLogger(LongFeedbackTextResource.class);

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    private final ParticipationAuthorizationCheckService participationAuthorizationCheckService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ResultService resultService;

    public LongFeedbackTextResource(LongFeedbackTextRepository longFeedbackTextRepository, ParticipationAuthorizationCheckService participationAuthorizationCheckService,
            AuthorizationCheckService authorizationCheckService, ResultService resultService) {
        this.longFeedbackTextRepository = longFeedbackTextRepository;
        this.participationAuthorizationCheckService = participationAuthorizationCheckService;
        this.authorizationCheckService = authorizationCheckService;
        this.resultService = resultService;
    }

    /**
     * Gets the long feedback associated with the specified feedback.
     *
     * @param feedbackId The feedback for which the long feedback should be fetched.
     * @return The long feedback text belonging to the feedback with id {@code feedbackId}.
     */
    @GetMapping("feedbacks/{feedbackId}/long-feedback")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getLongFeedback(@PathVariable Long feedbackId) {
        log.debug("REST request to get long feedback: {}", feedbackId);

        final LongFeedbackText longFeedbackText = longFeedbackTextRepository.findByFeedbackIdWithFeedbackAndResultAndParticipationElseThrow(feedbackId);
        checkCanAccessResultElseThrow(longFeedbackText);

        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(longFeedbackText.getText());
    }

    private void checkCanAccessResultElseThrow(final LongFeedbackText longFeedbackText) {
        final Result result = longFeedbackText.getFeedback().getResult();
        final Participation participation = result.getSubmission().getParticipation();
        participationAuthorizationCheckService.checkCanAccessParticipationElseThrow(participation);
        checkFeedbackIsReleasedElseThrow(longFeedbackText.getFeedback(), result, participation);
    }

    /**
     * The long text of a feedback is served under the rules that decide whether the result is visible to its student: the result of an exam exercise is visible together with the
     * published results, an automatic result (including the automatic feedback of an AI assistant) is visible at once, and the result of a manual assessment is visible once the
     * assessment is completed and, for a course exercise, the assessment due date has passed. The decision is based on the assessment type of the result, not on the type of the
     * single feedback: feedback that was accepted from an automatic suggestion belongs to a manual assessment and is released with it. Like every read path of a full result, a
     * feedback that is hidden or only visible after the due date is not served before that. Staff members are not restricted.
     */
    private void checkFeedbackIsReleasedElseThrow(Feedback feedback, Result result, Participation participation) {
        Exercise exercise = participation.getExercise();
        if (authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return;
        }
        if (isExamResultNotPublished(participation) || isHiddenByVisibility(feedback, result, participation)) {
            throw new EntityNotFoundException("LongFeedbackText", feedback.getId());
        }
        AssessmentType assessmentType = result.getAssessmentType();
        boolean automaticResult = assessmentType == null || assessmentType == AssessmentType.AUTOMATIC || assessmentType == AssessmentType.AUTOMATIC_ATHENA;
        if (automaticResult) {
            return;
        }
        boolean assessmentReleased = result.getCompletionDate() != null && (exercise.isExamExercise() || ExerciseDateService.isAfterAssessmentDueDate(exercise));
        if (!assessmentReleased) {
            throw new EntityNotFoundException("LongFeedbackText", feedback.getId());
        }
    }

    /**
     * Whether the participation belongs to an exam exercise whose results the student may not see yet.
     */
    private boolean isExamResultNotPublished(Participation participation) {
        return participation.getExercise().isExamExercise() && (!(participation instanceof StudentParticipation studentParticipation)
                || participationAuthorizationCheckService.shouldHideExamExerciseResults(studentParticipation));
    }

    private boolean isHiddenByVisibility(Feedback feedback, Result result, Participation participation) {
        return feedback.isInvisible() || (feedback.isAfterDueDate() && resultService.shouldHideAfterDueDateFeedback(participation, result.getAssessmentType()));
    }
}
