package de.tum.cit.aet.artemis.core.security.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

class WebsocketTopicTest {

    @Test
    void testTemplateValidation() {
        for (String invalid : List.of("/queue/x", "topic/x", "/topic/x/", "/topic//x", "/topic/x{id}", "/topic/{id}/{id}", "/topic/a b", "/topic/*")) {
            assertThatThrownBy(() -> WebsocketTopic.of(invalid, WebsocketTopicAccess.anyAuthenticatedUser())).as(invalid).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> WebsocketTopic.of("/topic/courses/{id}", WebsocketTopicAccess.atLeastStudentInCourse("courseId"))).as("rule reads an undeclared variable")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebsocketTopic.of("/topic/courses/{courseId}", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDestinationsAreExactMatches() {
        var topic = WebsocketTopic.of("/topic/courses/{courseId}/quizExercises/{quizBatchId}", WebsocketTopicAccess.atLeastStudentInCourse("courseId"));
        var destination = topic.at(5L, 7L);
        assertThat(destination.value()).isEqualTo("/topic/courses/5/quizExercises/7");
        assertThat(topic.match("/topic/courses/5/quizExercises/7")).contains(Map.of("courseId", "5", "quizBatchId", "7"));
        for (String other : List.of("/topic/courses/5/quizExercises", "/topic/courses/5/quizExercises/7/more", "/topic/courses//quizExercises/7",
                "/topic/courses/5/quizexercises/7", "topic/courses/5/quizExercises/7")) {
            assertThat(topic.match(other)).as(other).isEmpty();
        }
        assertThatThrownBy(() -> topic.at(5L)).as("missing value").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> topic.at(5L, "a/b")).as("value with a separator").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebsocketDestination(topic, "/topic/courses/5/other/7")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIdsMustBeCanonical() {
        var subscription = new WebsocketSubscription(new UsernamePasswordAuthenticationToken("user", ""), "/topic/x",
                Map.of("zero", "0", "id", "42", "leadingZero", "042", "signed", "+42", "big", "99999999999999999999", "text", "abc"), () -> false);
        assertThat(subscription.id("zero")).isZero();
        assertThat(subscription.id("id")).isEqualTo(42L);
        for (String variable : List.of("leadingZero", "signed", "big", "text", "missing")) {
            assertThatThrownBy(() -> subscription.id(variable)).as(variable).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void testRegistryRejectsOverlappingTopics() {
        assertThatThrownBy(() -> registryOf(new OverlappingTopics())).isInstanceOf(IllegalStateException.class).hasMessageContaining("equally well");
        // the variables sit at different segments, so neither sample destination reaches the other topic, but /topic/things/items/details matches both
        assertThatThrownBy(() -> registryOf(new CrossingTopics())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both match /topic/things/items/details equally well");
        assertThatThrownBy(() -> registryOf(new DuplicateTopics())).isInstanceOf(IllegalStateException.class).hasMessageContaining("declared twice");
        assertThatThrownBy(() -> registryOf(new NonConstantTopic())).isInstanceOf(IllegalStateException.class).hasMessageContaining("public static final");
    }

    @Test
    void testUserTopicsMayShareDestinations() {
        var registry = registryOf(new CrossingUserTopics());
        var user = new UsernamePasswordAuthenticationToken("user", "");
        // user topics have no access rule, so it does not matter which of them a destination belongs to
        for (String destination : List.of("/user/topic/things/competencies/commands", "/user/topic/things/42/commands", "/user/topic/things/competencies/42")) {
            assertThat(registry.authorizeSubscription(user, destination)).as(destination).isEqualTo(WebsocketTopicRegistry.Decision.ALLOWED);
        }
        assertThat(registry.authorizeSubscription(user, "/user/topic/things/42/other")).isEqualTo(WebsocketTopicRegistry.Decision.UNDECLARED);
    }

    @Test
    void testMoreSpecificTopicWins() {
        var registry = registryOf(new LiteralAndVariableTopics());
        var user = new UsernamePasswordAuthenticationToken("user", "");
        assertThat(registry.authorizeSubscription(user, "/topic/things/special")).isEqualTo(WebsocketTopicRegistry.Decision.ALLOWED);
        assertThat(registry.authorizeSubscription(user, "/topic/things/42")).isEqualTo(WebsocketTopicRegistry.Decision.DENIED);
    }

    @Test
    void testAdministratorTopicsNeedElevation() {
        var elevatedAccessService = mock(ElevatedAccessService.class);
        var registry = new WebsocketTopicRegistry(List.of(new AdministratorTopics()), mock(UserRepository.class), mock(ExerciseRepository.class), elevatedAccessService);
        var elevated = new UsernamePasswordAuthenticationToken("admin", "");
        var notElevated = new UsernamePasswordAuthenticationToken("admin2", "");
        when(elevatedAccessService.isAdminElevationActive(elevated)).thenReturn(true);
        when(elevatedAccessService.isAdminElevationActive(notElevated)).thenReturn(false);
        assertThat(registry.authorizeSubscription(elevated, "/topic/admin/things")).isEqualTo(WebsocketTopicRegistry.Decision.ALLOWED);
        assertThat(registry.authorizeSubscription(notElevated, "/topic/admin/things")).isEqualTo(WebsocketTopicRegistry.Decision.DENIED);
    }

    static class AdministratorTopics implements WebsocketTopicProvider {

        public static final WebsocketTopic THINGS = WebsocketTopic.of("/topic/admin/things", WebsocketTopicAccess.administrator());
    }

    private static WebsocketTopicRegistry registryOf(WebsocketTopicProvider provider) {
        return new WebsocketTopicRegistry(List.of(provider), mock(UserRepository.class), mock(ExerciseRepository.class), mock(ElevatedAccessService.class));
    }

    static class OverlappingTopics implements WebsocketTopicProvider {

        public static final WebsocketTopic BY_COURSE = WebsocketTopic.of("/topic/things/{courseId}/items", WebsocketTopicAccess.atLeastStudentInCourse("courseId"));

        public static final WebsocketTopic BY_EXERCISE = WebsocketTopic.of("/topic/things/{exerciseId}/items", WebsocketTopicAccess.atLeastTutorInExercise("exerciseId"));
    }

    static class CrossingTopics implements WebsocketTopicProvider {

        public static final WebsocketTopic ITEM_DETAILS = WebsocketTopic.of("/topic/things/{courseId}/details", WebsocketTopicAccess.atLeastStudentInCourse("courseId"));

        public static final WebsocketTopic ITEMS = WebsocketTopic.of("/topic/things/items/{exerciseId}", WebsocketTopicAccess.atLeastTutorInExercise("exerciseId"));
    }

    static class CrossingUserTopics implements WebsocketTopicProvider {

        public static final WebsocketUserTopic SESSION_COMMANDS = WebsocketUserTopic.of("/topic/things/{sessionId}/commands");

        public static final WebsocketUserTopic COMPETENCIES = WebsocketUserTopic.of("/topic/things/competencies/{courseId}");
    }

    static class DuplicateTopics implements WebsocketTopicProvider {

        public static final WebsocketTopic FIRST = WebsocketTopic.of("/topic/things", WebsocketTopicAccess.anyAuthenticatedUser());

        public static final WebsocketTopic SECOND = WebsocketTopic.of("/topic/things", WebsocketTopicAccess.administrator());
    }

    static class NonConstantTopic implements WebsocketTopicProvider {

        public WebsocketTopic things = WebsocketTopic.of("/topic/things", WebsocketTopicAccess.anyAuthenticatedUser());
    }

    static class LiteralAndVariableTopics implements WebsocketTopicProvider {

        public static final WebsocketTopic SPECIAL = WebsocketTopic.of("/topic/things/special", WebsocketTopicAccess.anyAuthenticatedUser());

        public static final WebsocketTopic ANY = WebsocketTopic.of("/topic/things/{thingId}",
                WebsocketTopicAccess.custom(LiteralAndVariableTopics.class, (provider, subscription) -> false));
    }
}
