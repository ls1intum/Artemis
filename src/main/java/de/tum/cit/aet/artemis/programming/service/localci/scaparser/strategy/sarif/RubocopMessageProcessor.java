package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptor;

/**
 * Strips the rule id from the message.
 */
public class RubocopMessageProcessor implements MessageProcessor {

    @Override
    public String processMessage(String message, @Nullable ReportingDescriptor rule) {
        if (rule == null) {
            return message;
        }

        String prefix = rule.id() + ": ";
        if (message.startsWith(prefix)) {
            return message.substring(prefix.length());
        }
        return message;
    }
}
