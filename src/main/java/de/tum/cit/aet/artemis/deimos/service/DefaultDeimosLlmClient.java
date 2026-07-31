package de.tum.cit.aet.artemis.deimos.service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.deimos.config.DeimosEnabled;
import de.tum.cit.aet.artemis.deimos.dto.DeimosFailureType;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmRequest;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmResponse;
import de.tum.cit.aet.artemis.deimos.exception.DeimosLlmException;

/**
 * Calls the dedicated Deimos {@link ChatClient} and turns its answer into a {@link DeimosLlmResponse}.
 * <p>
 * The raw assistant text is parsed here rather than through Spring AI's {@code responseEntity(...)} converter. Reasoning
 * models routinely wrap their answer in a preamble or a fenced block, and the strict converter throws on anything that
 * is not bare JSON, which turns a cosmetic formatting difference into a failed participation. Parsing the text directly
 * lets those responses be recovered while still rejecting genuinely unusable ones.
 * <p>
 * No retry is implemented here. Transport retries are configured once via {@code artemis.deimos.llm.max-retries} and
 * performed by the OpenAI SDK; layering a second retry on top would multiply attempts and stall a large batch.
 */
@Conditional(DeimosEnabled.class)
@Lazy
@Service
public class DefaultDeimosLlmClient implements DeimosLlmClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultDeimosLlmClient.class);

    private static final Pattern FENCED_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final String MALICIOUS_FIELD = "malicious";

    private static final String RATIONALE_FIELD = "rationale";

    // Trailing tokens are rejected so a response containing an early guess followed by a corrected verdict is not
    // silently resolved to the first object; such a response falls through to the fenced and balanced candidates.
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final ChatClient chatClient;

    public DefaultDeimosLlmClient(@Qualifier("deimosChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public DeimosLlmResponse analyze(DeimosLlmRequest request) {
        String content;
        try {
            content = chatClient.prompt().system(request.systemPrompt()).user(request.userPrompt()).call().content();
        }
        catch (Exception ex) {
            throw new DeimosLlmException(classifyCallFailure(ex), "LLM call failed for participation " + request.participationId() + ": " + ex.getMessage(), ex);
        }

        if (content == null || content.isBlank()) {
            throw new DeimosLlmException(DeimosFailureType.LLM_UNPARSEABLE, "LLM returned an empty response for participation " + request.participationId());
        }

        return parseVerdict(content).orElseThrow(() -> new DeimosLlmException(DeimosFailureType.LLM_UNPARSEABLE,
                "LLM response for participation " + request.participationId() + " contained no valid verdict object"));
    }

    /**
     * Extracts the verdict from a raw assistant response.
     * <p>
     * Candidates are tried in order of decreasing confidence: the whole trimmed response, then the contents of any
     * fenced block, then any balanced JSON object found in the text. Fences are not stripped indiscriminately, because
     * the required JSON is frequently the fenced content itself.
     *
     * @param content the raw assistant response
     * @return the parsed verdict, or empty if no candidate yielded a valid one
     */
    static Optional<DeimosLlmResponse> parseVerdict(String content) {
        for (String candidate : candidates(content)) {
            Optional<DeimosLlmResponse> parsed = tryParse(candidate);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private static List<String> candidates(String content) {
        List<String> candidates = new ArrayList<>();
        candidates.add(content.strip());

        Matcher fencedMatcher = FENCED_BLOCK_PATTERN.matcher(content);
        while (fencedMatcher.find()) {
            candidates.add(fencedMatcher.group(1).strip());
        }

        candidates.addAll(balancedJsonObjects(content));
        return candidates;
    }

    /**
     * Finds balanced top-level JSON objects in arbitrary text.
     * <p>
     * String- and escape-aware: braces inside a string value (a rationale quoting the student's code, for instance)
     * must not change the nesting depth, otherwise the scanner would cut the object short.
     *
     * @param text the text to scan
     * @return every balanced top-level object found, in order of appearance
     */
    private static List<String> balancedJsonObjects(String text) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                }
                else if (currentChar == '\\') {
                    escaped = true;
                }
                else if (currentChar == '"') {
                    inString = false;
                }
                continue;
            }

            if (currentChar == '"') {
                inString = true;
            }
            else if (currentChar == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            }
            else if (currentChar == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    /**
     * Parses one candidate, requiring both fields to be present and correctly typed.
     * <p>
     * Deliberately does not bind straight onto the {@code boolean} record component: a response missing
     * {@code malicious} would silently deserialise to {@code false}, i.e. a security tool would report "benign" for an
     * answer it never actually understood. A missing or blank {@code rationale} is rejected for the same reason: the
     * rationale is the only thing an instructor can review, and a verdict without one is not usable evidence.
     *
     * @param candidate the candidate text
     * @return the verdict, or empty if the candidate is not a valid verdict object
     */
    private static Optional<DeimosLlmResponse> tryParse(String candidate) {
        if (candidate.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(candidate);
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            JsonNode maliciousNode = root.get(MALICIOUS_FIELD);
            if (maliciousNode == null || !maliciousNode.isBoolean()) {
                return Optional.empty();
            }
            JsonNode rationaleNode = root.get(RATIONALE_FIELD);
            if (rationaleNode == null || !rationaleNode.isTextual() || rationaleNode.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new DeimosLlmResponse(maliciousNode.asBoolean(), rationaleNode.asText()));
        }
        catch (Exception ex) {
            log.debug("Deimos verdict candidate could not be parsed as JSON", ex);
            return Optional.empty();
        }
    }

    /**
     * Maps a failure surviving the SDK's transport retries onto a reportable failure type.
     *
     * @param exception the exception thrown by the chat client
     * @return the matching failure type
     */
    private static DeimosFailureType classifyCallFailure(Exception exception) {
        for (Throwable current = exception; current != null; current = current.getCause() == current ? null : current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return DeimosFailureType.LLM_TIMEOUT;
            }
            String message = current.getMessage();
            if (message != null) {
                String lowerCaseMessage = message.toLowerCase();
                if (lowerCaseMessage.contains("429") || lowerCaseMessage.contains("rate limit") || lowerCaseMessage.contains("too many requests")) {
                    return DeimosFailureType.LLM_RATE_LIMITED;
                }
                if (lowerCaseMessage.contains("timeout") || lowerCaseMessage.contains("timed out")) {
                    return DeimosFailureType.LLM_TIMEOUT;
                }
            }
            if (current instanceof IOException) {
                return DeimosFailureType.LLM_ERROR;
            }
        }
        return DeimosFailureType.LLM_ERROR;
    }
}
