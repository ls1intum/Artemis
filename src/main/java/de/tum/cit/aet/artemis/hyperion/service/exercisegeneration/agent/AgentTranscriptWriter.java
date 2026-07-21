package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;

/**
 * Writes the full model conversation of a finished agent session to disk as Markdown, so an operator can read qualitatively what the model saw, said, and did — the
 * progress-event stream and final artifacts alone cannot answer "why did the agent do that". Disabled by default: it only writes when {@code artemis.hyperion.agent.transcript-dir}
 * names a directory, which deployments leave unset and test/debug environments point at a scratch folder. Strictly best-effort — a transcript write failure never affects the run.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class AgentTranscriptWriter {

    private static final Logger log = LoggerFactory.getLogger(AgentTranscriptWriter.class);

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final String transcriptDirectory;

    public AgentTranscriptWriter(@Value("${artemis.hyperion.agent.transcript-dir:}") String transcriptDirectory) {
        this.transcriptDirectory = transcriptDirectory == null ? "" : transcriptDirectory.strip();
    }

    /** @return whether transcript writing is enabled (a target directory is configured) */
    public boolean enabled() {
        return !transcriptDirectory.isBlank();
    }

    /**
     * Writes one session's conversation under {@code <transcript-dir>/exercise-<id>/<timestamp>-<label>.md}. No-op when disabled or the conversation is absent.
     *
     * @param exerciseId   the exercise the session generated
     * @param label        a short caller-chosen label (e.g. {@code attempt-1-staged}, {@code attempt-2-repair}); sanitized for the filename
     * @param conversation the conversation as returned by the agent loop (system message excluded), or {@code null}
     */
    public void write(long exerciseId, String label, @Nullable List<Message> conversation) {
        if (!enabled() || conversation == null || conversation.isEmpty()) {
            return;
        }
        try {
            Path directory = Path.of(transcriptDirectory).resolve("exercise-" + exerciseId);
            Files.createDirectories(directory);
            String safeLabel = label == null ? "session" : label.replaceAll("[^a-zA-Z0-9._-]", "-");
            Path file = directory.resolve(FILE_TIMESTAMP.format(Instant.now()) + "-" + safeLabel + ".md");
            Files.writeString(file, render(label, conversation), StandardCharsets.UTF_8);
            log.info("Wrote agent transcript for exercise {} to {}", exerciseId, file);
        }
        catch (IOException | RuntimeException e) {
            log.warn("Could not write agent transcript for exercise {} ({}): {}", exerciseId, label, e.getMessage());
        }
    }

    static String render(@Nullable String label, List<Message> conversation) {
        StringBuilder out = new StringBuilder("# Agent transcript");
        if (label != null && !label.isBlank()) {
            out.append(" — ").append(label);
        }
        out.append("\n\n").append(conversation.size()).append(" messages.\n");
        for (Message message : conversation) {
            switch (message) {
                case SystemMessage system -> out.append("\n## SYSTEM\n\n").append(system.getText()).append('\n');
                case UserMessage user -> out.append("\n## USER\n\n").append(user.getText()).append('\n');
                case AssistantMessage assistant -> {
                    out.append("\n## ASSISTANT\n\n");
                    if (assistant.getText() != null && !assistant.getText().isBlank()) {
                        out.append(assistant.getText()).append('\n');
                    }
                    for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls() == null ? List.<AssistantMessage.ToolCall>of() : assistant.getToolCalls()) {
                        out.append("\n### tool_call ").append(toolCall.name()).append("\n\n```json\n").append(toolCall.arguments()).append("\n```\n");
                    }
                }
                case ToolResponseMessage toolResponse -> {
                    for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                        out.append("\n### tool_result ").append(response.name()).append("\n\n```\n").append(response.responseData()).append("\n```\n");
                    }
                }
                default -> out.append("\n## ").append(message.getMessageType()).append("\n\n").append(String.valueOf(message.getText())).append('\n');
            }
        }
        return out.toString();
    }
}
