package de.tum.cit.aet.artemis.core.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserScheduleService;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.api.SlideUnhideScheduleApi;
import de.tum.cit.aet.artemis.notification.service.NotificationScheduleService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseScheduleService;
import de.tum.cit.aet.artemis.quiz.service.QuizScheduleService;

/**
 * This service is only available on a node with the 'scheduling' profile.
 * It receives messages from Hazelcast whenever another node sends a message to a specific topic and processes it on this node.
 */
@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class InstanceMessageReceiveService {

    private static final Logger log = LoggerFactory.getLogger(InstanceMessageReceiveService.class);

    private final ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    private final NotificationScheduleService notificationScheduleService;

    private final ParticipantScoreScheduleService participantScoreScheduleService;

    private final Optional<AthenaApi> athenaApi;

    private final UserScheduleService userScheduleService;

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final Optional<SlideUnhideScheduleApi> slideUnhideScheduleApi;

    private final DistributedDataProvider distributedDataProvider;

    private final QuizScheduleService quizScheduleService;

    public InstanceMessageReceiveService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseScheduleService programmingExerciseScheduleService,
            ExerciseRepository exerciseRepository, Optional<AthenaApi> athenaApi, DistributedDataProvider distributedDataProvider, UserRepository userRepository,
            UserScheduleService userScheduleService, NotificationScheduleService notificationScheduleService, ParticipantScoreScheduleService participantScoreScheduleService,
            QuizScheduleService quizScheduleService, Optional<SlideUnhideScheduleApi> slideUnhideScheduleApi) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseScheduleService = programmingExerciseScheduleService;
        this.athenaApi = athenaApi;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.userScheduleService = userScheduleService;
        this.notificationScheduleService = notificationScheduleService;
        this.participantScoreScheduleService = participantScoreScheduleService;
        this.distributedDataProvider = distributedDataProvider;
        this.quizScheduleService = quizScheduleService;
        this.slideUnhideScheduleApi = slideUnhideScheduleApi;
    }

    /**
     * Initialize all topic listeners on the distributed data provider
     */
    @PostConstruct
    public void init() {
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processScheduleProgrammingExercise((payload));
                processSchedulePotentialAthenaExercise((payload));
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE_CANCEL.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processScheduleProgrammingExerciseCancel(payload);
                processPotentialAthenaExerciseScheduleCancel(payload);
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processSchedulePotentialAthenaExercise(payload);
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE_CANCEL.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processPotentialAthenaExerciseScheduleCancel(payload);
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processRemoveNonActivatedUser((payload));
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USERS.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processCancelRemoveNonActivatedUser((payload));
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.EXERCISE_RELEASED_SCHEDULE.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processScheduleExerciseReleasedNotification((payload));
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.ASSESSED_EXERCISE_SUBMISSION_SCHEDULE.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processScheduleAssessedExerciseSubmittedNotification((payload));
            });
        });
        distributedDataProvider.<Long[]>getReliableTopic(MessageTopic.PARTICIPANT_SCORE_SCHEDULE.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processScheduleParticipantScore(payload[0], payload[1], payload[2]);
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.QUIZ_EXERCISE_START_SCHEDULE.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processScheduleQuizStart(payload);
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.QUIZ_EXERCISE_START_CANCEL.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processCancelQuizStart(payload);
            });
        });

        // Add listeners for slide unhide messages
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.SLIDE_UNHIDE_SCHEDULE.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processScheduleSlideUnhide(payload);
            });
        });
        distributedDataProvider.<Long>getReliableTopic(MessageTopic.SLIDE_UNHIDE_SCHEDULE_CANCEL.toString()).addMessageListener(payload -> {
            SecurityUtils.runAsSystem(() -> {
                processCancelSlideUnhide(payload);
            });
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

    public void processSchedulePotentialAthenaExercise(Long exerciseId) {
        log.info("Received schedule update for potential Athena exercise {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        athenaApi.ifPresent(api -> api.scheduleExerciseForAthenaIfRequired(exercise));
    }

    public void processPotentialAthenaExerciseScheduleCancel(Long exerciseId) {
        log.info("Received schedule cancel for potential Athena exercise {}", exerciseId);
        athenaApi.ifPresent(api -> api.cancelScheduledAthena(exerciseId));
    }

    public void processRemoveNonActivatedUser(Long userId) {
        log.info("Received remove non-activated user for user {}", userId);
        User user = userRepository.findByIdWithAuthoritiesElseThrow(userId);
        userScheduleService.scheduleForRemoveNonActivatedUser(user);
    }

    public void processCancelRemoveNonActivatedUser(Long userId) {
        log.info("Received cancel removal of non-activated user for user {}", userId);
        User user = userRepository.findByIdWithAuthoritiesElseThrow(userId);
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

    public void processScheduleParticipantScore(Long exerciseId, Long participantId, Long resultIdToBeDeleted) {
        log.debug("Received schedule participant score for exercise {} and participant {} (result to be deleted: {})", exerciseId, participantId, resultIdToBeDeleted);
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

    public void processScheduleSlideUnhide(Long slideId) {
        log.info("Received schedule update for slide unhiding {}", slideId);
        slideUnhideScheduleApi.ifPresent(api -> api.scheduleSlideUnhiding(slideId));
    }

    public void processCancelSlideUnhide(Long slideId) {
        log.info("Received schedule cancel for slide unhiding {}", slideId);
        slideUnhideScheduleApi.ifPresent(api -> api.cancelScheduledUnhiding(slideId));
    }
}
