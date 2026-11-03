package de.tum.cit.aet.artemis.hyperion.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscriptionInterceptor;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.ExerciseGenerationStateChangedEvent;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/** Exercises the real topic registry, subscription interceptor and Spring event listener in the existing generation context. */
class HyperionSharedGenerationStateWebsocketTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hypsharedstate";

    private static final String OTHER_PREFIX = "hypsharedstateother";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private WebsocketTopicRegistry topicRegistry;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private long exerciseId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(OTHER_PREFIX, 0, 0, 0, 1);
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        userUtilService.addStudentToCourse(TEST_PREFIX + "student1", course);
        userUtilService.addTeachingAssistantToCourse(TEST_PREFIX + "tutor1", course);
        userUtilService.addEditorToCourse(TEST_PREFIX + "editor1", course);
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor1", course);
        exerciseId = ((ProgrammingExercise) course.getExercises().iterator().next()).getId();
    }

    @ParameterizedTest
    @CsvSource({ TEST_PREFIX + "editor1,EDITOR,true", TEST_PREFIX + "instructor1,INSTRUCTOR,true", TEST_PREFIX + "student1,STUDENT,false",
            TEST_PREFIX + "tutor1,TEACHING_ASSISTANT,false", OTHER_PREFIX + "instructor1,INSTRUCTOR,false" })
    void subscriptionsRequireEditorAccessToTheExercise(String login, Role role, boolean allowed) {
        String destination = HyperionWebsocketTopics.EXERCISE_GENERATION_STATE.at(exerciseId).value();
        var authentication = new UsernamePasswordAuthenticationToken(login, "unused", List.of(new SimpleGrantedAuthority(role.getAuthority())));
        assertThat(topicRegistry.authorizeSubscription(authentication, destination))
                .isEqualTo(allowed ? WebsocketTopicRegistry.Decision.ALLOWED : WebsocketTopicRegistry.Decision.DENIED);
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);
        headers.setUser(authentication);
        var frame = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        var result = new WebsocketSubscriptionInterceptor(() -> topicRegistry).preSend(frame, Mockito.mock(MessageChannel.class));
        if (allowed) {
            assertThat(result).isSameAs(frame);
        }
        else {
            assertThat(result).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void springListenerBroadcastsStartAndFinish(boolean running) {
        var state = new ExerciseGenerationStateDTO(exerciseId, "shared-lock-" + exerciseId, running);
        var destination = HyperionWebsocketTopics.EXERCISE_GENERATION_STATE.at(exerciseId);
        Mockito.doReturn(CompletableFuture.completedFuture(null)).when(websocketMessagingService).sendMessage(destination, state);

        eventPublisher.publishEvent(new ExerciseGenerationStateChangedEvent(state));

        Mockito.verify(websocketMessagingService, Mockito.timeout(5_000)).sendMessage(destination, state);
    }
}
