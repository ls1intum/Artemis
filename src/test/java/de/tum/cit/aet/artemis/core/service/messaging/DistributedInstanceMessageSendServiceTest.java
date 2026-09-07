package de.tum.cit.aet.artemis.core.service.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    @Mock
    private DistributedDataProvider distributedDataProvider;

    @Mock
    private DistributedTopic<Long> topic;

    private DistributedInstanceMessageSendService service;

    @BeforeEach
    void setUp() {
        when(distributedDataProvider.<Long>getReliableTopic(anyString())).thenReturn(topic);
        // Run the deferred publish immediately, so the assertions do not depend on the scheduler or on machine load.
        service = new DistributedInstanceMessageSendService(distributedDataProvider, new SameThreadScheduledExecutor());
    }

    @Test
    void shouldPublishProgrammingExerciseScheduleOnReliableTopic() {
        service.sendProgrammingExerciseSchedule(42L);

        verify(distributedDataProvider).getReliableTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE.toString());
        verify(topic).publish(42L);
    }

    @Test
    void shouldPublishProgrammingExerciseScheduleCancelOnReliableTopic() {
        service.sendProgrammingExerciseScheduleCancel(42L);

        verify(distributedDataProvider).getReliableTopic(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE_CANCEL.toString());
        verify(topic).publish(42L);
    }

    @Test
    void shouldPublishQuizExerciseStartOnReliableTopic() {
        service.sendQuizExerciseStartSchedule(7L);

        verify(distributedDataProvider).getReliableTopic(MessageTopic.QUIZ_EXERCISE_START_SCHEDULE.toString());
        verify(topic).publish(7L);
    }

    @Test
    void shouldPublishTextExerciseScheduleOnReliableTopic() {
        service.sendTextExerciseSchedule(9L);

        verify(distributedDataProvider).getReliableTopic(MessageTopic.TEXT_EXERCISE_SCHEDULE.toString());
        verify(topic).publish(9L);
    }

    @Test
    void shouldPublishSlideUnhideScheduleOnReliableTopic() {
        service.sendSlideUnhideSchedule(3L);

        verify(distributedDataProvider).getReliableTopic(MessageTopic.SLIDE_UNHIDE_SCHEDULE.toString());
        verify(topic).publish(3L);
    }

    @Test
    void shouldPublishRemoveNonActivatedUserScheduleOnReliableTopic() {
        service.sendRemoveNonActivatedUserSchedule(11L);

        verify(distributedDataProvider).getReliableTopic(MessageTopic.USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS.toString());
        verify(topic).publish(11L);
    }

    /**
     * Runs a scheduled task on the calling thread instead of after the delay, so the tests observe the publish
     * synchronously. Only {@code schedule(Runnable, long, TimeUnit)} is used by the service under test.
     */
    private static class SameThreadScheduledExecutor extends ScheduledThreadPoolExecutor {

        SameThreadScheduledExecutor() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            command.run();
            return super.schedule(() -> {
            }, 0, TimeUnit.NANOSECONDS);
        }
    }

}
