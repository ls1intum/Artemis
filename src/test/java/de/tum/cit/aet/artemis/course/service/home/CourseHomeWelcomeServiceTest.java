package de.tum.cit.aet.artemis.course.service.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.CourseHomeQuestionState;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeQuestionsDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.course.domain.CourseHomeTargetKind;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeSource;
import de.tum.cit.aet.artemis.course.dto.CourseAvailableTabsDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeGeneratedWelcomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTeachingTeamDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeWelcomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeWelcomeRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseHomeSessionsDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CourseHomeWelcomeServiceTest {

    private static final String FP = "a".repeat(64);

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private static final String GOOD = "Try one manageable step, then reflect on what you learn from it.";

    private final CourseHomeService home = mock(CourseHomeService.class);

    private final CourseHomeAiPolicyService policy = mock(CourseHomeAiPolicyService.class);

    private final CourseHomeAiClient client = mock(CourseHomeAiClient.class);

    private final User user = new User();

    private CourseHomeAiPromptBuilderService prompts;

    private CourseHomeWelcomeService service;

    @BeforeEach
    void setup() throws Exception {
        user.setId(7L);
        user.setLogin("private-user");
        user.setFirstName("PRIVATE_FIRST");
        user.setEmail("private@example.org");
        prompts = new CourseHomeAiPromptBuilderService(JsonObjectMapper.get(), new ClassPathResource("prompts/course/home-welcome-system.st"));
        when(policy.canGenerate(7)).thenReturn(true);
        when(home.load(anyLong(), any(), any(), any())).thenReturn(fixture(false));
        when(client.generate(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(GOOD)));
        service = new CourseHomeWelcomeService(home, policy, prompts, Optional.of(client), new SimpleMeterRegistry());
    }

    @Test
    void validWordingIsPlainTextAndKeepsTheContextFingerprint() {
        var result = generate().join();
        assertThat(result.source()).isEqualTo(CourseHomeWelcomeSource.AI);
        assertThat(result.message()).isEqualTo(GOOD);
        assertThat(result.contextFingerprint()).isEqualTo(FP);
    }

    @Test
    void declinedOrUnavailableAiNeverCallsTheProvider() {
        when(policy.canGenerate(7)).thenReturn(false);
        assertFallback(generate().join());
        verify(client, never()).generate(any());
    }

    @Test
    void missingProviderReturnsTheSameUsableFallback() {
        service = new CourseHomeWelcomeService(home, policy, prompts, Optional.empty(), new SimpleMeterRegistry());
        assertFallback(generate().join());
        verify(client, never()).generate(any());
    }

    @Test
    void changedAuthorizedContextIsNotSentToTheProvider() {
        assertFallback(service.generate(42, user, new CourseHomeWelcomeRequestDTO("b".repeat(64), "en"), NOW).join());
        verify(client, never()).generate(any());
    }

    @Test
    void revocationImmediatelyBeforeDispatchPreventsModelWork() {
        when(policy.canGenerate(7)).thenReturn(true, false);
        assertFallback(generate().join());
        verify(client, never()).generate(any());
    }

    @Test
    void revocationWhileTheProviderIsRunningDiscardsItsWording() {
        var pending = new CompletableFuture<Optional<String>>();
        when(client.generate(any())).thenReturn(pending);
        var result = generate();
        when(policy.canGenerate(7)).thenReturn(false);
        pending.complete(Optional.of(GOOD));
        assertFallback(result.join());
    }

    @Test
    void changedMembershipStillFailsAuthorization() {
        when(home.load(anyLong(), any(), any(), any())).thenThrow(new AccessForbiddenException("course", 42L));
        assertThatThrownBy(this::generate).isInstanceOf(AccessForbiddenException.class);
        verify(client, never()).generate(any());
    }

    @Test
    void providerFailureIsAFallback() {
        when(client.generate(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
        assertFallback(generate().join());
    }

    @Test
    void emptyOrOverlongOutputIsAFallback() {
        when(client.generate(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        assertFallback(generate().join());
        when(client.generate(any())).thenReturn(CompletableFuture.completedFuture(Optional.of("x".repeat(501))));
        assertFallback(generate().join());
    }

    @ParameterizedTest
    @ValueSource(strings = { " ", "<b>Keep learning</b>", "[Read this](https://example.org)", "Visit example.org for advice.", "Tomorrow is your next deadline.",
            "Heute musst du die Aufgabe abgeben.", "You have 42 exercises to finish.", "{\"message\":\"Keep learning\"}", "```Keep learning```" })
    void malformedLinksAndNewTimeOrCountClaimsAreRejected(String output) {
        when(client.generate(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(output)));
        assertFallback(generate().join());
    }

    @Test
    void thePromptContainsOnlyBoundedPublishedContext() {
        generate().join();
        var input = ArgumentCaptor.forClass(CourseHomeAiClient.Input.class);
        verify(client).generate(input.capture());
        String all = input.getValue().userPrompt();
        assertThat(all).contains("Published course", "Learn by trying", "Public exercise").doesNotContain("private-user", "PRIVATE_FIRST", "private@example.org",
                "private question", "private session", "PRIVATE_TRANSCRIPT", "PRIVATE_SOURCE");
        assertThat(input.getValue().systemPrompt().length() + all.length()).isLessThanOrEqualTo(8192);
    }

    @Test
    void aPrivatePrimaryTargetIsReplacedWithGenericGuidance() {
        when(home.load(anyLong(), any(), any(), any())).thenReturn(fixture(true));
        generate().join();
        var input = ArgumentCaptor.forClass(CourseHomeAiClient.Input.class);
        verify(client).generate(input.capture());
        assertThat(input.getValue().userPrompt()).doesNotContain("private question", "private session", "19", "20");
    }

    @ParameterizedTest
    @ValueSource(strings = { "The next deadline is at noon.", "Come back in two hours." })
    void spelledOutTimeClaimsAreRejected(String output) {
        when(client.generate(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(output)));
        assertFallback(generate().join());
    }

    @Test
    void controlCharactersAreRejected() {
        when(client.generate(any())).thenReturn(CompletableFuture.completedFuture(Optional.of("Keep exploring." + (char) 0)));
        assertFallback(generate().join());
    }

    @Test
    void jsonEscapingCannotExceedThePromptBudget() {
        var input = prompts.build(fixture(false, String.valueOf((char) 1).repeat(4000), String.valueOf((char) 1).repeat(2000)), "de");
        assertThat(input.systemPrompt().length() + input.userPrompt().length()).isLessThanOrEqualTo(8192);
        assertThat(input.userPrompt()).contains("German");
    }

    private CompletableFuture<CourseHomeGeneratedWelcomeDTO> generate() {
        return service.generate(42, user, new CourseHomeWelcomeRequestDTO(FP, "en"), NOW);
    }

    private void assertFallback(CourseHomeGeneratedWelcomeDTO result) {
        assertThat(result.source()).isEqualTo(CourseHomeWelcomeSource.FALLBACK);
        assertThat(result.message()).isNull();
    }

    private CourseHomeDTO fixture(boolean privateTarget) {
        return fixture(privateTarget, "Published description", "Learn by trying");
    }

    private CourseHomeDTO fixture(boolean privateTarget, String description, String learningApproach) {
        var primary = new CourseHomeDTO.ItemDTO(privateTarget ? "private question" : "Public exercise",
                new CourseHomeTargetDTO(privateTarget ? CourseHomeTargetKind.THREAD : CourseHomeTargetKind.EXERCISE, 19, privateTarget ? 5L : null, privateTarget ? 20L : null),
                NOW, null, privateTarget ? "UNREAD_REPLY" : "NEW_EXERCISE");
        var question = new CourseHomeQuestionsDTO.ItemDTO(19, 5, "private question", 1, CourseHomeQuestionState.ANSWERED, 1L, NOW, 20L);
        var chat = new IrisCourseHomeSessionsDTO.ItemDTO(21, "private session", "COURSE_CHAT", 42, null, NOW);
        return new CourseHomeDTO(NOW, new CourseHomeDTO.VisitDTO(true, null, NOW, 0, NOW.minusSeconds(86400)),
                new CourseHomeDTO.CourseInfoDTO(42, "Published course", description, "en", null, null, learningApproach),
                new CourseAvailableTabsDTO(true, false, false, false, true, true, false, true, false), new CourseHomeDTO.ProgressDTO(3, 1, 0, 0),
                new CourseHomeWelcomeDTO(privateTarget ? CourseHomeWelcomeCase.UNREAD_REPLY : CourseHomeWelcomeCase.NEW_EXERCISE, 0, Map.of("courseTitle", "Published course"), FP,
                        true),
                primary, List.of(), null, List.of(), new CourseHomeTeachingTeamDTO(List.of(), false, 0),
                new CourseHomeQuestionsDTO(CourseHomeSectionStatus.AVAILABLE, List.of(question)), new IrisCourseHomeSessionsDTO(CourseHomeSectionStatus.AVAILABLE, List.of(chat)),
                Map.of("lectures", CourseHomeSectionStatus.AVAILABLE, "exams", CourseHomeSectionStatus.DISABLED, "faq", CourseHomeSectionStatus.AVAILABLE), NOW.plusSeconds(3600));
    }
}
