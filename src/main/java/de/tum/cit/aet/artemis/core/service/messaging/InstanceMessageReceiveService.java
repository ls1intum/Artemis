package de.tum.cit.aet.artemis.core.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.athena.service.AthenaScheduleService;
import de.tum.cit.aet.artemis.communication.service.NotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.UserScheduleService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseScheduleService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseScheduleService;
import de.tum.cit.aet.artemis.quiz.service.QuizScheduleService;

/**
 * This service is only available on a node with the 'scheduling' profile.
 * It receives messages from Hazelcast whenever another node sends a message to a specific topic and processes it on this node.
 */
@Service
@Profile(PROFILE_SCHEDULING)
public class InstanceMessageReceiveService {

    private static final Logger log = LoggerFactory.getLogger(InstanceMessageReceiveService.class);

    private final ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    private final ModelingExerciseScheduleService modelingExerciseScheduleService;

    private final NotificationScheduleService notificationScheduleService;

    private final ParticipantScoreScheduleService participantScoreScheduleService;

    private final Optional<AthenaScheduleService> athenaScheduleService;

    private final UserScheduleService userScheduleService;

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final UserRepository userRepository;

    private final RedissonClient redissonClient;

    private final QuizScheduleService quizScheduleService;

    public InstanceMessageReceiveService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseScheduleService programmingExerciseScheduleService,
            ModelingExerciseRepository modelingExerciseRepository, ModelingExerciseScheduleService modelingExerciseScheduleService, ExerciseRepository exerciseRepository,
            Optional<AthenaScheduleService> athenaScheduleService, RedissonClient redissonClient, UserRepository userRepository, UserScheduleService userScheduleService,
            NotificationScheduleService notificationScheduleService, ParticipantScoreScheduleService participantScoreScheduleService, QuizScheduleService quizScheduleService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseScheduleService = programmingExerciseScheduleService;
        this.athenaScheduleService = athenaScheduleService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseScheduleService = modelingExerciseScheduleService;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.userScheduleService = userScheduleService;
        this.notificationScheduleService = notificationScheduleService;
        this.participantScoreScheduleService = participantScoreScheduleService;
        this.redissonClient = redissonClient;
        this.quizScheduleService = quizScheduleService;
    }

    /**
     * Initialize all topic listeners from hazelcast
     */
    @PostConstruct
    public void init() {
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleProgrammingExercise((message));
            processSchedulePotentialAthenaExercise((message));
        });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE_CANCEL.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleProgrammingExerciseCancel(message);
            processPotentialAthenaExerciseScheduleCancel(message);
        });
        redissonClient.getTopic(MessageTopic.MODELING_EXERCISE_SCHEDULE.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleModelingExercise((message));
        });
        redissonClient.getTopic(MessageTopic.MODELING_EXERCISE_SCHEDULE_CANCEL.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleModelingExerciseCancel(message);
        });
        redissonClient.getTopic(MessageTopic.MODELING_EXERCISE_INSTANT_CLUSTERING.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processModelingExerciseInstantClustering((message));
        });
        redissonClient.getTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processSchedulePotentialAthenaExercise(message);
        });
        redissonClient.getTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE_CANCEL.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processPotentialAthenaExerciseScheduleCancel(message);
        });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processUnlockAllRepositories((message));
        });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_AND_PARTICIPATIONS.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processUnlockAllRepositoriesAndParticipations((message));
        });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_AND_PARTICIPATIONS_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE.toString())
                .addListener(Long.class, (channel, message) -> {
                    SecurityUtils.setAuthorizationObject();
                    processUnlockAllRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate((message));
                });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE.toString()).addListener(Long.class,
                (channel, message) -> {
                    SecurityUtils.setAuthorizationObject();
                    processUnlockAllRepositoriesWithEarlierStartDateAndLaterDueDate((message));
                });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_PARTICIPATIONS_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE.toString()).addListener(Long.class,
                (channel, message) -> {
                    SecurityUtils.setAuthorizationObject();
                    processUnlockAllParticipationsWithEarlierStartDateAndLaterDueDate((message));
                });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_LOCK_REPOSITORIES_AND_PARTICIPATIONS.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processLockAllRepositoriesAndParticipations((message));
        });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_LOCK_REPOSITORIES.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processLockAllRepositories((message));
        });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_LOCK_REPOSITORIES_AND_PARTICIPATIONS_WITH_EARLIER_DUE_DATE.toString()).addListener(Long.class,
                (channel, message) -> {
                    SecurityUtils.setAuthorizationObject();
                    processLockAllRepositoriesAndParticipationsWithEarlierDueDate((message));
                });
        redissonClient.getTopic(MessageTopic.PROGRAMMING_EXERCISE_LOCK_PARTICIPATIONS_WITH_EARLIER_DUE_DATE.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processLockAllParticipationsWithEarlierDueDate((message));
        });
        redissonClient.getTopic(MessageTopic.USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processRemoveNonActivatedUser((message));
        });
        redissonClient.getTopic(MessageTopic.USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USERS.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processCancelRemoveNonActivatedUser((message));
        });
        redissonClient.getTopic(MessageTopic.EXERCISE_RELEASED_SCHEDULE.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleExerciseReleasedNotification((message));
        });
        redissonClient.getTopic(MessageTopic.ASSESSED_EXERCISE_SUBMISSION_SCHEDULE.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleAssessedExerciseSubmittedNotification((message));
        });
        redissonClient.getTopic(MessageTopic.EXAM_RESCHEDULE_DURING_CONDUCTION.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processRescheduleExamDuringConduction(message);
        });
        redissonClient.getTopic(MessageTopic.STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processStudentExamIndividualWorkingTimeChangeDuringConduction(message);
        });
        redissonClient.getTopic(MessageTopic.PARTICIPANT_SCORE_SCHEDULE.toString()).addListener(Long[].class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleParticipantScore(message[0], message[1], message[2]);
        });
        redissonClient.getTopic(MessageTopic.QUIZ_EXERCISE_START_SCHEDULE.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleQuizStart(message);
        });
        redissonClient.getTopic(MessageTopic.QUIZ_EXERCISE_START_CANCEL.toString()).addListener(Long.class, (channel, message) -> {
            SecurityUtils.setAuthorizationObject();
            processCancelQuizStart(message);
        });
    }

    public void processScheduleProgrammingExercise(Long exerciseId) {
        log.info("Received schedule update for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        programmingExerciseScheduleService.updateScheduling(programmingExercise);
    }

    public void processScheduleProgrammingExerciseCancel(Long exerciseId) {
        log.info("Received schedule cancel for programming exercise {}", exerciseId);
        // The exercise might already be deleted, so we can not get it from the database.
        // Use the ID directly instead.
        programmingExerciseScheduleService.cancelAllScheduledTasks(exerciseId);
    }

    public void processScheduleModelingExercise(Long exerciseId) {
        log.info("Received schedule update for modeling exercise {}", exerciseId);
        ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);
        modelingExerciseScheduleService.updateScheduling(modelingExercise);
    }

    public void processScheduleModelingExerciseCancel(Long exerciseId) {
        log.info("Received schedule cancel for modeling exercise {}", exerciseId);
        // The exercise might already be deleted, so we can not get it from the database.
        // Use the ID directly instead.
        modelingExerciseScheduleService.cancelAllScheduledTasks(exerciseId);
    }

    public void processModelingExerciseInstantClustering(Long exerciseId) {
        log.info("Received schedule instant clustering for modeling exercise {}", exerciseId);
        ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);
        modelingExerciseScheduleService.scheduleExerciseForInstant(modelingExercise);
    }

    public void processSchedulePotentialAthenaExercise(Long exerciseId) {
        log.info("Received schedule update for potential Athena exercise {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        athenaScheduleService.ifPresent(service -> service.scheduleExerciseForAthenaIfRequired(exercise));
    }

    public void processPotentialAthenaExerciseScheduleCancel(Long exerciseId) {
        log.info("Received schedule cancel for potential Athena exercise {}", exerciseId);
        athenaScheduleService.ifPresent(service -> service.cancelScheduledAthena(exerciseId));
    }

    public void processUnlockAllRepositoriesAndParticipations(Long exerciseId) {
        log.info("Received unlock all repositories for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are unlocked as fast as possible
        programmingExerciseScheduleService.unlockAllStudentRepositoriesAndParticipations(programmingExercise).run();
    }

    public void processUnlockAllRepositories(Long exerciseId) {
        log.info("Received unlock all repositories for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are unlocked as fast as possible
        programmingExerciseScheduleService.unlockAllStudentRepositories(programmingExercise).run();
    }

    public void processUnlockAllRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        log.info("Received unlock all repositories and participations with earlier start date and later due date for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are unlocked as fast as possible
        programmingExerciseScheduleService.unlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExercise).run();
    }

    public void processUnlockAllRepositoriesWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        log.info("Received unlock all repositories with earlier start date and later due date for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are unlocked as fast as possible
        programmingExerciseScheduleService.unlockAllStudentRepositoriesWithEarlierStartDateAndLaterDueDate(programmingExercise).run();
    }

    public void processUnlockAllParticipationsWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        log.info("Received unlock all participations with earlier start date and later due date for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are unlocked as fast as possible
        programmingExerciseScheduleService.unlockAllStudentParticipationsWithEarlierStartDateAndLaterDueDate(programmingExercise).run();
    }

    public void processLockAllRepositoriesAndParticipations(Long exerciseId) {
        log.info("Received lock all repositories and participations for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExerciseScheduleService.lockAllStudentRepositoriesAndParticipations(programmingExercise).run();
    }

    public void processLockAllRepositories(Long exerciseId) {
        log.info("Received lock all repositories for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExerciseScheduleService.lockAllStudentRepositories(programmingExercise).run();
    }

    public void processLockAllRepositoriesAndParticipationsWithEarlierDueDate(Long exerciseId) {
        log.info("Received lock all repositories and participations with earlier due date for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExerciseScheduleService.lockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(programmingExercise).run();
    }

    public void processLockAllParticipationsWithEarlierDueDate(Long exerciseId) {
        log.info("Received lock all participations with a due date in the past for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExerciseScheduleService.lockAllStudentParticipationsWithEarlierDueDate(programmingExercise).run();
    }

    public void processRemoveNonActivatedUser(Long userId) {
        log.info("Received remove non-activated user for user {}", userId);
        User user = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);
        userScheduleService.scheduleForRemoveNonActivatedUser(user);
    }

    public void processCancelRemoveNonActivatedUser(Long userId) {
        log.info("Received cancel removal of non-activated user for user {}", userId);
        User user = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);
        userScheduleService.cancelScheduleRemoveNonActivatedUser(user);
    }

    public void processScheduleExerciseReleasedNotification(Long exerciseId) {
        log.info("Received schedule update for exercise {} released notification ", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        notificationScheduleService.updateSchedulingForReleasedExercises(exercise);
    }

    public void processScheduleAssessedExerciseSubmittedNotification(Long exerciseId) {
        log.info("Received schedule update for assessed exercise submitted {} notification ", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        notificationScheduleService.updateSchedulingForAssessedExercisesSubmissions(exercise);
    }

    public void processRescheduleExamDuringConduction(Long examId) {
        log.info("Received reschedule of exam during conduction {}", examId);
        programmingExerciseScheduleService.rescheduleExamDuringConduction(examId);
    }

    public void processStudentExamIndividualWorkingTimeChangeDuringConduction(Long studentExamId) {
        log.info("Received reschedule of student exam during conduction {}", studentExamId);
        programmingExerciseScheduleService.rescheduleStudentExamDuringConduction(studentExamId);
    }

    public void processScheduleParticipantScore(Long exerciseId, Long participantId, Long resultIdToBeDeleted) {
        log.info("Received schedule participant score for exercise {} and participant {} (result to be deleted: {})", exerciseId, participantId, resultIdToBeDeleted);
        participantScoreScheduleService.scheduleTask(exerciseId, participantId, resultIdToBeDeleted);
    }

    public void processScheduleQuizStart(Long exerciseId) {
        log.info("Received schedule quiz start for quiz exercise {}", exerciseId);
        quizScheduleService.scheduleQuizStart(exerciseId);
    }

    public void processCancelQuizStart(Long exerciseId) {
        log.info("Received cancel quiz start for quiz exercise {}", exerciseId);
        quizScheduleService.cancelScheduledQuizStart(exerciseId);
    }
}
