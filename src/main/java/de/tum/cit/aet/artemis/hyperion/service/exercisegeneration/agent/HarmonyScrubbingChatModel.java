package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

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
        return delegate.stream(prompt).map(HarmonyScrubbingChatModel::scrub);
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

    static String sanitizeHarmonyTokens(String content) {
        if (content == null || content.indexOf("<|") < 0) {
            return content;
        }
        return HARMONY_CONTROL_TOKEN.matcher(content).replaceAll("");
    }
}
