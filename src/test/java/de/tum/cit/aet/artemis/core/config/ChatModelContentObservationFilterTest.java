package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ChatModelContentObservationFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatModelContentObservationFilter filter = new ChatModelContentObservationFilter(objectMapper, true,
            ChatModelContentObservationFilter.DEFAULT_MAX_ATTRIBUTE_BYTES);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(ObjectMapper.class).withPropertyValues("spring.profiles.active=core")
            .withUserConfiguration(ChatModelContentObservationFilter.class);

    @Test
    void contentCaptureRequiresExplicitOptIn() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ChatModelContentObservationFilter.class));
        contextRunner.withPropertyValues("management.opentelemetry.instrumentation.gen-ai.capture-content=true")
                .run(context -> assertThat(context).hasSingleBean(ChatModelContentObservationFilter.class));
    }

    @Test
    void theAttributeBoundIsReadFromConfiguration() {
        contextRunner.withPropertyValues(ChatModelContentObservationFilter.MAX_ATTRIBUTE_BYTES_PROPERTY + "=250000")
                .run(context -> assertThat(context).hasSingleBean(ChatModelContentObservationFilter.class));
    }

    @Test
    void aNonPositiveBoundFallsBackToTheShippedDefaultInsteadOfBreakingTracing() {
        // Two things at once. A bound of zero would omit every attribute, silently reproducing the measurement loss the property exists to let an operator escape. And this
        // bean is lazy, so throwing here would not fail startup — it would surface at the first span, inside the provider call this filter only observes. Neither is acceptable,
        // so the misconfiguration is refused and the shipped default is used.
        String content = "x".repeat(80_000);
        ChatModelObservationContext context = ChatModelObservationContext.builder().prompt(new Prompt(new UserMessage(content))).provider("openai").build();

        new ChatModelContentObservationFilter(objectMapper, true, 0).map(context);

        assertThat(context.getHighCardinalityKeyValue("gen_ai.input.messages").getValue()).contains(content);
        assertThat(context.getHighCardinalityKeyValue("artemis.gen_ai.content.complete").getValue()).isEqualTo("true");
    }

    @Test
    void aConfiguredBoundDecidesWhetherTheSameContentIsKeptOrOmitted() {
        // The bound is the operator's dial, not a compile-time fact: one deployment's collector sizing must be able to differ from another's without a rebuild.
        String content = "x".repeat(80_000);
        ChatModelObservationContext omitted = ChatModelObservationContext.builder().prompt(new Prompt(new UserMessage(content))).provider("openai").build();
        ChatModelObservationContext kept = ChatModelObservationContext.builder().prompt(new Prompt(new UserMessage(content))).provider("openai").build();

        new ChatModelContentObservationFilter(objectMapper, true, 64_000).map(omitted);
        new ChatModelContentObservationFilter(objectMapper, true, 200_000).map(kept);

        assertThat(omitted.getHighCardinalityKeyValue("gen_ai.input.messages")).isNull();
        assertThat(omitted.getHighCardinalityKeyValue("artemis.gen_ai.content.complete").getValue()).isEqualTo("false");
        assertThat(kept.getHighCardinalityKeyValue("gen_ai.input.messages").getValue()).contains(content);
        assertThat(kept.getHighCardinalityKeyValue("artemis.gen_ai.content.complete").getValue()).isEqualTo("true");
    }

    @Test
    void metadataOnlyModeStillClassifiesChatSpans() {
        ChatModelContentObservationFilter metadataOnlyFilter = new ChatModelContentObservationFilter(objectMapper, false,
                ChatModelContentObservationFilter.DEFAULT_MAX_ATTRIBUTE_BYTES);
        ChatModelObservationContext context = ChatModelObservationContext.builder().prompt(new Prompt(new UserMessage("sensitive"))).provider("openai").build();

        metadataOnlyFilter.map(context);

        assertThat(context.getLowCardinalityKeyValue("ai.span").getValue()).isEqualTo("true");
        assertThat(context.getHighCardinalityKeyValues()).noneMatch(keyValue -> keyValue.getKey().startsWith("gen_ai."));
    }

    @Test
    void mapAddsStandardGenAiInputAndOutputMessages() throws Exception {
        ToolCallback tool = new ToolCallback() {

            @Override
            public ToolDefinition getToolDefinition() {
                return new DefaultToolDefinition("inspect_repository", "Inspect a file", "{\"type\":\"object\"}");
            }

            @Override
            public String call(String input) {
                return "unused";
            }
        };
        AssistantMessage toolCall = AssistantMessage.builder().content("").properties(Map.of("reasoningContent", "Inspect the specification before editing."))
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "inspect_repository", "{\"path\":\"SPEC.md\"}"))).build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "inspect_repository", "Specification contents"))).build();
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .prompt(new Prompt(List.of(new SystemMessage("Follow the rubric"), new UserMessage("Create an exercise"), toolCall, toolResponse),
                        ToolCallingChatOptions.builder().toolCallbacks(tool).build()))
                .provider("openai").build();
        context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("Generated exercise")))));

        filter.map(context);

        JsonNode input = objectMapper.readTree(context.getHighCardinalityKeyValue("gen_ai.input.messages").getValue());
        JsonNode output = objectMapper.readTree(context.getHighCardinalityKeyValue("gen_ai.output.messages").getValue());
        JsonNode tools = objectMapper.readTree(context.getHighCardinalityKeyValue("gen_ai.tool.definitions").getValue());
        assertThat(input).hasSize(4);
        assertThat(input.get(0).get("role").asText()).isEqualTo("system");
        assertThat(input.get(1).get("parts").get(0).get("content").asText()).isEqualTo("Create an exercise");
        assertThat(input.get(2).get("parts").get(0).get("type").asText()).isEqualTo("reasoning");
        assertThat(input.get(2).get("parts").get(0).get("content").asText()).isEqualTo("Inspect the specification before editing.");
        assertThat(input.get(2).get("parts").get(1).get("type").asText()).isEqualTo("tool_call");
        assertThat(input.get(2).get("parts").get(1).get("arguments").get("path").asText()).isEqualTo("SPEC.md");
        assertThat(input.get(3).get("parts").get(0).get("type").asText()).isEqualTo("tool_call_response");
        assertThat(input.get(3).get("parts").get(0).get("response").asText()).isEqualTo("Specification contents");
        assertThat(output).hasSize(1);
        assertThat(output.get(0).get("role").asText()).isEqualTo("assistant");
        assertThat(output.get(0).get("parts").get(0).get("content").asText()).isEqualTo("Generated exercise");
        assertThat(output.get(0).get("finish_reason").asText()).isEqualTo("unknown");
        assertThat(tools.get(0).get("name").asText()).isEqualTo("inspect_repository");
        assertThat(tools.get(0).get("parameters").get("type").asText()).isEqualTo("object");
        assertThat(context.getLowCardinalityKeyValue("ai.span").getValue()).isEqualTo("true");
        assertThat(context.getHighCardinalityKeyValue("artemis.gen_ai.content.complete").getValue()).isEqualTo("true");
    }

    @Test
    void oversizedUtf8ContentIsOmittedAndMarkedIncomplete() {
        // Measured in UTF-8 bytes, not characters: each 'é' costs two, so this clears the shipped bound at half the character count.
        String content = "é".repeat(ChatModelContentObservationFilter.DEFAULT_MAX_ATTRIBUTE_BYTES / 2);
        ChatModelObservationContext context = ChatModelObservationContext.builder().prompt(new Prompt(new UserMessage(content))).provider("openai").build();

        filter.map(context);

        assertThat(context.getHighCardinalityKeyValue("gen_ai.input.messages")).isNull();
        assertThat(context.getHighCardinalityKeyValue("artemis.gen_ai.content.complete").getValue()).isEqualTo("false");
    }

    @Test
    void aFullSizedAgenticPromptIsCapturedIntact() {
        // The case the shipped bound exists to serve, and the one a 64000-byte ceiling silently destroyed: an agentic turn whose context carries repository contents and tool
        // output. A harness that requires content on every counted model span records the whole run unmeasurable when a span like this loses its attribute, and because long
        // runs are the successful ones, the loss is not just noise — it inverts the result. Any future tightening has to fail here first.
        String repositoryContents = "public class Solution { /* generated exercise source */ }\n".repeat(6_000);
        String specification = "## Rules\n- R1: the reference solution passes every generated test.\n".repeat(1_500);
        AssistantMessage toolCall = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "read_file", "{\"path\":\"solution/src/Solution.java\"}"))).build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "read_file", repositoryContents)))
                .build();
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .prompt(new Prompt(List.of(new SystemMessage(specification), new UserMessage("Build the exercise"), toolCall, toolResponse))).provider("openai").build();

        filter.map(context);

        String captured = context.getHighCardinalityKeyValue("gen_ai.input.messages").getValue();
        assertThat(captured.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(400_000);
        assertThat(captured).contains("generated exercise source", "the reference solution passes every generated test");
        assertThat(context.getHighCardinalityKeyValue("artemis.gen_ai.content.complete").getValue()).isEqualTo("true");
    }

    @Test
    void malformedToolMetadataCannotBreakAProviderCall() {
        ToolCallback brokenTool = new ToolCallback() {

            @Override
            public ToolDefinition getToolDefinition() {
                throw new IllegalStateException("broken metadata");
            }

            @Override
            public String call(String input) {
                return "unused";
            }
        };
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .prompt(new Prompt(new UserMessage("create"), ToolCallingChatOptions.builder().toolCallbacks(brokenTool).build())).provider("openai").build();

        assertThatCode(() -> filter.map(context)).doesNotThrowAnyException();
        assertThat(context.getHighCardinalityKeyValue("artemis.gen_ai.content.complete").getValue()).isEqualTo("false");
    }
}
