package de.tum.cit.aet.artemis.core.service.messaging;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

/**
 * This service is only active on a node that does not run with the 'scheduling' profile.
 * All requests are forwarded to a Hazelcast topic and a node with the 'scheduling' profile will then process it.
 */
@Service
@Profile("!scheduling & core")
public class DistributedInstanceMessageSendService implements InstanceMessageSendService {

    private static final Logger log = LoggerFactory.getLogger(DistributedInstanceMessageSendService.class);

    private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

    private final HazelcastInstance hazelcastInstance;

    public DistributedInstanceMessageSendService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
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
    public void sendUnlockAllStudentRepositoriesAndParticipations(Long exerciseId) {
        log.info("Sending unlock all repositories for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_AND_PARTICIPATIONS, exerciseId);
    }

    @Override
    public void sendUnlockAllStudentRepositories(Long exerciseId) {
        log.info("Sending unlock all repositories for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES, exerciseId);
    }

    @Override
    public void sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        log.info("Sending unlock all repositories and participations with a start date in the past and a due date in the future for programming exercise {} to broker.",
                exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_AND_PARTICIPATIONS_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE, exerciseId);
    }

    @Override
    public void sendUnlockAllStudentRepositoriesWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        log.info("Sending unlock all student repositories with earlier start date and later due date for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE, exerciseId);
    }

    @Override
    public void sendUnlockAllStudentParticipationsWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        log.info("Sending unlock all student participations with earlier start date and later due date for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_UNLOCK_PARTICIPATIONS_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE, exerciseId);
    }

    @Override
    public void sendLockAllStudentRepositoriesAndParticipations(Long exerciseId) {
        log.info("Sending lock all student repositories and participations for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_LOCK_REPOSITORIES_AND_PARTICIPATIONS, exerciseId);
    }

    @Override
    public void sendLockAllStudentRepositories(Long exerciseId) {
        log.info("Sending lock all student repositories for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_LOCK_REPOSITORIES, exerciseId);
    }

    @Override
    public void sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(Long exerciseId) {
        log.info("Sending lock all student repositories and participations with past due date for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_LOCK_REPOSITORIES_AND_PARTICIPATIONS_WITH_EARLIER_DUE_DATE, exerciseId);
    }

    @Override
    public void sendLockAllStudentParticipationsWithEarlierDueDate(Long exerciseId) {
        log.info("Sending lock all student participations with past due date for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed(MessageTopic.PROGRAMMING_EXERCISE_LOCK_PARTICIPATIONS_WITH_EARLIER_DUE_DATE, exerciseId);
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
    public void sendRescheduleAllStudentExams(Long examId) {
        log.info("Sending message to reschedule exam {} to broker.", examId);
        sendMessageDelayed(MessageTopic.EXAM_RESCHEDULE_DURING_CONDUCTION, examId);
    }

    @Override
    public void sendStudentExamIndividualWorkingTimeChangeDuringConduction(Long studentExamId) {
        log.info("Sending schedule to reschedule student exam {} to broker.", studentExamId);
        sendMessageDelayed(MessageTopic.STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION, studentExamId);
    }

    @Override
    public void sendParticipantScoreSchedule(Long exerciseId, Long participantId, Long resultId) {
        log.info("Sending schedule participant score update for exercise {} and participant {}.", exerciseId, participantId);
        sendMessageDelayed(MessageTopic.PARTICIPANT_SCORE_SCHEDULE, exerciseId, participantId, resultId);
    }

    @Override
    public void sendQuizExerciseStartSchedule(Long quizExerciseId) {
        log.info("Sending schedule for quiz exercise {} to broker.", quizExerciseId);
        sendMessageDelayed(MessageTopic.QUIZ_EXERCISE_START_SCHEDULE, quizExerciseId);
    }

    @Override
    public void sendQuizExerciseStartCancel(Long quizExerciseId) {
        log.info("Sending schedule cancel for quiz exercise {} to broker.", quizExerciseId);
        sendMessageDelayed(MessageTopic.QUIZ_EXERCISE_START_CANCEL, quizExerciseId);
    }

    // NOTE: Don't remove any of the following methods despite the warning.
    private void sendMessageDelayed(MessageTopic topic, Long payload) {
        exec.schedule(() -> hazelcastInstance.getTopic(topic.toString()).publish(payload), 1, TimeUnit.SECONDS);
    }

    private void sendMessageDelayed(MessageTopic topic, Long... payload) {
        exec.schedule(() -> hazelcastInstance.getTopic(topic.toString()).publish(payload), 1, TimeUnit.SECONDS);
    }
}
