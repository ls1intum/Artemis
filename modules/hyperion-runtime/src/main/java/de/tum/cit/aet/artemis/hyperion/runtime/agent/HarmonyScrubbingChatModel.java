package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * A thin {@link ChatModel} decorator that strips gpt-oss "harmony" control tokens (e.g. {@code <|channel|>commentary}, {@code <|end|>}) the deployment occasionally leaks into the
 * assistant {@code content}. If such a token were replayed verbatim in the next request, the server's harmony chat template would re-parse it as structure — most visibly an
 * {@code "Unknown role: assistant<|channel|>commentary"} HTTP 400 that aborts a long, otherwise-healthy run. Removing the tokens keeps the conversation replayable.
 */
public class HarmonyScrubbingChatModel implements ChatModel {

    /** Matches a harmony / channel control token such as {@code <|channel|>} or {@code <|end|>}. The {@code >} exclusion keeps one token from spanning into the next. */
    static final Pattern HARMONY_CONTROL_TOKEN = Pattern.compile("<\\|[^|>]*\\|>");

    private static final Pattern INCOMPLETE_CONTROL_TOKEN = Pattern.compile("<\\|[^|>]*\\|?");

    private final ChatModel delegate;

    public HarmonyScrubbingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return scrub(delegate.call(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.defer(() -> {
            StreamScrubber scrubber = new StreamScrubber();
            return delegate.stream(prompt).map(scrubber::scrubChunk).concatWith(Flux.defer(scrubber::flush));
        });
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }

    static ChatResponse scrub(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return response;
        }
        boolean changed = false;
        List<Generation> rebuilt = new ArrayList<>(response.getResults().size());
        for (Generation generation : response.getResults()) {
            AssistantMessage output = generation.getOutput();
            String text = output == null ? null : output.getText();
            if (output != null && text != null && text.contains("<|")) {
                changed = true;
                AssistantMessage cleaned = AssistantMessage.builder().content(sanitizeHarmonyTokens(text)).properties(output.getMetadata()).media(output.getMedia())
                        .toolCalls(output.getToolCalls()).build();
                rebuilt.add(new Generation(cleaned, generation.getMetadata()));
            }
            else {
                rebuilt.add(generation);
            }
        }
        return changed ? new ChatResponse(rebuilt, response.getMetadata()) : response;
    }

    /** Holds only a possible token suffix, separately for each response choice and subscription. */
    private static final class StreamScrubber {

        private final List<String> pending = new ArrayList<>();

        ChatResponse scrubChunk(ChatResponse response) {
            List<Generation> results = new ArrayList<>();
            for (int i = 0; i < response.getResults().size(); i++) {
                while (pending.size() <= i) {
                    pending.add("");
                }
                Generation generation = response.getResults().get(i);
                AssistantMessage output = generation.getOutput();
                String text = sanitizeHarmonyTokens(pending.get(i) + (output.getText() == null ? "" : output.getText()));
                int suffix = text.lastIndexOf('<');
                boolean incomplete = suffix >= 0 && (text.substring(suffix).equals("<") || INCOMPLETE_CONTROL_TOKEN.matcher(text.substring(suffix)).matches());
                pending.set(i, incomplete ? text.substring(suffix) : "");
                String clean = incomplete ? text.substring(0, suffix) : text;
                results.add(
                        new Generation(AssistantMessage.builder().content(clean).properties(output.getMetadata()).media(output.getMedia()).toolCalls(output.getToolCalls()).build(),
                                generation.getMetadata()));
            }
            return new ChatResponse(results, response.getMetadata());
        }

        Flux<ChatResponse> flush() {
            if (pending.stream().allMatch(String::isEmpty)) {
                return Flux.empty();
            }
            // Incomplete delimiters are ordinary text. Do not duplicate provider usage or tool-call metadata when flushing them.
            return Flux.just(new ChatResponse(pending.stream().map(text -> new Generation(new AssistantMessage(text))).toList()));
        }
    }

    static String sanitizeHarmonyTokens(String content) {
        if (content == null || content.indexOf("<|") < 0) {
            return content;
        }
        return HARMONY_CONTROL_TOKEN.matcher(content).replaceAll("");
    }
}
