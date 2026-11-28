package de.tum.cit.aet.artemis.core.config;

import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Suppresses the warning that Spring AI's OpenAI chat model writes when an endpoint answers without a single choice, because the warning holds the whole
 * prompt: for Metis the code and the answers of a student. The callers report the empty answer themselves, without the prompt; Metis treats it as unusable
 * output.
 */
public class SpringAiPromptLogFilter extends TurboFilter {

    /** The logger of {@code org.springframework.ai.openai.OpenAiChatModel}, by name, so that this filter does not load the class. */
    static final String OPENAI_CHAT_MODEL_LOGGER = "org.springframework.ai.openai.OpenAiChatModel";

    /** The start of the warning; the prompt follows it. */
    static final String NO_CHOICES_WARNING = "No choices returned for prompt: ";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {
        // The chat model concatenates the prompt into the message before it calls the logger, so the start of the message is all there is to match
        if (format != null && OPENAI_CHAT_MODEL_LOGGER.equals(logger.getName()) && format.startsWith(NO_CHOICES_WARNING)) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
