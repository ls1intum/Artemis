package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.MAX_PROBLEM_STATEMENT_LENGTH;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.getSanitizedCourseDescription;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.getSanitizedCourseTitle;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.sanitizeInput;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.stripLineNumbers;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.stripWrapperMarkers;
import static de.tum.cit.aet.artemis.hyperion.service.HyperionUtils.validateUserPrompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;
import io.micrometer.observation.annotation.Observed;

/**
 * Service for generating initial draft problem statements using Spring AI.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementGenerationService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementGenerationService.class);

    private static final String GENERATION_PIPELINE_ID = "HYPERION_PROBLEM_GENERATION";

    /** The maximum number of topic categories surfaced from a draft (the prompt asks for 1-3; we cap defensively). */
    private static final int MAX_SUGGESTED_CATEGORIES = 3;

    /**
     * Matches one fenced {@code ```json { ... } ```} block carrying a flat JSON object. The draft prompt asks the model to append exactly one such block with the proposed metadata
     * after
     * the markdown; the body of a problem statement may itself contain fenced {@code json} examples, so the extractor keeps only the LAST block whose object has a metadata key.
     */
    private static final Pattern JSON_TRAILER_BLOCK = Pattern.compile("(?s)```json\\s*(\\{.*?})\\s*```");

    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    /**
     * Creates a new HyperionProblemStatementGenerationService.
     *
     *
     * @param chatClient           the AI chat client (optional)
     * @param templateService      prompt template service
     * @param llmTokenUsageService service for tracking LLM token usage
     * @param userRepository       repository for resolving current user
     * @param objectMapper         JSON mapper used to parse the proposed-metadata trailer
     */
    public HyperionProblemStatementGenerationService(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService, LLMTokenUsageService llmTokenUsageService,
            UserRepository userRepository, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a problem statement for an exercise
     *
     * @param course     the course context for the problem statement
     * @param userPrompt the user's requirements and instructions for the problem statement
     * @return the generated problem statement response
     * @throws InternalServerErrorAlertException if generation fails or response is too long
     */
    @Observed(name = "hyperion.generate", contextualName = "problem statement generation", lowCardinalityKeyValues = { "ai.span", "true" })
    public ProblemStatementGenerationResponseDTO generateProblemStatement(Course course, String userPrompt) {
        log.debug("Generating problem statement for course [{}]", course.getId());

        if (chatClient == null) {
            throw new InternalServerErrorAlertException("AI chat client is not configured", "ProblemStatement", "ProblemStatementGeneration.chatClientNotConfigured");
        }

        String sanitizedPrompt = sanitizeInput(userPrompt);
        validateUserPrompt(sanitizedPrompt, "ProblemStatementGeneration");

        String systemPrompt = templateService.render("/prompts/hyperion/generate_draft_problem_statement_system.st", Map.of());

        Map<String, String> userVariables = Map.of("userPrompt", sanitizedPrompt, "courseTitle", getSanitizedCourseTitle(course), "courseDescription",
                getSanitizedCourseDescription(course));
        String userMessage = templateService.render("/prompts/hyperion/generate_draft_problem_statement_user.st", userVariables);

        ChatResponse chatResponse;
        String generatedProblemStatement;
        try {
            chatResponse = chatClient.prompt().system(systemPrompt).user(userMessage).call().chatResponse();
            generatedProblemStatement = LLMTokenUsageService.extractResponseText(chatResponse);
        }
        catch (Exception e) {
            log.error("Error generating problem statement for course [{}]: {}", course.getId(), e.getMessage(), e);
            throw new InternalServerErrorAlertException("Failed to generate problem statement", "ProblemStatement", "ProblemStatementGeneration.problemStatementGenerationFailed");
        }
        Long userId = HyperionUtils.resolveCurrentUserId(userRepository);
        llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                builder -> builder.withCourse(course.getId()).withUser(userId));

        // Defensively strip artifacts the LLM may have copied from the prompt template
        if (generatedProblemStatement != null) {
            generatedProblemStatement = stripLineNumbers(generatedProblemStatement);
            generatedProblemStatement = stripWrapperMarkers(generatedProblemStatement);
            generatedProblemStatement = generatedProblemStatement.trim();
        }

        // Pull the proposed-metadata trailer (difficulty/categories/title) out of the markdown and remove it, so the editor never shows the JSON block. Fail-open: a missing or
        // malformed
        // trailer yields no suggestions and leaves the statement untouched. Done BEFORE the blank/length validation so the checks see the student-facing statement, not the rider.
        ParsedSuggestions suggestions = generatedProblemStatement == null ? ParsedSuggestions.none(null) : extractSuggestions(generatedProblemStatement);
        generatedProblemStatement = suggestions.cleanedStatement();

        boolean isEmptyResponse = generatedProblemStatement == null || generatedProblemStatement.isBlank();
        if (isEmptyResponse) {
            throw new InternalServerErrorAlertException("Generated problem statement is null or empty", "ProblemStatement",
                    "ProblemStatementGeneration.problemStatementGenerationNull");
        }

        // Validate response length
        boolean exceedsMaxLength = generatedProblemStatement.length() > MAX_PROBLEM_STATEMENT_LENGTH;
        if (exceedsMaxLength) {
            throw new InternalServerErrorAlertException("Generated problem statement exceeds the maximum allowed length", "ProblemStatement",
                    "ProblemStatementGeneration.generatedProblemStatementTooLong");
        }

        return new ProblemStatementGenerationResponseDTO(generatedProblemStatement, suggestions.title(), suggestions.difficulty(), suggestions.categories());
    }

    /**
     * Extracts the proposed-metadata trailer from a drafted problem statement and returns the statement with the trailer removed plus the parsed suggestions. Robust by
     * construction: it
     * scans every fenced {@code json} block and keeps the LAST one that actually carries a metadata key (so a {@code json} example inside the statement body is ignored), parses it
     * leniently, and fails open — any parse problem, or a strip that would empty the statement, returns the original statement with no suggestions.
     *
     * @param statement the cleaned problem statement (never {@code null})
     * @return the statement without the trailer and the parsed (possibly all-{@code null}) suggestions
     */
    private ParsedSuggestions extractSuggestions(String statement) {
        try {
            Matcher matcher = JSON_TRAILER_BLOCK.matcher(statement);
            JsonNode chosen = null;
            int start = -1;
            int end = -1;
            while (matcher.find()) {
                try {
                    JsonNode node = objectMapper.readTree(matcher.group(1));
                    if (node != null && (node.has("difficulty") || node.has("categories") || node.has("title"))) {
                        chosen = node;
                        start = matcher.start();
                        end = matcher.end();
                    }
                }
                catch (Exception ignored) {
                    // not a parseable metadata block; keep scanning
                }
            }
            if (chosen == null) {
                return ParsedSuggestions.none(statement);
            }
            String cleaned = (statement.substring(0, start) + statement.substring(end)).strip();
            if (cleaned.isBlank()) {
                // The model emitted (almost) only the JSON — keep the original statement rather than returning an empty one that would then be rejected as blank.
                return ParsedSuggestions.none(statement);
            }
            return new ParsedSuggestions(cleaned, textOrNull(chosen.get("title")), parseDifficulty(textOrNull(chosen.get("difficulty"))),
                    parseCategories(chosen.get("categories")));
        }
        catch (Exception e) {
            log.debug("Could not parse the proposed-metadata trailer; continuing without suggestions: {}", e.getMessage());
            return ParsedSuggestions.none(statement);
        }
    }

    @Nullable
    private static String textOrNull(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText().strip();
        return text.isBlank() ? null : text;
    }

    @Nullable
    private static DifficultyLevel parseDifficulty(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return DifficultyLevel.valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static List<String> parseCategories(@Nullable JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> categories = new ArrayList<>();
        for (JsonNode item : node) {
            String category = textOrNull(item);
            if (category != null && !categories.contains(category)) {
                categories.add(category);
            }
            if (categories.size() >= MAX_SUGGESTED_CATEGORIES) {
                break;
            }
        }
        return categories.isEmpty() ? null : categories;
    }

    /**
     * The statement with the metadata trailer removed plus the parsed suggestions (any of which may be {@code null}).
     */
    private record ParsedSuggestions(@Nullable String cleanedStatement, @Nullable String title, @Nullable DifficultyLevel difficulty, @Nullable List<String> categories) {

        private static ParsedSuggestions none(@Nullable String statement) {
            return new ParsedSuggestions(statement, null, null, null);
        }
    }

}
