package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;

/** Renders instructor-facing tool progress without exposing raw commands, arguments, or secret-bearing paths. */
final class AgentToolProgress {

    private static final int MAX_PATH_CHARS = 160;

    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private AgentToolProgress() {
    }

    static String describe(AssistantMessage.ToolCall toolCall) {
        String path = sanitizePath(extractJsonStringValue(toolCall.arguments() == null ? "" : toolCall.arguments(), "path"));
        return switch (toolCall.name()) {
            case "read_file" -> path == null ? "Reviewing an exercise file." : "Reviewing " + path + ".";
            case "write_file", "edit_file" -> path == null ? "Working on an exercise file." : "Working on " + path + ".";
            case "bash" -> "Running a workspace command.";
            case "verify" -> "Checking the exercise.";
            case "delete_file" -> path == null ? "Removing an exercise file." : "Removing " + path + ".";
            case "submit" -> "Submitting the current work for checking.";
            default -> "Continuing the exercise update.";
        };
    }

    static String attemptedNames(ChatResponse response) {
        try {
            String names = response.getResult().getOutput().getToolCalls().stream().map(AssistantMessage.ToolCall::name)
                    .map(name -> UNSAFE_CHARACTERS.matcher(name == null ? "" : name).replaceAll("")).filter(name -> !name.isBlank()).distinct().collect(Collectors.joining(", "));
            return names.isBlank() ? "unknown" : names.length() > 80 ? names.substring(0, 80) : names;
        }
        catch (RuntimeException e) {
            return "unknown";
        }
    }

    @Nullable
    private static String sanitizePath(@Nullable String path) {
        if (path == null) {
            return null;
        }
        HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(path, new byte[0], HyperionSecretMaterialPolicy.Origin.TOOL_OBSERVATION);
        if (!assessment.isSafe()) {
            return assessment.safePath();
        }
        String sanitized = UNSAFE_CHARACTERS.matcher(path).replaceAll(" ").replaceAll("\\s+", " ").strip();
        if (sanitized.isEmpty()) {
            return null;
        }
        if (sanitized.codePointCount(0, sanitized.length()) <= MAX_PATH_CHARS) {
            return sanitized;
        }
        return sanitized.substring(0, sanitized.offsetByCodePoints(0, MAX_PATH_CHARS - 1)) + "…";
    }

    @Nullable
    private static String extractJsonStringValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\") : null;
    }
}
