package de.tum.in.www1.artemis.service.messaging;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;

/**
 * This service is only active on a node that does not run with the 'scheduling' profile.
 * All requests are forwarded to a Hazelcast topic and a node with the 'scheduling' profile will then process it.
 */
@Service
@Profile("!scheduling")
public class DistributedInstanceMessageSendService implements InstanceMessageSendService {

    private final Logger log = LoggerFactory.getLogger(DistributedInstanceMessageSendService.class);

    private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

    private final HazelcastInstance hazelcastInstance;

    public DistributedInstanceMessageSendService(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public void sendProgrammingExerciseSchedule(Long exerciseId) {
        log.info("Sending schedule for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE, exerciseId);
    }

    @Override
    public void sendProgrammingExerciseScheduleCancel(Long exerciseId) {
        log.info("Sending schedule cancel for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE_CANCEL, exerciseId);
    }

    @Override
    public void sendModelingExerciseSchedule(Long exerciseId) {
        log.info("Sending schedule for modeling exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.MODELING_EXERCISE_SCHEDULE, exerciseId);
    }

    @Override
    public void sendModelingExerciseScheduleCancel(Long exerciseId) {
        log.info("Sending schedule cancel for modeling exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.MODELING_EXERCISE_SCHEDULE_CANCEL, exerciseId);
    }

    @Override
    public void sendModelingExerciseInstantClustering(Long exerciseId) {
        log.info("Sending schedule instant clustering for modeling exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.MODELING_EXERCISE_INSTANT_CLUSTERING, exerciseId);
    }

    @Override
    public void sendTextExerciseSchedule(Long exerciseId) {
        log.info("Sending schedule for text exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.TEXT_EXERCISE_SCHEDULE, exerciseId);
    }

    @Override
    public void sendTextExerciseScheduleCancel(Long exerciseId) {
        log.info("Sending schedule cancel for text exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.TEXT_EXERCISE_SCHEDULE_CANCEL, exerciseId);
    }

    @Override
    public void sendTextExerciseInstantClustering(Long exerciseId) {
        log.info("Sending schedule instant clustering for text exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.TEXT_EXERCISE_INSTANT_CLUSTERING, exerciseId);
    }

    @Override
    public void sendUnlockAllRepositories(Long exerciseId) {
        log.info("Sending unlock all repositories for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES, exerciseId);
    }

    @Override
    public void sendLockAllRepositories(Long exerciseId) {
        log.info("Sending lock all repositories for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_LOCK_REPOSITORIES, exerciseId);
    }

    @Override
    public void sendUnlockAllRepositoriesWithoutEarlierIndividualDueDate(Long exerciseId) {
        log.info("Sending unlock all repositories without an individual due date before now for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_WITHOUT_EARLIER_DUE_DATE, exerciseId);
    }

    @Override
    public void sendLockAllRepositoriesWithoutLaterIndividualDueDate(Long exerciseId) {
        log.info("Sending lock all repositories without an individual due date after now for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_LOCK_WITHOUT_LATER_DUE_DATE, exerciseId);
    }

    @Override
    public void sendRemoveNonActivatedUserSchedule(Long userId) {
        log.info("Sending remove non-activated user {} to broker.", userId);
        sendMessageDelayed(MessageTopic.USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS, userId);
    }

    @Override
    public void sendCancelRemoveNonActivatedUserSchedule(Long userId) {
        log.info("Sending cancel removal of non-activated user {} to broker.", userId);
        sendMessageDelayed(MessageTopic.USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USERS, userId);
    }

    @Override
    public void sendExerciseReleaseNotificationSchedule(Long exerciseId) {
        log.info("Sending prepare release notification for exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.EXERCISE_RELEASED_SCHEDULE, exerciseId);
    }

    @Override
    public void sendAssessedExerciseSubmissionNotificationSchedule(Long exerciseId) {
        log.info("Sending prepare assessed exercise submitted notification for exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.ASSESSED_EXERCISE_SUBMISSION_SCHEDULE, exerciseId);
    }

    @Override
    @FeatureToggle(Feature.ExamLiveStatistics)
    public void sendExamMonitoringSchedule(Long examId) {
        log.info("Sending schedule for exam monitoring {} to broker.", examId);
        sendMessageDelayed(MessageTopic.EXAM_MONITORING_SCHEDULE, examId);
    }

    @Override
    public void sendExamMonitoringScheduleCancel(Long examId) {
        log.info("Sending schedule cancel for exam monitoring {} to broker.", examId);
        sendMessageDelayed(MessageTopic.EXAM_MONITORING_SCHEDULE_CANCEL, examId);
    }

    @Override
    public void sendExamWorkingTimeChangeDuringConduction(Long studentExamId) {
        log.info("Sending schedule to reschedule student exam {} to broker.", studentExamId);
        sendMessageDelayed(MessageTopic.STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION, studentExamId);
    }

    @Override
    public void sendParticipantScoreSchedule(Long exerciseId, Long participantId, Long resultId) {
        log.info("Sending schedule participant score update for exercise {} and participant {}.", exerciseId, participantId);
        sendMessageDelayed(MessageTopic.PARTICIPANT_SCORE_SCHEDULE, exerciseId, participantId, resultId);
    }

    private void sendMessageDelayed(MessageTopic topic, Long payload) {
        exec.schedule(() -> hazelcastInstance.getTopic(topic.toString()).publish(payload), 1, TimeUnit.SECONDS);
    }

    private void sendMessageDelayed(MessageTopic topic, Long... payload) {
        exec.schedule(() -> hazelcastInstance.getTopic(topic.toString()).publish(payload), 1, TimeUnit.SECONDS);
    }
}
