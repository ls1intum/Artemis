package de.tum.cit.aet.artemis.metis.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.dto.MetisAnswerRequestDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseStatusDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisStudentCheckDTO;
import de.tum.cit.aet.artemis.metis.service.MetisStudentCheckService;

/**
 * REST resource for a student's own comprehension check of a programming exercise: whether the exercise has one, the check itself, starting it and answering
 * its questions.
 * <p>
 * The endpoints are keyed by the exercise alone. The service finds the requesting user's graded participation and its check, so no participation id is ever
 * sent and no other student's check can be reached; the role check of the exercise only guards the membership in its course.
 * <p>
 * The status counts as an automatic call for the usage analysis, because the exercise page asks for it whenever it shows a programming exercise. Reading the
 * check is a view: the tab shows it when the student opens it.
 */
@Conditional(MetisEnabled.class)
@Lazy
@FeatureUsage(UserFeature.METIS_COMPREHENSION_CHECK)
@RestController
@RequestMapping("api/metis/")
public class MetisStudentCheckResource {

    private static final Logger log = LoggerFactory.getLogger(MetisStudentCheckResource.class);

    private final MetisStudentCheckService studentCheckService;

    public MetisStudentCheckResource(MetisStudentCheckService studentCheckService) {
        this.studentCheckService = studentCheckService;
    }

    /**
     * GET exercises/{exerciseId}/status : whether an exercise has an enabled comprehension check, which decides whether the exercise page shows the check tab.
     *
     * @param exerciseId the exercise
     * @return 200 with the status of the exercise
     */
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @GetMapping("exercises/{exerciseId}/status")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<MetisExerciseStatusDTO> getExerciseStatus(@PathVariable long exerciseId) {
        log.debug("REST request to get the Metis status of exercise {}", exerciseId);
        return ResponseEntity.ok(studentCheckService.getExerciseStatus(exerciseId));
    }

    /**
     * GET exercises/{exerciseId}/check : the comprehension check of the requesting user on an exercise, with its presented status, the user's window, the open
     * question and the answered questions.
     *
     * @param exerciseId the exercise
     * @return 200 with the check of the requesting user
     */
    @GetMapping("exercises/{exerciseId}/check")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<MetisStudentCheckDTO> getOwnCheck(@PathVariable long exerciseId) {
        log.debug("REST request to get the own Metis check of exercise {}", exerciseId);
        return ResponseEntity.ok(studentCheckService.getOwnCheck(exerciseId));
    }

    /**
     * POST exercises/{exerciseId}/check/start : starts the comprehension check of the requesting user and shows the first question. Starting a check that is in
     * progress already changes nothing.
     *
     * @param exerciseId the exercise
     * @return 200 with the started check; 403 without consent to the AI processing; 409 if the check is not enabled, does not exist, is not ready, or the
     *         user's window has not opened, has closed or leaves less time than one question needs
     */
    @PostMapping("exercises/{exerciseId}/check/start")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<MetisStudentCheckDTO> startOwnCheck(@PathVariable long exerciseId) {
        log.debug("REST request to start the own Metis check of exercise {}", exerciseId);
        return ResponseEntity.ok(studentCheckService.startOwnCheck(exerciseId));
    }

    /**
     * POST exercises/{exerciseId}/check/questions/{questionId}/answer : answers a shown question of the requesting user's comprehension check. The answer is
     * rated, and the response shows what comes next: a follow-up question, the next main question, or the completed check.
     *
     * @param exerciseId the exercise
     * @param questionId the question of the user's own check
     * @param answer     the answer; its text may be empty, since the client sends its draft when the timer expires
     * @return 200 with the check after the answer; 403 without consent to the AI processing; 404 if the question is not part of the user's check; 409 if the
     *         check is not enabled, does not exist or is not in progress, the question has not been shown, or the user's window closed more than the grace ago
     */
    @PostMapping("exercises/{exerciseId}/check/questions/{questionId}/answer")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<MetisStudentCheckDTO> submitAnswer(@PathVariable long exerciseId, @PathVariable long questionId, @RequestBody MetisAnswerRequestDTO answer) {
        log.debug("REST request to answer the question {} of the own Metis check of exercise {}", questionId, exerciseId);
        return ResponseEntity.ok(studentCheckService.submitAnswer(exerciseId, questionId, answer.answerText()));
    }
}
