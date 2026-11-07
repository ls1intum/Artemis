package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ReviewGuardrails.requireReviewTextSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderFailureClass;

/** Extracts brief-owned scope before a reviewer can select goals to fit the candidate it sees. */
final class BriefLearningGoalExtractor {

    private static final Logger log = LoggerFactory.getLogger(BriefLearningGoalExtractor.class);

    private static final String SYSTEM_PROMPT = "/prompts/hyperion/critic/brief_learning_goals_system.st";

    private static final int MAX_OUTPUT_TOKENS = 4_096;

    private static final int MAX_GOALS = 32;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private record GoalRequest(String brief) {
    }

    private record GoalResponse(@Nullable List<GoalItem> goals) {
    }

    private record GoalItem(@Nullable String quote) {
    }

    private final ReviewerClient reviewer;

    private final JsonMapper objectMapper;

    BriefLearningGoalExtractor(ReviewerClient reviewer, JsonMapper objectMapper) {
        this.reviewer = reviewer;
        this.objectMapper = objectMapper;
    }

    @Nullable
    BriefLearningGoals extract(String brief, @Nullable Consumer<ChatResponse> usageSink, BooleanSupplier cancelled) {
        requireReviewTextSafe("brief-learning-goals/brief", brief);
        if (!reviewer.configured() || brief.isBlank() || cancelled.getAsBoolean()) {
            return null;
        }
        String response = reviewer.call(SYSTEM_PROMPT, objectMapper.writeValueAsString(new GoalRequest(brief)), usageSink, cancelled, MAX_OUTPUT_TOKENS);
        try {
            return parse(response, brief);
        }
        catch (RuntimeException error) {
            log.warn("Brief learning-goal response did not parse: {}", ProviderFailureClass.describe(error));
            return null;
        }
    }

    @Nullable
    BriefLearningGoals parse(@Nullable String response, String brief) {
        if (response == null || response.isBlank()) {
            return null;
        }
        GoalResponse parsed = objectMapper.readValue(extractJsonPayload(response), GoalResponse.class);
        if (parsed == null || parsed.goals() == null || parsed.goals().isEmpty() || parsed.goals().size() > MAX_GOALS) {
            return null;
        }
        String normalizedBrief = normalizeWhitespace(brief);
        List<BriefLearningGoals.Goal> goals = new ArrayList<>();
        var distinctQuotes = new HashSet<String>();
        for (GoalItem item : parsed.goals()) {
            if (item == null || item.quote() == null || item.quote().isBlank()) {
                return null;
            }
            String quote = normalizeWhitespace(item.quote());
            if (!normalizedBrief.contains(quote) || !distinctQuotes.add(quote)) {
                return null;
            }
            goals.add(new BriefLearningGoals.Goal("G" + (goals.size() + 1), quote));
        }
        return new BriefLearningGoals(brief, goals);
    }

    private static String normalizeWhitespace(String value) {
        return WHITESPACE.matcher(value.strip()).replaceAll(" ");
    }
}
