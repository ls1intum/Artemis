package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

class AgentTranscriptWriterTest {

    private static List<Message> conversation() {
        AssistantMessage withToolCall = AssistantMessage.builder().content("Reading the file first.")
                .toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "read_file", "{\"path\":\"solution/A.java\"}"))).build();
        ToolResponseMessage toolResult = ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse("c1", "read_file", "class A {}"))).build();
        return List.of(new UserMessage("Create the exercise."), withToolCall, toolResult, new AssistantMessage("Done."));
    }

    @Test
    void render_containsEveryRoleToolCallAndToolResultVerbatim() {
        String rendered = AgentTranscriptWriter.render("attempt-1-staged", conversation());

        assertThat(rendered).contains("# Agent transcript — attempt-1-staged").contains("## USER").contains("Create the exercise.").contains("## ASSISTANT")
                .contains("### tool_call read_file").contains("{\"path\":\"solution/A.java\"}").contains("### tool_result read_file").contains("class A {}").contains("Done.");
    }

    @Test
    void write_withConfiguredDirectory_writesOneMarkdownFilePerSession(@TempDir Path directory) throws IOException {
        AgentTranscriptWriter writer = new AgentTranscriptWriter(directory.toString());

        writer.write(42, "attempt-2-repair-completed", conversation());

        try (var files = Files.list(directory.resolve("exercise-42"))) {
            List<Path> written = files.toList();
            assertThat(written).hasSize(1);
            assertThat(written.getFirst().getFileName().toString()).endsWith("-attempt-2-repair-completed.md");
            assertThat(Files.readString(written.getFirst())).contains("### tool_call read_file");
        }
    }

    @Test
    void writeAudit_writesReviewerEvidenceWithoutPretendingItWasChat(@TempDir Path directory) throws IOException {
        AgentTranscriptWriter writer = new AgentTranscriptWriter(directory.toString());

        writer.writeAudit(42, "concept-review-1", "Selected candidate: 2\n\nCandidate 1: insufficient\nCandidate 2: accepted");

        try (var files = Files.list(directory.resolve("exercise-42"))) {
            List<Path> written = files.toList();
            assertThat(written).hasSize(1);
            assertThat(written.getFirst().getFileName().toString()).endsWith("-concept-review-1.md");
            assertThat(Files.readString(written.getFirst())).contains("# Generation audit — concept-review-1", "Selected candidate: 2", "Candidate 1: insufficient")
                    .doesNotContain("## ASSISTANT", "## USER");
        }
    }

    @Test
    void write_disabledOrEmptyConversation_isANoOp(@TempDir Path directory) throws IOException {
        new AgentTranscriptWriter("").write(42, "label", conversation());
        new AgentTranscriptWriter(directory.toString()).write(42, "label", null);
        new AgentTranscriptWriter(directory.toString()).write(42, "label", List.of());

        try (var files = Files.list(directory)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void write_sanitizesHostileLabelsIntoSafeFilenames(@TempDir Path directory) throws IOException {
        new AgentTranscriptWriter(directory.toString()).write(7, "../../etc/passwd", conversation());

        try (var files = Files.list(directory.resolve("exercise-7"))) {
            List<Path> written = files.toList();
            assertThat(written).hasSize(1);
            assertThat(written.getFirst().getFileName().toString()).doesNotContain("/").endsWith("-..-..-etc-passwd.md");
        }
    }
}
