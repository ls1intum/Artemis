package de.tum.cit.aet.artemis.core.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.communication.service.NotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.UserScheduleService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseAthenaConfigService;
import de.tum.cit.aet.artemis.iris.api.IrisLectureUnitAutoIngestionApi;
import de.tum.cit.aet.artemis.lecture.service.SlideUnhideScheduleService;
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

    private final ExerciseAthenaConfigService exerciseAthenaConfigService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final SlideUnhideScheduleService slideUnhideScheduleService;

    private final HazelcastInstance hazelcastInstance;

    private final QuizScheduleService quizScheduleService;

    private final Optional<IrisLectureUnitAutoIngestionApi> irisLectureUnitAutoIngestionApi;

    public InstanceMessageReceiveService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseScheduleService programmingExerciseScheduleService,
            ExerciseRepository exerciseRepository, ExerciseAthenaConfigService exerciseAthenaConfigService, Optional<AthenaApi> athenaApi,
            @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, UserRepository userRepository, UserScheduleService userScheduleService,
            NotificationScheduleService notificationScheduleService, ParticipantScoreScheduleService participantScoreScheduleService, QuizScheduleService quizScheduleService,
            SlideUnhideScheduleService slideUnhideScheduleService, Optional<IrisLectureUnitAutoIngestionApi> irisLectureUnitAutoIngestionApi) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseScheduleService = programmingExerciseScheduleService;
        this.athenaApi = athenaApi;
        this.exerciseRepository = exerciseRepository;
        this.exerciseAthenaConfigService = exerciseAthenaConfigService;
        this.userRepository = userRepository;
        this.userScheduleService = userScheduleService;
        this.notificationScheduleService = notificationScheduleService;
        this.participantScoreScheduleService = participantScoreScheduleService;
        this.hazelcastInstance = hazelcastInstance;
        this.quizScheduleService = quizScheduleService;
        this.slideUnhideScheduleService = slideUnhideScheduleService;
        this.irisLectureUnitAutoIngestionApi = irisLectureUnitAutoIngestionApi;
    }

    /**
     * Initialize all topic listeners from hazelcast
     */
    @PostConstruct
    public void init() {
        hazelcastInstance.<Long>getTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleProgrammingExercise((message.getMessageObject()));
            processSchedulePotentialAthenaExercise((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleProgrammingExerciseCancel(message.getMessageObject());
            processPotentialAthenaExerciseScheduleCancel(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processSchedulePotentialAthenaExercise(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processPotentialAthenaExerciseScheduleCancel(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processRemoveNonActivatedUser((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USERS.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processCancelRemoveNonActivatedUser((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.EXERCISE_RELEASED_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleExerciseReleasedNotification((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.ASSESSED_EXERCISE_SUBMISSION_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleAssessedExerciseSubmittedNotification((message.getMessageObject()));
        });
        hazelcastInstance.<Long[]>getTopic(MessageTopic.PARTICIPANT_SCORE_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleParticipantScore(message.getMessageObject()[0], message.getMessageObject()[1], message.getMessageObject()[2]);
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.QUIZ_EXERCISE_START_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleQuizStart(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.QUIZ_EXERCISE_START_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processCancelQuizStart(message.getMessageObject());
        });

        // Add listeners for slide unhide messages
        hazelcastInstance.<Long>getTopic(MessageTopic.SLIDE_UNHIDE_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleSlideUnhide(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.SLIDE_UNHIDE_SCHEDULE_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processCancelSlideUnhide(message.getMessageObject());
        });

        hazelcastInstance.<Long>getTopic(MessageTopic.LECTURE_UNIT_AUTO_INGESTION_SCHEDULE.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processLectureUnitAutoIngestionSchedule(message.getMessageObject());
        });
        hazelcastInstance.<Long>getTopic(MessageTopic.LECTURE_UNIT_AUTO_INGESTION_SCHEDULE_CANCEL.toString()).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processLectureUnitAutoIngestionScheduleCancel(message.getMessageObject());
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
        exerciseAthenaConfigService.loadAthenaConfig(exercise);
        athenaApi.ifPresent(api -> api.scheduleExerciseForAthenaIfRequired(exercise));
    }

    public void processPotentialAthenaExerciseScheduleCancel(Long exerciseId) {
        log.info("Received schedule cancel for potential Athena exercise {}", exerciseId);
        athenaApi.ifPresent(api -> api.cancelScheduledAthena(exerciseId));
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
        slideUnhideScheduleService.scheduleSlideUnhiding(slideId);
    }

    public void processCancelSlideUnhide(Long slideId) {
        log.info("Received schedule cancel for slide unhiding {}", slideId);
        slideUnhideScheduleService.cancelScheduledUnhiding(slideId);
    }

    public void processLectureUnitAutoIngestionSchedule(Long lectureUnitId) {
        log.info("Received schedule lecture unit ingestion for lecture unit id {}", lectureUnitId);
        irisLectureUnitAutoIngestionApi.ifPresent(api -> api.scheduleLectureUnitAutoIngestion(lectureUnitId));
    }

    public void processLectureUnitAutoIngestionScheduleCancel(Long lectureUnitId) {
        log.info("Received schedule cancel lecture unit ingestion for lecture unit id {}", lectureUnitId);
        irisLectureUnitAutoIngestionApi.ifPresent(api -> api.cancelLectureUnitAutoIngestion(lectureUnitId));
    }
}
