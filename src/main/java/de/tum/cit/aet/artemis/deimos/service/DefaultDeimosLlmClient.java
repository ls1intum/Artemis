package de.tum.cit.aet.artemis.deimos.service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.openai.errors.OpenAIServiceException;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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

    /**
     * A classification stated anywhere in the response, also where no object can be recovered: prose with an unmatched
     * brace or quote derails the balanced-object scan, and must not hide a later contradicting verdict. Each character of
     * the key may be written literally or as a JSON Unicode escape.
     */
    private static final Pattern CLASSIFICATION_PATTERN = Pattern
            .compile("\"" + MALICIOUS_FIELD.chars().mapToObj(character -> "(?:%c|\\\\u(?i:%04x))".formatted(character, character)).collect(Collectors.joining())
                    + "\"\\s*+:\\s*+(true|false)\\b");

    private static final String CREDENTIALS_REJECTED_MESSAGE = "LLM endpoint rejected the credentials (HTTP %d): API key missing or wrong, or not authorised for this endpoint or model";

    /**
     * Upper bound on the causes inspected when classifying a failure. Real chains are a handful of wrappers deep.
     */
    private static final int MAX_CAUSE_DEPTH = 32;

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
            OptionalInt rejectedStatus = credentialRejectionStatus(ex);
            if (rejectedStatus.isPresent()) {
                // Fixed text and no cause: the SDK exception can carry the rejected request, and a cause attached here
                // would be printed in full wherever this exception is logged.
                throw new DeimosLlmException(DeimosFailureType.LLM_AUTHENTICATION_FAILED, CREDENTIALS_REJECTED_MESSAGE.formatted(rejectedStatus.getAsInt()));
            }
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
     * Candidates are grouped into tiers of decreasing confidence: the whole trimmed response, then the contents of any
     * fenced block, then any balanced JSON object found in the text. The first tier that yields a verdict decides, so a
     * model may refine its rationale inside a fence. Fences are not stripped indiscriminately, because the required JSON
     * is frequently the fenced content itself.
     * <p>
     * Within a tier, identical verdicts are collapsed, since the same object is usually recovered by more than one
     * candidate. Two <em>differing</em> verdicts in the same tier are rejected instead of resolved by position: nothing
     * in a bare sequence of objects says which one is the final answer, and taking the first would report a malicious
     * participation as benign. Verdicts with differing classifications are rejected across tiers as well, because a
     * fence does not mark the final answer either: a fenced object may be a quoted example followed by the actual
     * answer. Classifications stated outside a recoverable object count as well. The response is then counted as
     * {@code LLM_UNPARSEABLE} and can be re-run, rather than silently resolved in the dangerous direction.
     *
     * @param content the raw assistant response
     * @return the parsed verdict, or empty if no tier yielded exactly one valid verdict or the classifications differ
     */
    static Optional<DeimosLlmResponse> parseVerdict(String content) {
        List<Set<DeimosLlmResponse>> verdictsPerTier = new ArrayList<>();
        for (List<String> tier : candidateTiers(content)) {
            var distinctVerdicts = new LinkedHashSet<DeimosLlmResponse>();
            for (String candidate : tier) {
                tryParse(candidate).ifPresent(distinctVerdicts::add);
            }
            verdictsPerTier.add(distinctVerdicts);
        }
        Stream<Boolean> parsedClassifications = verdictsPerTier.stream().flatMap(Set::stream).map(DeimosLlmResponse::malicious);
        Stream<Boolean> statedClassifications = CLASSIFICATION_PATTERN.matcher(content).results().map(match -> Boolean.parseBoolean(match.group(1)));
        if (Stream.concat(parsedClassifications, statedClassifications).distinct().count() > 1) {
            log.warn("LLM response contained verdicts with differing classifications, rejecting it as ambiguous");
            return Optional.empty();
        }
        for (Set<DeimosLlmResponse> distinctVerdicts : verdictsPerTier) {
            if (distinctVerdicts.size() == 1) {
                return Optional.of(distinctVerdicts.iterator().next());
            }
            if (distinctVerdicts.size() > 1) {
                log.warn("LLM response contained {} differing verdicts at the same confidence level, rejecting it as ambiguous", distinctVerdicts.size());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static List<List<String>> candidateTiers(String content) {
        List<String> fencedBlocks = new ArrayList<>();
        Matcher fencedMatcher = FENCED_BLOCK_PATTERN.matcher(content);
        while (fencedMatcher.find()) {
            fencedBlocks.add(fencedMatcher.group(1).strip());
        }

        return List.of(List.of(content.strip()), fencedBlocks, balancedJsonObjects(content));
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
            if (rationaleNode == null || !rationaleNode.isString() || rationaleNode.asString().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new DeimosLlmResponse(maliciousNode.asBoolean(), rationaleNode.asString()));
        }
        catch (Exception ex) {
            log.debug("Deimos verdict candidate could not be parsed as JSON", ex);
            return Optional.empty();
        }
    }

    /**
     * Finds an HTTP 401 or 403 answer of the LLM endpoint anywhere in the cause chain.
     * <p>
     * Checked over the whole chain before any of the heuristics in {@link #classifyCallFailure}, so a wrapper whose
     * message happens to mention a timeout, or an outer {@link IOException}, cannot hide that the credentials were
     * rejected.
     *
     * @param exception the exception thrown by the chat client
     * @return the rejecting HTTP status, or empty if the credentials were not rejected
     */
    static OptionalInt credentialRejectionStatus(Throwable exception) {
        for (Throwable current : causeChain(exception)) {
            if (current instanceof OpenAIServiceException serviceException) {
                int status = serviceException.statusCode();
                if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                    return OptionalInt.of(status);
                }
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Returns the cause chain of an exception, bounded in length and stopping at the first repeated element, so a cyclic
     * chain cannot loop forever.
     *
     * @param exception the outermost exception
     * @return the exception followed by its causes
     */
    private static List<Throwable> causeChain(Throwable exception) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = exception; current != null && chain.size() < MAX_CAUSE_DEPTH && visited.add(current); current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }

    /**
     * Maps a failure surviving the SDK's transport retries onto a reportable failure type.
     *
     * @param exception the exception thrown by the chat client
     * @return the matching failure type
     */
    private static DeimosFailureType classifyCallFailure(Exception exception) {
        for (Throwable current : causeChain(exception)) {
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return DeimosFailureType.LLM_TIMEOUT;
            }
            String message = current.getMessage();
            if (message != null) {
                String lowerCaseMessage = message.toLowerCase(Locale.ROOT);
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
