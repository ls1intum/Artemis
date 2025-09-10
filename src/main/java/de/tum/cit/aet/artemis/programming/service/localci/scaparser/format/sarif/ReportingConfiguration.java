package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Information about a rule or notification that can be configured at runtime.
 *
 * @param level      Specifies the failure level for the report.
 * @param properties Key/value pairs that provide additional information about the reporting configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportingConfiguration(Level level, PropertyBag properties) {

    /**
     * Specifies the failure level for the report.
     */
    public Optional<Level> getOptionalLevel() {
        return Optional.ofNullable(level);
    }

    /**
     * Key/value pairs that provide additional information about the reporting configuration.
     */
    public Optional<PropertyBag> getOptionalProperties() {
        return Optional.ofNullable(properties);
    }
}
