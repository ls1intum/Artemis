package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
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
 * Writes the returned agent conversation to disk as Markdown. Context compaction may have replaced earlier messages; per-call OTel content capture preserves the model exchange.
 * Disabled unless
 * {@code artemis.hyperion.agent.transcript-dir} names a directory, which deployments leave unset. Best-effort — a transcript write failure never affects the run.
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

    public boolean enabled() {
        return !transcriptDirectory.isBlank();
    }

    /**
     * Writes one session's conversation under {@code <transcript-dir>/exercise-<id>/<timestamp>-<uuid>-<label>.md}. No-op when disabled or the conversation is absent.
     *
     * @param exerciseId   the exercise the session generated
     * @param label        a short caller-chosen label, sanitized for the filename
     * @param conversation the conversation as returned by the agent loop, system message excluded
     */
    public void write(long exerciseId, String label, @Nullable List<Message> conversation) {
        if (!enabled() || conversation == null || conversation.isEmpty()) {
            return;
        }
        writeFile(exerciseId, label, render(label, conversation));
    }

    /**
     * Writes non-conversational generation evidence, such as a context-separated review decision, beside the transcript without presenting it as model dialogue.
     *
     * @param exerciseId the exercise whose generation produced the evidence
     * @param label      a short audit label used in the file name
     * @param evidence   the evidence to persist; blank evidence is ignored
     */
    public void writeAudit(long exerciseId, String label, @Nullable String evidence) {
        if (!enabled() || evidence == null || evidence.isBlank()) {
            return;
        }
        String safeLabel = label == null ? "audit" : label.replaceAll("[^a-zA-Z0-9._-]", "-");
        writeFile(exerciseId, safeLabel, "# Generation audit — " + safeLabel + "\n\n" + evidence.strip() + "\n");
    }

    private void writeFile(long exerciseId, String label, String content) {
        try {
            Path directory = Path.of(transcriptDirectory).resolve("exercise-" + exerciseId);
            Files.createDirectories(directory);
            String safeLabel = label == null ? "session" : label.replaceAll("[^a-zA-Z0-9._-]", "-");
            Path file = directory.resolve(FILE_TIMESTAMP.format(Instant.now()) + "-" + UUID.randomUUID() + "-" + safeLabel + ".md");
            FileUtils.writeStringToFile(file.toFile(), content, StandardCharsets.UTF_8);
            log.info("Wrote generation evidence for exercise {} to {}", exerciseId, file);
        }
        catch (IOException | RuntimeException e) {
            log.warn("Could not write generation evidence for exercise {} ({}): {}", exerciseId, label, e.getMessage());
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
