package de.tum.cit.aet.artemis.localci.service.scaparser.strategy.sarif;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.localci.service.scaparser.format.sarif.ReportingDescriptor;

/**
 * Modifies a given message depending on an optional rule descriptor.
 */
public interface MessageProcessor {

    String processMessage(String message, @Nullable ReportingDescriptor rule);
}
