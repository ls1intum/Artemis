package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.beans.PropertyEditorSupport;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.quiz.domain.QuizAction;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseDatesDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizMessagingService;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;

/**
 * REST controller for managing QuizExercise actions.
 * CRUD operations have been moved to specialized resource files:
 * - Creation/Update: QuizExerciseCreationUpdateResource
 * - Deletion: QuizExerciseDeletionResource
 * - Retrieval: QuizExerciseRetrievalResource
 * - Evaluation: QuizExerciseEvaluationResource
 * - Batches: QuizExerciseBatchResource
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizSubmissionService quizSubmissionService;

    private final QuizExerciseService quizExerciseService;

    private final QuizMessagingService quizMessagingService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final AuthorizationCheckService authCheckService;

    private final QuizBatchService quizBatchService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    private final QuizBatchRepository quizBatchRepository;

    private final ExerciseVersionService exerciseVersionService;

    public QuizExerciseResource(QuizExerciseService quizExerciseService, QuizMessagingService quizMessagingService, QuizExerciseRepository quizExerciseRepository,
            UserRepository userRepository, InstanceMessageSendService instanceMessageSendService, AuthorizationCheckService authCheckService, QuizBatchService quizBatchService,
            QuizBatchRepository quizBatchRepository, QuizSubmissionService quizSubmissionService, ExerciseVersionService exerciseVersionService) {
        this.quizExerciseService = quizExerciseService;
        this.quizMessagingService = quizMessagingService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.authCheckService = authCheckService;
        this.quizBatchService = quizBatchService;
        this.quizBatchRepository = quizBatchRepository;
        this.quizSubmissionService = quizSubmissionService;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * Initialize the data binder for the quiz action enumeration
     *
     * @param binder the WebDataBinder for this controller
     */
    @InitBinder
    protected void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(QuizAction.class, new PropertyEditorSupport() {

            @Override
            public void setAsText(String text) {
                for (QuizAction action : QuizAction.values()) {
                    if (action.getValue().equals(text)) {
                        setValue(action);
                        return;
                    }
                }
                throw new IllegalArgumentException("Invalid value for QuizAction: " + text);
            }
        });
    }

    /**
     * PUT /quiz-exercises/:quizExerciseId/:action : perform the specified action for the quiz now
     *
     * @param quizExerciseId the id of the quiz exercise to start
     * @param action         the action to perform on the quiz (allowed actions: "start-now", "set-visible")
     * @return the response entity with status 200 if quiz was started, appropriate error code otherwise
     */
    @PutMapping("quiz-exercises/{quizExerciseId}/{action}")
    @EnforceAtLeastEditorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizExerciseDatesDTO> performActionForQuizExercise(@PathVariable Long quizExerciseId, @PathVariable QuizAction action) {
        log.debug("REST request to perform action {} on quiz exercise {}", action, quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("These actions are not allowed for exam exercises", ENTITY_NAME, "notAllowedInExam");
        }

        // Each case persists its state change via targeted @Modifying UPDATEs on the scalar columns it actually changes.
        // We deliberately avoid quizExerciseRepository.save(quizExercise) / saveAndFlush(quizExercise) here: the quiz is
        // loaded with its full question graph, and MultipleChoiceQuestion.answerOptions (and the DnD/SA siblings) use
        // @OneToMany + @OrderColumn + orphanRemoval=true, so a full-entity save deletes and re-inserts every child row
        // with fresh primary keys. Any student tab that was loaded before the save then hits ObjectNotFoundException on
        // submit. The targeted UPDATEs below touch only the columns we are changing (releaseDate / dueDate / batch
        // startTime), so existing answer-option / drag-item / spot IDs remain stable across these lifecycle actions.
        switch (action) {
            case START_NOW -> {
                // only synchronized quiz exercises can be started like this
                if (quizExercise.getQuizMode() != QuizMode.SYNCHRONIZED) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizNotSynchronized", "Quiz is not synchronized.")).build();
                }

                var quizBatch = quizBatchService.getOrCreateSynchronizedQuizBatch(quizExercise);
                // check if quiz hasn't already started
                if (quizBatch.isStarted()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyStarted", "Quiz has already started.")).build();
                }

                var now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                var previousReleaseDate = quizExercise.getReleaseDate();
                var releaseDateNeedsClamping = previousReleaseDate != null && previousReleaseDate.isAfter(now);
                var newReleaseDate = releaseDateNeedsClamping ? now : previousReleaseDate;
                var newDueDate = now.plusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);

                // getOrCreateSynchronizedQuizBatch may return a transient (id == null) batch for quizzes that never
                // started before. save() persists it; for already-existing batches it issues a plain UPDATE. Either
                // way there is no cascade back into the quizExercise graph (QuizBatch.quizExercise is a @ManyToOne
                // with no cascade), so child answer-option / drag-item IDs stay stable.
                quizBatch.setStartTime(now);
                quizBatchRepository.save(quizBatch);
                // Only rewrite releaseDate when it actually changes. Avoids a redundant column write in the common
                // case where the quiz was already visible to students before Start Now.
                if (releaseDateNeedsClamping) {
                    quizExerciseRepository.updateReleaseAndDueDate(quizExerciseId, newReleaseDate, newDueDate);
                }
                else {
                    quizExerciseRepository.updateDueDate(quizExerciseId, newDueDate);
                }

                // Mirror the writes onto the in-memory entity so the downstream DTO, broadcast and version creation
                // use the new values without needing to reload. The reload below provides an additional safety net.
                quizExercise.setReleaseDate(newReleaseDate);
                quizExercise.setDueDate(newDueDate);
            }
            case END_NOW -> {
                // editors may not end the quiz
                authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, quizExercise, user);
                // only synchronized quiz exercises can be started like this
                if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizSynchronized", "Quiz is synchronized."))
                            .build();
                }

                // endQuiz mutates the in-memory entity only (its contract, relied on by several re-evaluation tests).
                // Persist the scalar changes via targeted UPDATEs so the full-graph cascade is avoided.
                quizExerciseService.endQuiz(quizExercise);
                var lastStart = quizExercise.getDueDate().minusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);
                quizExerciseRepository.updateDueDate(quizExerciseId, quizExercise.getDueDate());
                quizBatchRepository.clampBatchStartTimesForEndNow(quizExerciseId, lastStart);
            }
            case SET_VISIBLE -> {
                // check if quiz is already visible
                if (quizExercise.isVisibleToStudents()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyVisible", "Quiz is already visible to students.")).build();
                }

                var now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                quizExerciseRepository.updateReleaseDate(quizExerciseId, now);
                quizExercise.setReleaseDate(now);
            }
            case START_BATCH -> {
                // Use the start-batch endpoint for starting batches instead
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizBatchActionNotAllowed", "Action not allowed."))
                        .build();
            }
        }

        // Reload to refresh proxy state before building the response DTO and broadcasting. Cheap (one SELECT with
        // the existing entity graph) and — critically — no write path was invoked above that could cascade into the
        // question graph, so child primary keys are guaranteed stable at this point.
        quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());

        if (action == QuizAction.START_NOW) {
            // notify the instance message send service to send the quiz exercise start schedule (if necessary
            instanceMessageSendService.sendQuizExerciseStartSchedule(quizExercise.getId());
        }
        else if (action == QuizAction.END_NOW) {
            // when the instructor ends the quiz, calculate the results
            quizSubmissionService.calculateAllResults(quizExerciseId);
        }

        // get the batch for synchronized quiz exercises and start-now action; otherwise it doesn't matter
        var quizBatch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, "any").orElse(null);

        // notify websocket channel of changes to the quiz exercise
        quizMessagingService.sendQuizExerciseToSubscribedClients(quizExercise, quizBatch, action);
        exerciseVersionService.createExerciseVersion(quizExercise, user);
        if (quizBatch != null && quizBatch.getStartTime() != null) {
            // Set the start date from the batch to the quiz exercise DTO
            quizExercise.setStartDate(quizBatch.getStartTime());
        }
        QuizExerciseDatesDTO quizExerciseDatesDTO = QuizExerciseDatesDTO.of(quizExercise);
        return new ResponseEntity<>(quizExerciseDatesDTO, HttpStatus.OK);
    }
}
