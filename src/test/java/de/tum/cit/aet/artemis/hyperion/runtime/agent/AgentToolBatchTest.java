package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

class AgentToolBatchTest {

    @ParameterizedTest
    @CsvSource({ "bash<|channel|>commentary,bash", "' read_file ',read_file", "'<|channel|>',''", "'',''" })
    void sanitizeToolNameKeepsOnlyTheNameBeforeTheFirstLeakedControlToken(String name, String expected) {
        assertThat(AgentToolBatch.sanitizeToolName(name)).isEqualTo(expected);
    }

    @Test
    void unexecutedResponsesAnswerEveryRequestedCallId() {
        List<AssistantMessage.ToolCall> calls = List.of(call("a", "read_file", "{}"), call("b", "bash", "{}"));

        ToolResponseMessage answered = AgentToolBatch.unexecutedResponses(calls, "ERROR: not executed");

        assertThat(answered.getResponses()).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("a", "b");
        assertThat(answered.getResponses()).extracting(ToolResponseMessage.ToolResponse::responseData).containsOnly("ERROR: not executed");
    }

    @Test
    void anIdenticalRejectedCallIsEscalatedFromItsThirdOccurrence() {
        Map<String, Integer> counts = new HashMap<>();
        List<AssistantMessage.ToolCall> calls = List.of(call("1", "search", "{\"text\":\"x\"}"));

        for (int occurrence = 1; occurrence < AgentToolBatch.REPEATED_REJECTION_ESCALATION; occurrence++) {
            List<Message> conversation = batch(response("1", "search", "ERROR: multiline text"));
            assertThat(AgentToolBatch.escalateRepeatedRejections(conversation, calls, counts)).isFalse();
            assertThat(lastResponse(conversation)).isEqualTo("ERROR: multiline text");
        }
        List<Message> conversation = batch(response("1", "search", "ERROR: multiline text"));

        assertThat(AgentToolBatch.escalateRepeatedRejections(conversation, calls, counts)).isTrue();
        assertThat(lastResponse(conversation)).startsWith("ERROR: this exact search call has now been rejected 3 times").endsWith("The rejection was: ERROR: multiline text");
        assertThat(AgentToolBatch.lastRejection(conversation)).startsWith("search: ERROR: this exact search call");
    }

    @Test
    void identicalSuccessfulWritesAndSubmitsAreNeverRepeats() {
        Map<String, Integer> counts = new HashMap<>();
        List<AssistantMessage.ToolCall> calls = List.of(call("1", "write_file", "{\"path\":\"A.java\"}"), call("2", "submit", "{}"));

        for (int occurrence = 0; occurrence < AgentToolBatch.REPEATED_REJECTION_ESCALATION + 2; occurrence++) {
            List<Message> conversation = batch(response("1", "write_file", "Wrote A.java"), response("2", "submit", "ERROR: vetoed"));
            assertThat(AgentToolBatch.escalateRepeatedRejections(conversation, calls, counts)).isFalse();
        }
    }

    @Test
    void aRepeatedObservationKeepsItsFullResultAndRestartsAfterTheWorkspaceOrConversationChanged() {
        Map<String, Integer> counts = new HashMap<>();
        String specification = "# SPEC.md\n" + "rule ".repeat(200);
        List<AssistantMessage.ToolCall> read = List.of(call("1", "read_file", "{\"path\":\"SPEC.md\"}"));
        List<AssistantMessage.ToolCall> readAndEdit = List.of(call("1", "read_file", "{\"path\":\"SPEC.md\"}"), call("2", "edit_file", "{\"path\":\"A.java\"}"));

        AgentToolBatch.escalateRepeatedRejections(batch(response("1", "read_file", specification)), read, counts);
        AgentToolBatch.escalateRepeatedRejections(batch(response("1", "read_file", specification)), read, counts);
        List<Message> third = batch(response("1", "read_file", specification));
        assertThat(AgentToolBatch.escalateRepeatedRejections(third, read, counts)).isFalse();
        assertThat(lastResponse(third)).startsWith("NOTE: this exact read_file call has now returned the same result 3 times").endsWith(specification);

        // An edit elsewhere in the same batch changed the workspace, so the identical read starts a new count.
        List<Message> afterEdit = batch(response("1", "read_file", specification), response("2", "edit_file", "Edited A.java"));
        AgentToolBatch.escalateRepeatedRejections(afterEdit, readAndEdit, counts);
        assertThat(lastResponseOf(afterEdit, "1")).isEqualTo(specification);

        // Compaction dropped the earlier results; rejected calls keep their count.
        List<AssistantMessage.ToolCall> search = List.of(call("3", "search", "{\"text\":\"x\"}"));
        AgentToolBatch.escalateRepeatedRejections(batch(response("3", "search", "ERROR: multiline text")), search, counts);
        AgentToolBatch.escalateRepeatedRejections(batch(response("3", "search", "ERROR: multiline text")), search, counts);
        AgentToolBatch.escalateRepeatedRejections(batch(response("1", "read_file", specification)), read, counts);
        AgentToolBatch.forgetObservations(counts);
        List<Message> afterCompaction = batch(response("1", "read_file", specification));
        AgentToolBatch.escalateRepeatedRejections(afterCompaction, read, counts);
        assertThat(lastResponse(afterCompaction)).isEqualTo(specification);
        assertThat(AgentToolBatch.escalateRepeatedRejections(batch(response("3", "search", "ERROR: multiline text")), search, counts)).isTrue();
    }

    @Test
    void aBatchCountsAtMostOnceTowardEndingTheSession() {
        Map<String, Integer> counts = new HashMap<>();
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            calls.add(call("c" + index, "search", "{\"text\":\"x\"}"));
            responses.add(response("c" + index, "search", "ERROR: multiline text"));
        }
        List<Message> conversation = new ArrayList<>(List.of(ToolResponseMessage.builder().responses(responses).build()));

        assertThat(AgentToolBatch.escalateRepeatedRejections(conversation, calls, counts)).isTrue();
        assertThat(((ToolResponseMessage) conversation.getLast()).getResponses()).extracting(ToolResponseMessage.ToolResponse::responseData)
                .filteredOn(data -> data.startsWith("ERROR: this exact search call")).hasSize(5);
    }

    @Test
    void onlyEditAndSearchRejectionsAreRecoverableAndVerifyIsNeverARejectedAction() {
        assertThat(AgentToolBatch.hasRejectedToolAction(batch(response("1", "verify", "ERROR: tests fail")))).isFalse();
        assertThat(AgentToolBatch.hasRejectedToolAction(batch(response("1", "read_file", "\"ERROR: missing\"")))).isTrue();
        assertThat(AgentToolBatch.hasOnlyRecoverableFileToolRejections(batch(response("1", "edit_file", "ERROR: not unique"), response("2", "search", "ERROR: multiline"))))
                .isTrue();
        assertThat(AgentToolBatch.hasOnlyRecoverableFileToolRejections(batch(response("1", "edit_file", "ERROR: not unique"), response("2", "bash", "ERROR: denied")))).isFalse();
        assertThat(AgentToolBatch.lastRejection(batch(response("1", "read_file", "contents")))).isEqualTo("not recorded");
    }

    private static AssistantMessage.ToolCall call(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }

    private static ToolResponseMessage.ToolResponse response(String id, String name, String data) {
        return new ToolResponseMessage.ToolResponse(id, name, data);
    }

    private static List<Message> batch(ToolResponseMessage.ToolResponse... responses) {
        List<Message> conversation = new ArrayList<>();
        conversation.add(ToolResponseMessage.builder().responses(List.of(responses)).build());
        return conversation;
    }

    private static String lastResponse(List<Message> conversation) {
        return ((ToolResponseMessage) conversation.getLast()).getResponses().getFirst().responseData();
    }

    private static String lastResponseOf(List<Message> conversation, String id) {
        return ((ToolResponseMessage) conversation.getLast()).getResponses().stream().filter(response -> response.id().equals(id)).findFirst().orElseThrow().responseData();
    }
}
