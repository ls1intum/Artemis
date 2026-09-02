package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationTitleSuggestionResponseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Suggests the draft title of an exercise that is about to be generated, from the brief the instructor has already written.
 * <p>
 * Artemis requires a programming exercise title to be unique within its course, so a constant draft title lets an instructor generate exactly once per course. This service exists
 * to make that first title both meaningful and free: one short model call names the exercise, the answer is filtered into a title Artemis accepts, and a collision with an existing
 * exercise is resolved with a numeric suffix.
 * <p>
 * It never fails. A missing provider, a slow provider, a provider error, or an answer nothing survives all end at the same deterministic fallback, because a suggestion an
 * instructor is free to overwrite is not worth blocking generation over.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionExerciseTitleSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(HyperionExerciseTitleSuggestionService.class);

    private static final String TITLE_PIPELINE_ID = "HYPERION_EXERCISE_TITLE_SUGGESTION";

    private static final String PROMPT_TEMPLATE = "/prompts/hyperion/suggest_exercise_title.st";

    private static final String SYSTEM_PROMPT = "You name programming exercises. Answer with the title only: no quotation marks, no punctuation, no explanation.";

    /** The English source string of {@code artemisApp.hyperion.generation.brief.draftTitle}, so a suggestion that fails looks like what the dialog used to fill in by itself. */
    static final String FALLBACK_TITLE = "AI draft exercise";

    /**
     * The instructor is waiting on this with a half-typed brief in front of them, so the request is abandoned long before a generation run would give up. Note that this bounds
     * what the instructor waits for, not what the provider does: an abandoned call still finishes on its own thread.
     */
    private static final Duration SUGGESTION_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Small against the 32k a reviewer pass may spend, but not as small as a title looks: on a reasoning model the hidden reasoning is charged to the same budget, so a cap of a
     * few dozen tokens buys an empty answer rather than a fast one.
     */
    private static final int MAX_OUTPUT_TOKENS = 2_048;

    /** Only the opening of a long brief carries what the exercise is about, and a title does not get better from the rest of it. */
    private static final int MAX_BRIEF_LENGTH = 4_000;

    /**
     * Bounds the disambiguation search. Reaching it needs a course that already holds this many exercises named alike, which is not a case worth another fifty queries; the
     * clock-derived suffix below ends it instead.
     */
    private static final int MAX_UNIQUENESS_ATTEMPTS = 50;

    /** Null when no AI provider is configured, which is one of the ways this service falls back rather than fails. */
    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    /**
     * Whether the deployment's model takes {@code max_tokens} rather than {@code max_completion_tokens}, resolved once from the configured model exactly as the agent and the
     * reviewer resolve it.
     */
    private final boolean usesLegacyMaxTokens;

    /**
     * @param chatClient                    the shared chat client, null when no AI provider is configured
     * @param chatModels                    all configured chat models; only the first one's options are read, matching how the shared {@code ChatClient} bean is built
     * @param templateService               loads the prompt template
     * @param llmTokenUsageService          meters what the suggestion costs
     * @param userRepository                resolves the calling user for that metering
     * @param courseRepository              loads the course the title must be unique in
     * @param programmingExerciseRepository answers whether a candidate title is already taken
     */
    public HyperionExerciseTitleSuggestionService(@Nullable ChatClient chatClient, List<ChatModel> chatModels, HyperionPromptTemplateService templateService,
            LLMTokenUsageService llmTokenUsageService, UserRepository userRepository, CourseRepository courseRepository,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        ChatOptions configuredOptions = chatModels.isEmpty() ? null : chatModels.getFirst().getOptions();
        boolean hasCompletionTokenOption = configuredOptions instanceof OpenAiChatOptions openAiOptions && openAiOptions.getMaxCompletionTokens() != null;
        this.usesLegacyMaxTokens = !hasCompletionTokenOption && configuredOptions != null && configuredOptions.getMaxTokens() != null;
    }

    /**
     * Suggests a draft title for an exercise the instructor is about to generate in the given course.
     *
     * @param courseId the course the exercise will be created in
     * @param brief    the instructor's brief
     * @return a title that is valid and not yet taken in the course; never an error, and never blank
     */
    public ExerciseGenerationTitleSuggestionResponseDTO suggestTitle(long courseId, String brief) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        // Resolved on the request thread: the model call runs on a scheduler thread, where the security context this reads from is not available.
        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        String suggestion = HyperionExerciseTitleSanitizer.sanitize(requestSuggestion(courseId, userId, brief));
        return new ExerciseGenerationTitleSuggestionResponseDTO(makeUniqueInCourse(suggestion.isEmpty() ? FALLBACK_TITLE : suggestion, course));
    }

    /** One bounded model call, and the empty string for every way it can fail to produce something. */
    private String requestSuggestion(long courseId, @Nullable Long userId, String brief) {
        if (chatClient == null) {
            log.debug("No AI chat client is configured; falling back to the deterministic draft title for course [{}]", courseId);
            return "";
        }
        String sanitizedBrief = sanitizeInput(brief);
        if (sanitizedBrief.isBlank()) {
            return "";
        }
        if (sanitizedBrief.length() > MAX_BRIEF_LENGTH) {
            sanitizedBrief = sanitizedBrief.substring(0, MAX_BRIEF_LENGTH);
        }
        String boundedBrief = sanitizedBrief;
        String result = Mono.fromCallable(() -> callModel(courseId, userId, boundedBrief)).subscribeOn(Schedulers.boundedElastic()).timeout(SUGGESTION_TIMEOUT)
                .onErrorResume(error -> {
                    log.warn("Could not suggest an exercise title for course [{}]; falling back to the deterministic draft title", courseId, error);
                    return Mono.just("");
                }).block();
        return result == null ? "" : result;
    }

    private String callModel(long courseId, @Nullable Long userId, String brief) {
        String userPrompt = templateService.render(PROMPT_TEMPLATE, Map.of("brief", brief));
        ChatResponse chatResponse = chatClient.prompt().system(SYSTEM_PROMPT).user(userPrompt).options(titleOptions()).call().chatResponse();
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, TITLE_PIPELINE_ID, builder -> builder.withCourse(courseId).withUser(userId));
        return LLMTokenUsageService.extractResponseText(chatResponse);
    }

    /** A fresh builder per call: the chat client consumes a mutable options builder. */
    private OpenAiChatOptions.Builder titleOptions() {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        if (usesLegacyMaxTokens) {
            options.maxTokens(MAX_OUTPUT_TOKENS);
        }
        else {
            options.maxCompletionTokens(MAX_OUTPUT_TOKENS);
        }
        return options;
    }

    /**
     * Resolves a collision the same way {@link ProgrammingExerciseRepository#validateTitle} detects one, so a suggestion this returns cannot be rejected as a duplicate by the
     * create request that follows it.
     */
    private String makeUniqueInCourse(String title, Course course) {
        if (isTitleFree(title, course)) {
            return title;
        }
        for (int suffix = 2; suffix <= MAX_UNIQUENESS_ATTEMPTS; suffix++) {
            String candidate = HyperionExerciseTitleSanitizer.withSuffix(title, suffix);
            if (isTitleFree(candidate, course)) {
                return candidate;
            }
        }
        // Unreachable short of a course built to reach it. A suffix nobody has used yet still beats knowingly returning a title the next request would reject.
        return HyperionExerciseTitleSanitizer.withSuffix(title, System.currentTimeMillis() % 1_000_000L);
    }

    private boolean isTitleFree(String title, Course course) {
        return programmingExerciseRepository.countByTitleAndCourse(title, course) + programmingExerciseRepository.countByTitleAndExerciseGroupExamCourse(title, course) == 0;
    }
}
