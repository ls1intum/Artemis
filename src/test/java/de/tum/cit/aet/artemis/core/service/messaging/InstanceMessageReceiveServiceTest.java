package de.tum.cit.aet.artemis.core.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserScheduleService;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.api.SlideUnhideScheduleApi;
import de.tum.cit.aet.artemis.notification.service.NotificationScheduleService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseScheduleService;
import de.tum.cit.aet.artemis.quiz.service.QuizScheduleService;

/**
 * Pins down the subscribing half of the scheduling messages.
 *
 * <p>
 * A forgotten or misspelled topic name here does not fail loudly: the publisher writes to one name, nobody listens on it,
 * and the exercise or quiz is simply never scheduled. These tests assert the wiring itself, and that it uses
 * <em>reliable</em> topics, because a dropped scheduling message has the same silent effect.
 */
class InstanceMessageReceiveServiceTest {

    /**
     * Topics that must have a subscriber here, listed explicitly rather than derived from {@link MessageTopic} because not
     * every enum value is wired to this service:
     * <ul>
     * <li>{@code WEBSOCKET_BROKER_RECONNECT} is handled by {@link WebsocketBrokerReconnectionMessagingService} and does not
     * need reliable delivery.</li>
     * <li>{@code EXAM_RESCHEDULE_DURING_CONDUCTION} and {@code STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION} currently have
     * neither a publisher nor a subscriber anywhere in the codebase. That predates this service's migration to the
     * distributed data provider, so they are excluded here rather than silently asserted as wired.</li>
     * </ul>
     */
    private static final Set<String> EXPECTED_SCHEDULING_TOPICS = Arrays.stream(MessageTopic.values()).filter(topic -> topic != MessageTopic.WEBSOCKET_BROKER_RECONNECT
            && topic != MessageTopic.EXAM_RESCHEDULE_DURING_CONDUCTION && topic != MessageTopic.STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION).map(MessageTopic::toString)
            .collect(Collectors.toSet());

    private DistributedDataProvider distributedDataProvider;

    private DistributedTopic<Object> topic;

    @BeforeEach
    void setUp() {
        distributedDataProvider = mock(DistributedDataProvider.class);
        topic = mock(DistributedTopic.class);
        when(distributedDataProvider.getReliableTopic(anyString())).thenReturn(topic);

        InstanceMessageReceiveService service = new InstanceMessageReceiveService(mock(ProgrammingExerciseRepository.class), mock(ProgrammingExerciseScheduleService.class),
                mock(ExerciseRepository.class), Optional.of(mock(AthenaApi.class)), distributedDataProvider, mock(UserRepository.class), mock(UserScheduleService.class),
                mock(NotificationScheduleService.class), mock(ParticipantScoreScheduleService.class), mock(QuizScheduleService.class),
                Optional.of(mock(SlideUnhideScheduleApi.class)));
        service.init();
    }

    @Test
    void shouldSubscribeToEverySchedulingTopic() {
        ArgumentCaptor<String> topicNames = ArgumentCaptor.forClass(String.class);
        verify(distributedDataProvider, atLeastOnce()).getReliableTopic(topicNames.capture());

        assertThat(topicNames.getAllValues()).as("every scheduling topic must have a subscriber, otherwise its messages are silently dropped")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_SCHEDULING_TOPICS);
    }

    @Test
    void shouldNotSubscribeToPlainTopicsForScheduling() {
        // A plain topic would silently lose a message whenever this node is briefly disconnected.
        verify(distributedDataProvider, never()).getTopic(anyString());
    }

    @Test
    void shouldRegisterExactlyOneListenerPerSubscribedTopic() {
        ArgumentCaptor<String> topicNames = ArgumentCaptor.forClass(String.class);
        verify(distributedDataProvider, atLeastOnce()).getReliableTopic(topicNames.capture());
        List<String> subscribed = topicNames.getAllValues();

        assertThat(subscribed).as("a topic subscribed twice would process every message twice").doesNotHaveDuplicates();
        verify(topic, times(subscribed.size())).addMessageListener(any());
    }
}
