package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Information about how to locate a relevant reporting descriptor.
 *
 * @param id    The id of the descriptor.
 * @param index The index into an array of descriptors in toolComponent.ruleDescriptors, toolComponent.notificationDescriptors, or toolComponent.taxonomyDescriptors, depending on
 *                  context.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportingDescriptorReference(String id, Integer index) {

    public ReportingDescriptorReference(String id, Integer index) {
        this.id = id;
        this.index = Objects.requireNonNullElse(index, -1);
    }

    /**
     * The id of the descriptor.
     */
    public Optional<String> getOptionalId() {
        return Optional.ofNullable(id);
    }

    /**
     * The index into an array of descriptors in toolComponent.ruleDescriptors, toolComponent.notificationDescriptors, or toolComponent.taxonomyDescriptors, depending on context.
     */
    public Optional<Integer> getOptionalIndex() {
        return Optional.ofNullable(index);
    }
}
