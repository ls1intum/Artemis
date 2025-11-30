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

import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
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
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
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
 * - Deletion/Import: QuizExerciseDeletionResource
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

    private final GroupNotificationService groupNotificationService;

    private final QuizBatchService quizBatchService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    private final QuizBatchRepository quizBatchRepository;

    private final ExerciseVersionService exerciseVersionService;

    public QuizExerciseResource(QuizExerciseService quizExerciseService, QuizMessagingService quizMessagingService, QuizExerciseRepository quizExerciseRepository,
            UserRepository userRepository, InstanceMessageSendService instanceMessageSendService, AuthorizationCheckService authCheckService,
            GroupNotificationService groupNotificationService, QuizBatchService quizBatchService, QuizBatchRepository quizBatchRepository,
            QuizSubmissionService quizSubmissionService, ExerciseVersionService exerciseVersionService) {
        this.quizExerciseService = quizExerciseService;
        this.quizMessagingService = quizMessagingService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
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
     * @param action         the action to perform on the quiz (allowed actions: "start-now", "set-visible", "open-for-practice")
     * @return the response entity with status 200 if quiz was started, appropriate error code otherwise
     */
    @PutMapping("quiz-exercises/{quizExerciseId}/{action}")
    @EnforceAtLeastEditorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizExercise> performActionForQuizExercise(@PathVariable Long quizExerciseId, @PathVariable QuizAction action) {
        log.debug("REST request to perform action {} on quiz exercise {}", action, quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("These actions are not allowed for exam exercises", ENTITY_NAME, "notAllowedInExam");
        }

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

                // set release date to now, truncated to seconds
                var now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
                quizBatch.setStartTime(now);
                quizBatchRepository.save(quizBatch);
                if (quizExercise.getReleaseDate() != null && quizExercise.getReleaseDate().isAfter(now)) {
                    // preserve null and valid releaseDates for quiz start lifecycle event
                    quizExercise.setReleaseDate(now);
                }
                quizExercise.setDueDate(now.plusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS));
            }
            case END_NOW -> {
                // editors may not end the quiz
                authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, quizExercise, user);
                // only synchronized quiz exercises can be started like this
                if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizSynchronized", "Quiz is synchronized."))
                            .build();
                }

                // set release date to now, truncated to seconds because the database only stores seconds
                quizExerciseService.endQuiz(quizExercise);
            }
            case SET_VISIBLE -> {
                // check if quiz is already visible
                if (quizExercise.isVisibleToStudents()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyVisible", "Quiz is already visible to students.")).build();
                }

                // set quiz to visible
                quizExercise.setReleaseDate(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            }
            case OPEN_FOR_PRACTICE -> {
                // check if quiz has ended
                if (!quizExercise.isQuizEnded()) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizNotEndedYet", "Quiz hasn't ended yet."))
                            .build();
                }
                // check if quiz is already open for practice
                if (quizExercise.isIsOpenForPractice()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyOpenForPractice", "Quiz is already open for practice."))
                            .build();
                }

                // set quiz to open for practice
                quizExercise.setIsOpenForPractice(true);
                groupNotificationService.notifyStudentGroupAboutExercisePractice(quizExercise);
            }
            case START_BATCH -> {
                // Use the start-batch endpoint for starting batches instead
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizBatchActionNotAllowed", "Action not allowed."))
                        .build();
            }
        }

        // save quiz exercise
        quizExercise = quizExerciseRepository.saveAndFlush(quizExercise);
        // reload the quiz exercise with questions and statistics to prevent problems with proxy objects
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
        return new ResponseEntity<>(quizExercise, HttpStatus.OK);
    }
}
