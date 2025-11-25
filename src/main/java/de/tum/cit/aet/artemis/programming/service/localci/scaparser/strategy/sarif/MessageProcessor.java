package de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.ReportingDescriptor;

/**
 * Modifies a given message depending on an optional rule descriptor.
 */
public interface MessageProcessor {

    String processMessage(String message, @Nullable ReportingDescriptor rule);
}
