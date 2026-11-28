package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog.endpoint;
import static de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog.preset;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import io.micrometer.observation.ObservationRegistry;

class AgentResponsesReplayTest {

    @Test
    void continuationReplaysOnlyTheRepairedTextAndToolName() throws IOException {
        var mapper = new JsonMapper();
        var output = """
                [{"type":"reasoning","id":"rs-1","summary":[],"encrypted_content":"opaque<|end|>","provider_tag":"trace"},
                 {"type":"message","role":"assistant","id":"msg-1","status":"completed","provider_tag":"keep","content":[
                  {"type":"output_text","text":"before<|chan","annotations":[],"provider_tag":"part"},
                  {"type":"output_text","text":"nel|>after<|end|>","annotations":[]}]},
                 {"type":"function_call","id":"fc-1","call_id":"call-1","name":"lookup<|channel|>commentary","arguments":"{\\"key\\":\\"<|end|>\\"}","provider_tag":"keep"},
                 {"type":"message","role":"assistant","id":"msg-2","status":"completed","content":[{"type":"output_text","text":"tail","annotations":[]}]},
                 {"type":"function_call","id":"fc-2","call_id":"call-2","name":"verify","arguments":"{}"}]
                """;
        byte[] reply = ("""
                {"id":"resp-1","object":"response","model":"test","status":"completed","output":%s}
                """.formatted(output)).getBytes(StandardCharsets.UTF_8);
        var requests = new CopyOnWriteArrayList<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            try (exchange) {
                requests.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, reply.length);
                exchange.getResponseBody().write(reply);
            }
        });
        server.start();
        try {
            var properties = new LlmProperties(Map.of("main", endpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")),
                    Map.of("agent", preset("main", "test", LlmProperties.Api.RESPONSES)), null);
            var clients = new LlmClientService(properties, mapper, ObservationRegistry.NOOP);
            var model = new HarmonyScrubbingChatModel(clients.chatModel("agent"));
            var user = new UserMessage("Work");
            var assistant = AgentToolBatch.normalizeToolNames(model.call(new Prompt(user))).getResult().getOutput();
            assertThat(assistant.getText()).isEqualTo("beforeaftertail");
            assertThat(assistant.getToolCalls()).extracting(call -> call.name()).containsExactly("lookup", "verify");
            model.call(new Prompt(List.of(user, assistant, AgentToolBatch.unexecutedResponses(assistant.getToolCalls(), "retry"))));

            assertThat(requests).hasSize(2);
            var input = requests.getLast().get("input");
            assertThat(input.size()).isEqualTo(8);
            assertThat(input.at("/0/role").asString()).isEqualTo("user");
            assertThat(input.at("/0/content").asString()).isEqualTo("Work");
            var expected = mapper.readTree(output.replace("before<|chan", "before").replace("nel|>after<|end|>", "after").replace("lookup<|channel|>commentary", "lookup"));
            assertThat(input.valueStream().skip(1).limit(5).toList()).containsExactlyElementsOf(expected.valueStream().toList());
            var results = mapper.readTree("""
                    [{"type":"function_call_output","call_id":"call-1","output":"retry"},
                     {"type":"function_call_output","call_id":"call-2","output":"retry"}]
                    """);
            assertThat(input.valueStream().skip(6).toList()).containsExactlyElementsOf(results.valueStream().toList());
        }
        finally {
            server.stop(0);
        }
    }
}
