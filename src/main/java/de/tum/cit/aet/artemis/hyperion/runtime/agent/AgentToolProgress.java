package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import de.tum.cit.aet.artemis.hyperion.runtime.security.HyperionSecretMaterialPolicy;

/** Renders instructor-facing tool progress without exposing raw commands, arguments, or secret-bearing paths. */
final class AgentToolProgress {

    private static final int MAX_PATH_CHARS = 160;

    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // A number or boolean in place of the path is not a path, so it must not be coerced to text.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .withCoercionConfig(LogicalType.Textual, config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail).setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private AgentToolProgress() {
    }

    static String describe(AssistantMessage.ToolCall toolCall) {
        String path = sanitizePath(extractJsonPathValue(toolCall.arguments()));
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

    @Nullable
    private static String sanitizePath(@Nullable String path) {
        if (path == null) {
            return null;
        }
        HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(path, new byte[0]);
        if (!assessment.isSafe()) {
            return assessment.safePath();
        }
        String sanitized = WHITESPACE.matcher(UNSAFE_CHARACTERS.matcher(path).replaceAll(" ")).replaceAll(" ").strip();
        if (sanitized.isEmpty()) {
            return null;
        }
        if (sanitized.codePointCount(0, sanitized.length()) <= MAX_PATH_CHARS) {
            return sanitized;
        }
        return sanitized.substring(0, sanitized.offsetByCodePoints(0, MAX_PATH_CHARS - 1)) + "…";
    }

    @Nullable
    private static String extractJsonPathValue(@Nullable String json) {
        if (json == null) {
            return null;
        }
        try {
            PathArguments arguments = MAPPER.readValue(json, PathArguments.class);
            return arguments == null ? null : arguments.path();
        }
        catch (JacksonException e) {
            return null;
        }
    }

    /**
     * The part of a tool call's JSON arguments that names a workspace path.
     *
     * @param path the path the tool call works on, absent when the tool takes none
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PathArguments(@Nullable String path) {
    }
}
