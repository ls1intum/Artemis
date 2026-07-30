package de.tum.cit.aet.artemis.core.service.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;

/**
 * These messages are how a non-scheduling node asks the scheduling node to (re)schedule something. Losing one does not
 * degrade gracefully: the exercise, quiz or slide is simply never scheduled. The tests therefore pin down that each
 * request goes out on a <em>reliable</em> topic named after the message, rather than a fire-and-forget one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributedInstanceMessageSendServiceTest {

    /**
     * The service defers publishing by a second, so verifications allow for that.
     */
    private static final int PUBLISH_TIMEOUT_MS = 5000;

    @Mock
    private DistributedDataProvider distributedDataProvider;

    @Mock
    private DistributedTopic<Long> topic;

    private DistributedInstanceMessageSendService service;

    @BeforeEach
    void setUp() {
        when(distributedDataProvider.<Long>getReliableTopic(anyString())).thenReturn(topic);
        service = new DistributedInstanceMessageSendService(distributedDataProvider);
    }

    @Test
    void shouldPublishProgrammingExerciseScheduleOnReliableTopic() {
        service.sendProgrammingExerciseSchedule(42L);

        verify(distributedDataProvider, timeout(PUBLISH_TIMEOUT_MS)).getReliableTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE.toString());
        verify(topic, timeout(PUBLISH_TIMEOUT_MS)).publish(42L);
    }

    @Test
    void shouldPublishProgrammingExerciseScheduleCancelOnReliableTopic() {
        service.sendProgrammingExerciseScheduleCancel(42L);

        verify(distributedDataProvider, timeout(PUBLISH_TIMEOUT_MS)).getReliableTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE_CANCEL.toString());
        verify(topic, timeout(PUBLISH_TIMEOUT_MS)).publish(42L);
    }

    @Test
    void shouldPublishQuizExerciseStartOnReliableTopic() {
        service.sendQuizExerciseStartSchedule(7L);

        verify(distributedDataProvider, timeout(PUBLISH_TIMEOUT_MS)).getReliableTopic(MessageTopic.QUIZ_EXERCISE_START_SCHEDULE.toString());
        verify(topic, timeout(PUBLISH_TIMEOUT_MS)).publish(7L);
    }

    @Test
    void shouldPublishTextExerciseScheduleOnReliableTopic() {
        service.sendTextExerciseSchedule(9L);

        verify(distributedDataProvider, timeout(PUBLISH_TIMEOUT_MS)).getReliableTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE.toString());
        verify(topic, timeout(PUBLISH_TIMEOUT_MS)).publish(9L);
    }

    @Test
    void shouldPublishSlideUnhideScheduleOnReliableTopic() {
        service.sendSlideUnhideSchedule(3L);

        verify(distributedDataProvider, timeout(PUBLISH_TIMEOUT_MS)).getReliableTopic(MessageTopic.SLIDE_UNHIDE_SCHEDULE.toString());
        verify(topic, timeout(PUBLISH_TIMEOUT_MS)).publish(3L);
    }

    @Test
    void shouldPublishRemoveNonActivatedUserScheduleOnReliableTopic() {
        service.sendRemoveNonActivatedUserSchedule(11L);

        verify(distributedDataProvider, timeout(PUBLISH_TIMEOUT_MS)).getReliableTopic(MessageTopic.USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS.toString());
        verify(topic, timeout(PUBLISH_TIMEOUT_MS)).publish(11L);
    }
}
