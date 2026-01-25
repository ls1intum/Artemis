package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.QuizJoinException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.quiz.domain.QuizAction;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchJoinDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchWithPasswordDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizMessagingService;

/**
 * REST controller for handling quiz batches.
 * Allows students to join a batch and tutors to create and start batches.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseBatchResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseBatchResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizMessagingService quizMessagingService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final AuthorizationCheckService authCheckService;

    private final QuizBatchService quizBatchService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    private final QuizBatchRepository quizBatchRepository;

    public QuizExerciseBatchResource(QuizMessagingService quizMessagingService, QuizExerciseRepository quizExerciseRepository, UserRepository userRepository,
            InstanceMessageSendService instanceMessageSendService, AuthorizationCheckService authCheckService, QuizBatchService quizBatchService,
            QuizBatchRepository quizBatchRepository) {
        this.quizMessagingService = quizMessagingService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.authCheckService = authCheckService;
        this.quizBatchService = quizBatchService;
        this.quizBatchRepository = quizBatchRepository;
    }

    /**
     * POST /quiz-exercises/:quizExerciseId/join : add a student to a particular batch for participating in it and if in INDIVIDUAL mode create the batch to join
     *
     * @param quizExerciseId the id of the quizExercise to which the batch to join belongs
     * @param joinRequest    DTO with the password for the batch to join; unused for quizzes in INDIVIDUAL mode
     * @return the ResponseEntity with status 200 (OK) and with body the quizBatch that was joined
     */
    @PostMapping("quiz-exercises/{quizExerciseId}/join")
    @EnforceAtLeastStudent
    public ResponseEntity<QuizBatchDTO> joinBatch(@PathVariable Long quizExerciseId, @RequestBody QuizBatchJoinDTO joinRequest) {
        log.info("REST request to join quiz batch : {}, {}", quizExerciseId, joinRequest);
        var quizExercise = quizExerciseRepository.findByIdElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("Students cannot join quiz batches for exam exercises", ENTITY_NAME, "notAllowedInExam");
        }
        if (!authCheckService.isAllowedToSeeCourseExercise(quizExercise, user) || !quizExercise.isQuizStarted() || quizExercise.isQuizEnded()) {
            throw new AccessForbiddenException();
        }

        try {
            var batch = quizBatchService.joinBatch(quizExercise, user, joinRequest.password());
            return ResponseEntity.ok(QuizBatchDTO.of(batch));
        }
        catch (QuizJoinException ex) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", ex.getError(), ex.getMessage())).build();
        }
    }

    /**
     * PUT /quiz-exercises/:quizExerciseId/add-batch : add a new batch to the exercise for batched running
     *
     * @param quizExerciseId the id of the quizExercise to add the batch to
     * @return the ResponseEntity with status 200 (OK) and with body the new batch
     */
    @PutMapping("quiz-exercises/{quizExerciseId}/add-batch")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizBatchWithPasswordDTO> addBatch(@PathVariable Long quizExerciseId) {
        log.info("REST request to add quiz batch : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithBatchesElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("Batches cannot be created for exam exercises", ENTITY_NAME, "notAllowedInExam");
        }

        // TODO: quiz cleanup: it should be possible to limit the number of batches a tutor can create

        var quizBatch = quizBatchService.createBatch(quizExercise, user);
        quizBatch = quizBatchService.save(quizBatch);

        return ResponseEntity.ok(QuizBatchWithPasswordDTO.of(quizBatch));
    }

    /**
     * TODO: URL should be /quiz-exercises/batches/:batchId/join or smth for clarity
     * POST /quiz-exercises/:quizBatchId/start-batch : start a particular batch of the quiz
     *
     * @param quizBatchId the id of the quizBatch to start
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("quiz-exercises/{quizBatchId}/start-batch")
    @EnforceAtLeastTutor
    public ResponseEntity<QuizBatchDTO> startBatch(@PathVariable Long quizBatchId) {
        log.info("REST request to start quiz batch : {}", quizBatchId);
        QuizBatch batch = quizBatchRepository.findByIdElseThrow(quizBatchId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(batch.getQuizExercise().getId());
        var user = userRepository.getUserWithGroupsAndAuthorities();

        if (!user.getId().equals(batch.getCreator())) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, quizExercise, user);
        }
        else {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, quizExercise, user);
        }
        if (quizExercise.isExamExercise()) {
            throw new BadRequestAlertException("Batches cannot be started for exam exercises", ENTITY_NAME, "");
        }

        batch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, ZonedDateTime.now()));
        batch = quizBatchService.save(batch);

        // ensure that there is no scheduler that thinks the batch hasn't started yet
        instanceMessageSendService.sendQuizExerciseStartSchedule(quizExercise.getId());

        quizExercise.setQuizBatches(Set.of(batch));
        quizMessagingService.sendQuizExerciseToSubscribedClients(quizExercise, batch, QuizAction.START_BATCH);

        return ResponseEntity.ok(QuizBatchDTO.of(batch));
    }
}
