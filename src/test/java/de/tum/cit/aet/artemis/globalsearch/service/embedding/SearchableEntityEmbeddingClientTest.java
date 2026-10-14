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
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEmbeddingProperties;
import de.tum.cit.aet.artemis.globalsearch.dto.embedding.EmbeddingResponseDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;

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
            int status = response.startsWith("{") ? 200 : Integer.parseInt(response);
            byte[] bytes = status == 200 ? response.getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        // A trailing slash on the base URL must not lead to a double slash in the path.
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        var properties = new WeaviateConfigurationProperties(true, "localhost", 8001, 50051, "http", "", "text2vec-openai", MODEL, baseUrl, "secret-key", null, INSTRUCTION);
        var embeddingProperties = new WeaviateEmbeddingProperties(600, 30, maxInputs, queryTimeout, Duration.ofSeconds(10));
        return new SearchableEntityEmbeddingClient(RestClient.builder(), properties, embeddingProperties);
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
    }

    @Test
    void embedQuery_failsWhenTheEndpointIsUnreachable() throws IOException {
        var client = clientFor(SearchableEntityEmbeddingClientTest::unitVectors, Duration.ofMillis(500), 32);
        server.stop(0);

        assertThatThrownBy(() -> client.embedQuery("anything")).isInstanceOf(EmbeddingFailedException.class);
    }

    @Test
    void timeoutsMustBePositive() {
        assertThatThrownBy(() -> new WeaviateEmbeddingProperties(600, 30, 32, Duration.ZERO, Duration.ofSeconds(60))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeaviateEmbeddingProperties(600, 30, 32, Duration.ofSeconds(3), Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_rejectsResponsesThatDoNotGiveExactlyOneUsableVectorPerInput() {
        var good = new EmbeddingResponseDTO.EmbeddingDataDTO(0, new float[] { 1f, 0f });

        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(null, 1)).isInstanceOf(EmbeddingFailedException.class);
        var withEmptyEntry = new java.util.ArrayList<EmbeddingResponseDTO.EmbeddingDataDTO>();
        withEmptyEntry.add(null);
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new EmbeddingResponseDTO(withEmptyEntry), 1)).isInstanceOf(EmbeddingFailedException.class);
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new EmbeddingResponseDTO(List.of(good)), 2)).isInstanceOf(EmbeddingFailedException.class);
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new EmbeddingResponseDTO(List.of(good, good)), 2)).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new EmbeddingResponseDTO(List.of(new EmbeddingResponseDTO.EmbeddingDataDTO(1, new float[] { 1f }))), 1))
                .hasMessageContaining("index");
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient.validate(new EmbeddingResponseDTO(List.of(new EmbeddingResponseDTO.EmbeddingDataDTO(0, new float[0]))), 1))
                .hasMessageContaining("Empty");
        assertThatThrownBy(
                () -> SearchableEntityEmbeddingClient.validate(new EmbeddingResponseDTO(List.of(new EmbeddingResponseDTO.EmbeddingDataDTO(0, new float[] { Float.NaN }))), 1))
                .hasMessageContaining("non-finite");
        assertThatThrownBy(
                () -> SearchableEntityEmbeddingClient.validate(new EmbeddingResponseDTO(List.of(new EmbeddingResponseDTO.EmbeddingDataDTO(0, new float[] { 0f, 0f }))), 1))
                .hasMessageContaining("zero norm");
        assertThatThrownBy(() -> SearchableEntityEmbeddingClient
                .validate(new EmbeddingResponseDTO(List.of(good, new EmbeddingResponseDTO.EmbeddingDataDTO(1, new float[] { 1f, 0f, 0f }))), 2)).hasMessageContaining("dimension");
    }
}
