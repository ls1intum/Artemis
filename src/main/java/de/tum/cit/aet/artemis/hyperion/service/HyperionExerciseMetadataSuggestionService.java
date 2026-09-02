package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

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
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationMetadataSuggestionResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Derives everything an exercise needs to exist, from the brief the instructor has already written.
 * <p>
 * The instructor states what students should build; Artemis works out the rest. One short model call names the exercise and reads the difficulty the brief implies, and the short
 * name, the package name and the points follow deterministically from that name — asking an instructor to invent a repository slug adds a decision without adding a choice. Every
 * value returned is one Artemis will accept: the title and short name are checked for collisions the same way the create request will check them, and the package name against the
 * very pattern that will validate it.
 * <p>
 * It never fails. A missing provider, a slow provider, a provider error, or an answer nothing survives all end at the same deterministic suggestion built from the brief's own
 * first words, because a suggestion an instructor is free to overwrite is not worth blocking generation over.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionExerciseMetadataSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(HyperionExerciseMetadataSuggestionService.class);

    private static final String METADATA_PIPELINE_ID = "HYPERION_EXERCISE_METADATA_SUGGESTION";

    private static final String PROMPT_TEMPLATE = "/prompts/hyperion/suggest_exercise_metadata.st";

    private static final String SYSTEM_PROMPT = "You name programming exercises and judge how hard they are. Answer with one JSON object and nothing else.";

    /** The English source string of {@code artemisApp.hyperion.generation.brief.draftTitle}, for a brief whose own words yield nothing usable. */
    static final String FALLBACK_TITLE = "AI draft exercise";

    /** The difficulty the dialog defaulted to before it was suggested, so an unreadable answer changes nothing rather than guessing. */
    static final DifficultyLevel DEFAULT_DIFFICULTY = DifficultyLevel.MEDIUM;

    /** Points are not a model's to guess and not worth a question: ten is the draft's starting value and an ordinary exercise edit afterwards. */
    static final double DRAFT_MAX_POINTS = 10;

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

    /** Only the opening of a long brief carries what the exercise is about, and neither a title nor a difficulty gets better from the rest of it. */
    private static final int MAX_BRIEF_LENGTH = 4_000;

    /**
     * Bounds the disambiguation search. Reaching it needs an instance that already holds this many exercises named alike, which is not a case worth another fifty queries; the
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
     * @param courseRepository              loads the course the title and short name must be free in
     * @param programmingExerciseRepository answers whether a candidate title, short name, or project key is already taken
     */
    public HyperionExerciseMetadataSuggestionService(@Nullable ChatClient chatClient, List<ChatModel> chatModels, HyperionPromptTemplateService templateService,
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
     * Derives the metadata of the exercise a brief describes, in the course it will be created in.
     *
     * @param courseId    the course the exercise will be created in
     * @param brief       the instructor's brief
     * @param projectType the project type the exercise will use, which shapes the package name; may be null
     * @return a complete, valid, collision-free suggestion; never an error, and never with a blank field
     */
    public ExerciseGenerationMetadataSuggestionResponseDTO suggestMetadata(long courseId, String brief, @Nullable ProjectType projectType) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        // Resolved on the request thread: the model call runs on a scheduler thread, where the security context this reads from is not available.
        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        String sanitizedBrief = boundedBrief(brief);
        HyperionExerciseMetadataParser.ModelAnswer answer = HyperionExerciseMetadataParser.parse(requestSuggestion(courseId, userId, sanitizedBrief));

        String title = uniqueTitle(HyperionExerciseTitleSanitizer.sanitize(answer.title()), sanitizedBrief, course);
        String shortName = uniqueShortName(HyperionExerciseIdentifierDeriver.deriveShortName(title), course);
        String packageName = HyperionExerciseIdentifierDeriver.derivePackageName(shortName, projectType);
        return new ExerciseGenerationMetadataSuggestionResponseDTO(title, shortName, packageName, difficulty(answer, sanitizedBrief), DRAFT_MAX_POINTS);
    }

    /**
     * The model's reading of the brief first, the brief's own statement of difficulty second, and only then the default: a brief that ends "Difficulty: easy" has answered the
     * question itself, and honouring that keeps the suggestion right even with no provider configured at all.
     */
    private DifficultyLevel difficulty(HyperionExerciseMetadataParser.ModelAnswer answer, String brief) {
        if (answer.difficulty() != null) {
            return answer.difficulty();
        }
        DifficultyLevel statedInBrief = HyperionExerciseMetadataParser.difficultyStatedInBrief(brief);
        return statedInBrief != null ? statedInBrief : DEFAULT_DIFFICULTY;
    }

    /** The model's title if it survived sanitisation, the brief's own opening words if it did not, and the constant only for a brief that says nothing either. */
    private String uniqueTitle(String sanitizedTitle, String brief, Course course) {
        String title = sanitizedTitle;
        if (title.isEmpty()) {
            title = HyperionExerciseTitleSanitizer.fromBriefOpening(brief);
        }
        if (title.isEmpty()) {
            title = FALLBACK_TITLE;
        }
        return makeUnique(title, candidate -> isTitleFree(candidate, course), HyperionExerciseTitleSanitizer::withSuffix);
    }

    private String uniqueShortName(String shortName, Course course) {
        return makeUnique(shortName, candidate -> isShortNameFree(candidate, course), HyperionExerciseIdentifierDeriver::withSuffix);
    }

    /** One bounded model call, and the empty string for every way it can fail to produce something. */
    private String requestSuggestion(long courseId, @Nullable Long userId, String brief) {
        if (chatClient == null) {
            log.debug("No AI chat client is configured; deriving the generation metadata for course [{}] from the brief alone", courseId);
            return "";
        }
        if (brief.isBlank()) {
            return "";
        }
        String result = Mono.fromCallable(() -> callModel(courseId, userId, brief)).subscribeOn(Schedulers.boundedElastic()).timeout(SUGGESTION_TIMEOUT).onErrorResume(error -> {
            log.warn("Could not suggest exercise generation metadata for course [{}]; deriving it from the brief alone", courseId, error);
            return Mono.just("");
        }).block();
        return result == null ? "" : result;
    }

    private String callModel(long courseId, @Nullable Long userId, String brief) {
        String userPrompt = templateService.render(PROMPT_TEMPLATE, Map.of("brief", brief));
        ChatResponse chatResponse = chatClient.prompt().system(SYSTEM_PROMPT).user(userPrompt).options(metadataOptions()).call().chatResponse();
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, METADATA_PIPELINE_ID, builder -> builder.withCourse(courseId).withUser(userId));
        return LLMTokenUsageService.extractResponseText(chatResponse);
    }

    /** A fresh builder per call: the chat client consumes a mutable options builder. */
    private OpenAiChatOptions.Builder metadataOptions() {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        if (usesLegacyMaxTokens) {
            options.maxTokens(MAX_OUTPUT_TOKENS);
        }
        else {
            options.maxCompletionTokens(MAX_OUTPUT_TOKENS);
        }
        return options;
    }

    private String boundedBrief(String brief) {
        String sanitizedBrief = sanitizeInput(brief);
        return sanitizedBrief.length() > MAX_BRIEF_LENGTH ? sanitizedBrief.substring(0, MAX_BRIEF_LENGTH) : sanitizedBrief;
    }

    /**
     * Resolves a collision by counting up, exactly as the create request that follows will detect one, so nothing this returns can be rejected as a duplicate.
     *
     * @param candidate  the preferred value
     * @param isFree     whether a value is still available
     * @param withSuffix appends a disambiguating number while keeping the value's own validity rules
     */
    private static String makeUnique(String candidate, Predicate<String> isFree, BiFunction<String, Long, String> withSuffix) {
        if (isFree.test(candidate)) {
            return candidate;
        }
        for (long suffix = 2; suffix <= MAX_UNIQUENESS_ATTEMPTS; suffix++) {
            String suffixed = withSuffix.apply(candidate, suffix);
            if (isFree.test(suffixed)) {
                return suffixed;
            }
        }
        // Unreachable short of an instance built to reach it. A suffix nobody has used yet still beats knowingly returning a value the next request would reject.
        return withSuffix.apply(candidate, System.currentTimeMillis() % 1_000_000L);
    }

    private boolean isTitleFree(String title, Course course) {
        return programmingExerciseRepository.countByTitleAndCourse(title, course) + programmingExerciseRepository.countByTitleAndExerciseGroupExamCourse(title, course) == 0;
    }

    /**
     * Free means free everywhere, not just in this course. Artemis validates the short name against the course, but the project key it is folded into — {@code COURSEEXERCISE},
     * upper-cased — names the exercise's repositories and build plans instance-wide, so a short name that is free here and taken over there is a short name the setup request
     * would still reject.
     */
    private boolean isShortNameFree(String shortName, Course course) {
        boolean freeInCourse = programmingExerciseRepository.countByShortNameAndCourse(shortName, course)
                + programmingExerciseRepository.countByShortNameAndExerciseGroupExamCourse(shortName, course) == 0;
        return freeInCourse && isProjectKeyFree(shortName, course);
    }

    /** A course with no short name of its own cannot form a project key; the setup request rejects that course before it ever reaches the exercise's short name. */
    private boolean isProjectKeyFree(String shortName, Course course) {
        String courseShortName = course.getShortName();
        if (courseShortName == null || courseShortName.isBlank()) {
            return true;
        }
        String projectKey = (courseShortName + shortName).toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return programmingExerciseRepository.countByProjectKey(projectKey) == 0;
    }
}
