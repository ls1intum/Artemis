package de.tum.cit.aet.artemis.core.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserScheduleService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.lecture.api.SlideUnhideScheduleApi;
import de.tum.cit.aet.artemis.notification.service.NotificationScheduleService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseScheduleService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizScheduleService;

/**
 * Pins down the subscribing half of the scheduling messages.
 *
 * <p>
 * Neither kind of mistake here fails loudly. A misspelled topic name means the publisher writes to a name nobody listens
 * on, and a handler wired to the wrong topic means the wrong thing gets scheduled. Either way the exercise, quiz or slide
 * is simply never scheduled correctly and nothing reports an error, so the wiring is asserted explicitly.
 */
class InstanceMessageReceiveServiceTest {

    private static final long ENTITY_ID = 42L;

    /**
     * Topics that must have a subscriber here, listed by exclusion rather than derived wholesale from
     * {@link MessageTopic} because not every enum value is wired to this service:
     * <ul>
     * <li>{@code WEBSOCKET_BROKER_RECONNECT} is handled by {@link WebsocketBrokerReconnectionMessagingService} and does
     * not need reliable delivery.</li>
     * <li>{@code EXAM_RESCHEDULE_DURING_CONDUCTION} and {@code STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION} currently have
     * neither a publisher nor a subscriber anywhere in the codebase. That predates this service's migration to the
     * distributed data provider, so they are excluded here rather than silently asserted as wired.</li>
     * </ul>
     */
    private static final Set<String> EXPECTED_SCHEDULING_TOPICS = Arrays.stream(MessageTopic.values()).filter(topic -> topic != MessageTopic.WEBSOCKET_BROKER_RECONNECT
            && topic != MessageTopic.EXAM_RESCHEDULE_DURING_CONDUCTION && topic != MessageTopic.STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION).map(MessageTopic::toString)
            .collect(Collectors.toSet());

    /**
     * The listener each topic registered, so a message can be delivered to exactly one topic at a time.
     */
    private final Map<String, Consumer<Object>> listenersByTopic = new HashMap<>();

    private DistributedDataProvider distributedDataProvider;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    private ExerciseTestRepository exerciseRepository;

    private AthenaApi athenaApi;

    private UserTestRepository userRepository;

    private UserScheduleService userScheduleService;

    private NotificationScheduleService notificationScheduleService;

    private ParticipantScoreScheduleService participantScoreScheduleService;

    private QuizScheduleService quizScheduleService;

    private SlideUnhideScheduleApi slideUnhideScheduleApi;

    @BeforeEach
    void setUp() {
        distributedDataProvider = mock(DistributedDataProvider.class);
        when(distributedDataProvider.getReliableTopic(anyString())).thenAnswer(invocation -> {
            String topicName = invocation.getArgument(0);
            DistributedTopic<Object> topic = mock(DistributedTopic.class);
            when(topic.addMessageListener(any())).thenAnswer(registration -> {
                listenersByTopic.put(topicName, registration.getArgument(0));
                return UUID.randomUUID();
            });
            return topic;
        });

        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        programmingExerciseScheduleService = mock(ProgrammingExerciseScheduleService.class);
        exerciseRepository = mock(ExerciseTestRepository.class);
        athenaApi = mock(AthenaApi.class);
        userRepository = mock(UserTestRepository.class);
        userScheduleService = mock(UserScheduleService.class);
        notificationScheduleService = mock(NotificationScheduleService.class);
        participantScoreScheduleService = mock(ParticipantScoreScheduleService.class);
        quizScheduleService = mock(QuizScheduleService.class);
        slideUnhideScheduleApi = mock(SlideUnhideScheduleApi.class);

        InstanceMessageReceiveService service = new InstanceMessageReceiveService(programmingExerciseRepository, programmingExerciseScheduleService, exerciseRepository,
                Optional.of(athenaApi), distributedDataProvider, userRepository, userScheduleService, notificationScheduleService, participantScoreScheduleService,
                quizScheduleService, Optional.of(slideUnhideScheduleApi));
        service.init();
    }

    /**
     * Delivers a payload to the listener registered for the given topic, and only that one.
     *
     * @param topic   the topic to deliver on
     * @param payload the message payload
     */
    private void deliver(MessageTopic topic, Object payload) {
        Consumer<Object> listener = listenersByTopic.get(topic.toString());
        assertThat(listener).as("no listener registered for %s", topic).isNotNull();
        listener.accept(payload);
    }

    @Test
    void shouldSubscribeToEverySchedulingTopicExactlyOnce() {
        assertThat(listenersByTopic.keySet()).containsExactlyInAnyOrderElementsOf(EXPECTED_SCHEDULING_TOPICS);
    }

    @Test
    void shouldNotUsePlainTopicsForScheduling() {
        // A plain topic would silently drop a message whenever this node is briefly disconnected.
        verify(distributedDataProvider, never()).getTopic(anyString());
    }

    @Test
    void shouldScheduleAndCancelProgrammingExercise() {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(ENTITY_ID)).thenReturn(programmingExercise);
        when(exerciseRepository.findByIdElseThrow(ENTITY_ID)).thenReturn(new ProgrammingExercise());

        deliver(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE, ENTITY_ID);
        verify(programmingExerciseScheduleService).updateScheduling(programmingExercise);

        deliver(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE_CANCEL, ENTITY_ID);
        verify(programmingExerciseScheduleService).cancelAllScheduledTasks(ENTITY_ID);
        // the same listener also cancels Athena; without this the handler could be dropped here and only the
        // text-exercise topic would still cover it
        verify(athenaApi).cancelScheduledAthena(ENTITY_ID);
    }

    @Test
    void shouldScheduleAndCancelAthenaForTextExercise() {
        Exercise exercise = new ProgrammingExercise();
        when(exerciseRepository.findByIdElseThrow(ENTITY_ID)).thenReturn(exercise);

        deliver(MessageTopic.TEXT_EXERCISE_SCHEDULE, ENTITY_ID);
        verify(athenaApi).scheduleExerciseForAthenaIfRequired(exercise);

        deliver(MessageTopic.TEXT_EXERCISE_SCHEDULE_CANCEL, ENTITY_ID);
        verify(athenaApi).cancelScheduledAthena(ENTITY_ID);
    }

    @Test
    void shouldScheduleAndCancelNonActivatedUserRemoval() {
        User user = new User();
        when(userRepository.findByIdWithAuthoritiesElseThrow(ENTITY_ID)).thenReturn(user);

        deliver(MessageTopic.USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS, ENTITY_ID);
        verify(userScheduleService).scheduleForRemoveNonActivatedUser(user);

        deliver(MessageTopic.USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USERS, ENTITY_ID);
        verify(userScheduleService).cancelScheduleRemoveNonActivatedUser(user);
    }

    @Test
    void shouldScheduleExerciseNotifications() {
        Exercise exercise = new ProgrammingExercise();
        when(exerciseRepository.findByIdElseThrow(ENTITY_ID)).thenReturn(exercise);

        deliver(MessageTopic.EXERCISE_RELEASED_SCHEDULE, ENTITY_ID);
        verify(notificationScheduleService).updateSchedulingForReleasedExercises(exercise);

        deliver(MessageTopic.ASSESSED_EXERCISE_SUBMISSION_SCHEDULE, ENTITY_ID);
        verify(notificationScheduleService).updateSchedulingForAssessedExercisesSubmissions(exercise);
    }

    /**
     * The only topic carrying an array payload, so it is the one a codec change would break first.
     */
    @Test
    void shouldScheduleParticipantScoreFromArrayPayload() {
        deliver(MessageTopic.PARTICIPANT_SCORE_SCHEDULE, new Long[] { 1L, 2L, 3L });

        verify(participantScoreScheduleService).scheduleTask(1L, 2L, 3L);
    }

    @Test
    void shouldScheduleAndCancelQuizStart() {
        deliver(MessageTopic.QUIZ_EXERCISE_START_SCHEDULE, ENTITY_ID);
        verify(quizScheduleService).scheduleQuizStart(ENTITY_ID);

        deliver(MessageTopic.QUIZ_EXERCISE_START_CANCEL, ENTITY_ID);
        verify(quizScheduleService).cancelScheduledQuizStart(ENTITY_ID);
    }

    @Test
    void shouldScheduleAndCancelSlideUnhiding() {
        deliver(MessageTopic.SLIDE_UNHIDE_SCHEDULE, ENTITY_ID);
        verify(slideUnhideScheduleApi).scheduleSlideUnhiding(ENTITY_ID);

        deliver(MessageTopic.SLIDE_UNHIDE_SCHEDULE_CANCEL, ENTITY_ID);
        verify(slideUnhideScheduleApi).cancelScheduledUnhiding(ENTITY_ID);
    }

    /**
     * Guards against a copy-paste mistake in the listener block: a programming-exercise schedule must not be routed to the
     * quiz, slide or user schedulers.
     */
    @Test
    void shouldNotCrossWireHandlers() {
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(ENTITY_ID)).thenReturn(new ProgrammingExercise());
        when(exerciseRepository.findByIdElseThrow(ENTITY_ID)).thenReturn(new ProgrammingExercise());

        deliver(MessageTopic.PROGRAMMING_EXERCISE_SCHEDULE, ENTITY_ID);

        verify(quizScheduleService, never()).scheduleQuizStart(any());
        verify(slideUnhideScheduleApi, never()).scheduleSlideUnhiding(any());
        verify(userScheduleService, never()).scheduleForRemoveNonActivatedUser(any());
        // This topic deliberately also schedules Athena, so that call must stay.
        verify(athenaApi, times(1)).scheduleExerciseForAthenaIfRequired(any());
        verify(exerciseRepository, times(1)).findByIdElseThrow(eq(ENTITY_ID));
    }
}
