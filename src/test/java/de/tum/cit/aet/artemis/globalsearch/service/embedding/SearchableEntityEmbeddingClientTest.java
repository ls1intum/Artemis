package de.tum.cit.aet.artemis.globalsearch.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.core.service.llm.LlmEmbeddingResponse;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEmbeddingProperties;
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;
import io.micrometer.observation.ObservationRegistry;

/**
 * Tests {@link SearchableEntityEmbeddingClient} against a real local HTTP server, so the request format, the timeouts and
 * the response checks are exercised as they are in production.
 */
class SearchableEntityEmbeddingClientTest {

    private static final String MODEL = "Qwen/Qwen3-Embedding-8B";

    private static final String INSTRUCTION = "Instruct: Find course content\nQuery: ";

    private HttpServer server;

    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    private record RecordedRequest(String method, String path, String authorization, JsonNode body) {
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Starts a server that answers {@code /v1/embeddings} with the response the given function builds from the request.
     */
    private SearchableEntityEmbeddingClient clientFor(Function<JsonNode, String> responder, Duration queryTimeout, int maxInputs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            JsonNode body = JsonObjectMapper.get().readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"), body));
            String response = responder.apply(body);
            int status = response.matches("\\d{3}") ? Integer.parseInt(response) : 200;
            byte[] bytes = status == 200 ? response.getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        // A trailing slash on the base URL must not lead to a double slash in the path.
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        var properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", "", "text2vec-openai", "embedding", null, INSTRUCTION);
        var embeddingProperties = new WeaviateEmbeddingProperties(600, 30, maxInputs, queryTimeout, Duration.ofSeconds(10));
        var llmClientService = new LlmClientService(LlmTestCatalog.embedding(MODEL, baseUrl, "secret-key"), new JsonMapper(), ObservationRegistry.NOOP);
        return new SearchableEntityEmbeddingClient(properties, embeddingProperties, llmClientService);
    }

    /**
     * Answers with one unit vector per input, in reverse index order, so the client must sort by index.
     */
    private static String unitVectors(JsonNode request) {
        int count = request.get("input").size();
        StringBuilder data = new StringBuilder();
        for (int i = count - 1; i >= 0; i--) {
            data.append(data.isEmpty() ? "" : ",").append("{\"object\":\"embedding\",\"index\":").append(i).append(",\"embedding\":[").append(i + 1).append(",0]}");
        }
        return "{\"object\":\"list\",\"model\":\"" + MODEL + "\",\"data\":[" + data + "],\"usage\":{\"prompt_tokens\":3}}";
    }

    @Test
    void embedQuery_sendsTheQueryWithTheInstructionModelAndKey() throws IOException {
        var client = clientFor(SearchableEntityEmbeddingClientTest::unitVectors, Duration.ofSeconds(3), 32);

        float[] vector = client.embedQuery("flyweight pattern");

        assertThat(vector).containsExactly(1f, 0f);
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/v1/embeddings");
            assertThat(request.authorization()).isEqualTo("Bearer secret-key");
            assertThat(request.body().get("model").asString()).isEqualTo(MODEL);
            assertThat(request.body().get("input")).hasSize(1);
            assertThat(request.body().get("input").get(0).asString()).isEqualTo(INSTRUCTION + "flyweight pattern");
            assertThat(request.body().get("encoding_format").asString()).isEqualTo("float");
        });
    }

    @Test
    void embedDocuments_sendsTextsWithoutTheInstructionAndReturnsVectorsInInputOrder() throws IOException {
        var client = clientFor(SearchableEntityEmbeddingClientTest::unitVectors, Duration.ofSeconds(3), 32);

        List<float[]> vectors = client.embedDocuments(List.of("first", "second", "third"));

        assertThat(vectors).containsExactly(new float[] { 1f, 0f }, new float[] { 2f, 0f }, new float[] { 3f, 0f });
        assertThat(requests.getFirst().body().get("input").get(0).asString()).isEqualTo("first");
    }

    @Test
    void embedDocuments_rejectsMoreTextsThanOneRequestMayCarry() throws IOException {
        var client = clientFor(SearchableEntityEmbeddingClientTest::unitVectors, Duration.ofSeconds(3), 2);

        assertThatThrownBy(() -> client.embedDocuments(List.of("a", "b", "c"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(client.embedDocuments(List.of())).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void embedQuery_failsFastWhenTheEndpointIsSlowerThanTheQueryTimeout() throws IOException {
        var client = clientFor(request -> {
            try {
                Thread.sleep(2_000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return unitVectors(request);
        }, Duration.ofMillis(200), 32);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.embedQuery("slow")).isInstanceOf(EmbeddingFailedException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void embedDocuments_turnsAnErrorStatusIntoEmbeddingFailed() throws IOException {
        var client = clientFor(_ -> "503", Duration.ofSeconds(3), 32);

        assertThatThrownBy(() -> client.embedDocuments(List.of("a"))).isInstanceOf(EmbeddingFailedException.class);
        // the outbox retries a failed write, so transport retries are disabled, although the endpoint allows three
        assertThat(requests).hasSize(1);
    }

    @Test
    void embedQuery_turnsAnIndexBeyondIntIntoEmbeddingFailed() throws IOException {
        var client = clientFor(_ -> "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":2147483648,\"embedding\":[1,0]}]}", Duration.ofSeconds(3), 32);

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class).hasMessageContaining("index");
    }

    @Test
    void embedQuery_turnsAnEmptyEntryIntoEmbeddingFailed() throws IOException {
        var client = clientFor(_ -> "{\"object\":\"list\",\"data\":[null]}", Duration.ofSeconds(3), 32);

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class);
    }

    @Test
    void embedQuery_turnsANullBodyIntoEmbeddingFailed() throws IOException {
        var client = clientFor(_ -> "null", Duration.ofSeconds(3), 32);

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class);
    }

    @Test
    void embedQuery_turnsAnInvalidEmbeddingTypeIntoEmbeddingFailed() throws IOException {
        var client = clientFor(_ -> "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":{\"bad\":1}}]}", Duration.ofSeconds(3), 32);

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class);
    }

    @Test
    void embedQueryDecodesLittleEndianBase64Floats() throws IOException {
        // IEEE 754 little-endian encoding of 1.0 and -0.5.
        var client = clientFor(_ -> """
                {"data":[{"index":0,"embedding":"AACAPwAAAL8="}]}
                """, Duration.ofSeconds(3), 32);

        assertThat(client.embedQuery("query")).containsExactly(1f, -0.5f);
    }

    @Test
    void embedQueryReadsEveryCoordinateOfALargeVector() throws IOException {
        var vector = new float[768];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = i - 0.5f;
        }
        var response = JsonObjectMapper.get().writeValueAsString(new LlmEmbeddingResponse(List.of(new LlmEmbeddingResponse.Entry(0L, vector))));
        var client = clientFor(_ -> response, Duration.ofSeconds(3), 32);

        assertThat(client.embedQuery("query")).containsExactly(vector);
    }

    @Test
    void embedQuery_turnsInvalidBase64EmbeddingIntoEmbeddingFailed() throws IOException {
        var client = clientFor(_ -> "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":\"not!base64\"}]}", Duration.ofSeconds(3), 32);

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class);
    }

    @Test
    void embedQuery_turnsTruncatedBase64EmbeddingIntoEmbeddingFailed() throws IOException {
        // AACAPwA= decodes to 5 bytes (one 4-byte float plus 1 trailing byte); the extra byte cannot encode another float.
        var client = clientFor(_ -> "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":\"AACAPwA=\"}]}", Duration.ofSeconds(3), 32);

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class).hasMessageContaining("byte length");
    }

    @Test
    void embedQuery_failsWhenTheEndpointIsUnreachable() throws IOException {
        var client = clientFor(SearchableEntityEmbeddingClientTest::unitVectors, Duration.ofMillis(500), 32);
        server.stop(0);

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class);
    }

    @Test
    void embedFailsWithoutRequestWhenNoPresetIsAssigned() {
        var properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", "", "text2vec-openai", null, null, INSTRUCTION);
        var embeddingProperties = new WeaviateEmbeddingProperties(600, 30, 32, Duration.ofSeconds(3), Duration.ofSeconds(10));
        var client = new SearchableEntityEmbeddingClient(properties, embeddingProperties, new LlmClientService(LlmTestCatalog.empty(), new JsonMapper(), ObservationRegistry.NOOP));

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class).hasMessageContaining("embedding-llm-preset");
    }

    @Test
    void timeoutsMustBePositive() {
        assertThatThrownBy(() -> new WeaviateEmbeddingProperties(600, 30, 32, Duration.ZERO, Duration.ofSeconds(60))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeaviateEmbeddingProperties(600, 30, 32, Duration.ofSeconds(3), Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void embeddingDataComparesItsVectorByContentAndDoesNotPrintIt() {
        var data = new LlmEmbeddingResponse.Entry(3L, new float[] { 1f, 2f, 3f });
        var sameContent = new LlmEmbeddingResponse.Entry(3L, new float[] { 1f, 2f, 3f });

        assertThat(data).isEqualTo(sameContent).hasSameHashCodeAs(sameContent);
        assertThat(data).isNotEqualTo(new LlmEmbeddingResponse.Entry(3L, new float[] { 1f, 2f, 4f }));
        assertThat(data).isNotEqualTo(new LlmEmbeddingResponse.Entry(4L, new float[] { 1f, 2f, 3f }));
        assertThat(data).hasToString("Entry[index=3, dimensions=3]");
        assertThat(new LlmEmbeddingResponse.Entry(0L, null)).hasToString("Entry[index=0, dimensions=0]");
    }

    @Test
    void validate_rejectsResponsesThatDoNotGiveExactlyOneUsableVectorPerInput() {
        var good = new LlmEmbeddingResponse.Entry(0L, new float[] { 1f, 0f });

        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(null, 1)).isInstanceOf(EmbeddingFailedException.class);
        var withEmptyEntry = new java.util.ArrayList<LlmEmbeddingResponse.Entry>();
        withEmptyEntry.add(null);
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new LlmEmbeddingResponse(withEmptyEntry), 1)).isInstanceOf(EmbeddingFailedException.class);
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new LlmEmbeddingResponse(List.of(good)), 2)).isInstanceOf(EmbeddingFailedException.class);
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new LlmEmbeddingResponse(List.of(good, good)), 2)).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new LlmEmbeddingResponse(List.of(new LlmEmbeddingResponse.Entry(1L, new float[] { 1f }))), 1))
                .hasMessageContaining("index");
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new LlmEmbeddingResponse(List.of(new LlmEmbeddingResponse.Entry(0L, new float[0]))), 1))
                .hasMessageContaining("Empty");
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new LlmEmbeddingResponse(List.of(new LlmEmbeddingResponse.Entry(0L, new float[] { Float.NaN }))), 1))
                .hasMessageContaining("non-finite");
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new LlmEmbeddingResponse(List.of(new LlmEmbeddingResponse.Entry(0L, new float[] { 0f, 0f }))), 1))
                .hasMessageContaining("zero norm");
        assertThatThrownBy(
                () -> SearchableEntityEmbeddingClient.validate(new LlmEmbeddingResponse(List.of(good, new LlmEmbeddingResponse.Entry(1L, new float[] { 1f, 0f, 0f }))), 2))
                .hasMessageContaining("dimension");
    }
}
