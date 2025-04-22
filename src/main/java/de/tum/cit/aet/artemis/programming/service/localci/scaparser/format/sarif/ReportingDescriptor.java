package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Metadata that describes a specific report produced by the tool, as part of the analysis it provides or its runtime reporting.
 *
 * @param id                   A stable, opaque identifier for the report.
 *                                 (Required)
 * @param deprecatedIds        An array of stable, opaque identifiers by which this report was known in some previous version of the analysis tool.
 * @param guid                 A unique identifier for the reporting descriptor in the form of a GUID.
 * @param deprecatedGuids      An array of unique identifies in the form of a GUID by which this report was known in some previous version of the analysis tool.
 * @param name                 A report identifier that is understandable to an end user.
 * @param deprecatedNames      An array of readable identifiers by which this report was known in some previous version of the analysis tool.
 * @param shortDescription     A message string or message format string rendered in multiple formats.
 * @param fullDescription      A message string or message format string rendered in multiple formats.
 * @param messageStrings       A set of name/value pairs with arbitrary names. Each value is a multiformatMessageString object, which holds message strings in plain text and
 *                                 (optionally) Markdown format.
 *                                 The strings can include placeholders, which can be used to construct a message in combination with an arbitrary number of additional string
 *                                 arguments.
 * @param defaultConfiguration Default reporting configuration information.
 * @param helpUri              A URI where the primary documentation for the report can be found.
 * @param help                 A message string or message format string rendered in multiple formats.
 * @param properties           Key/value pairs that provide additional information about the object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportingDescriptor(String id, Set<String> deprecatedIds, String guid, Set<String> deprecatedGuids, String name, Set<String> deprecatedNames,
        MultiformatMessageString shortDescription, MultiformatMessageString fullDescription, MessageStrings messageStrings, ReportingConfiguration defaultConfiguration,
        URI helpUri, MultiformatMessageString help, PropertyBag properties) {

    /**
     * Create a ReportingDescriptor from a rule id without any optional fields.
     *
     * @param id A stable, opaque identifier for the report.
     */
    public ReportingDescriptor(String id) {
        this(id, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * An array of stable, opaque identifiers by which this report was known in some previous version of the analysis tool.
     */
    public Optional<Set<String>> getOptionalDeprecatedIds() {
        return Optional.ofNullable(deprecatedIds);
    }

    /**
     * A unique identifier for the reporting descriptor in the form of a GUID.
     */
    public Optional<String> getOptionalGuid() {
        return Optional.ofNullable(guid);
    }

    /**
     * An array of unique identifies in the form of a GUID by which this report was known in some previous version of the analysis tool.
     */
    public Optional<Set<String>> getOptionalDeprecatedGuids() {
        return Optional.ofNullable(deprecatedGuids);
    }

    /**
     * A report identifier that is understandable to an end user.
     */
    public Optional<String> getOptionalName() {
        return Optional.ofNullable(name);
    }

    /**
     * An array of readable identifiers by which this report was known in some previous version of the analysis tool.
     */
    public Optional<Set<String>> getOptionalDeprecatedNames() {
        return Optional.ofNullable(deprecatedNames);
    }

    /**
     * A message string or message format string rendered in multiple formats.
     */
    public Optional<MultiformatMessageString> getOptionalShortDescription() {
        return Optional.ofNullable(shortDescription);
    }

    /**
     * A message string or message format string rendered in multiple formats.
     */
    public Optional<MultiformatMessageString> getOptionalFullDescription() {
        return Optional.ofNullable(fullDescription);
    }

    /**
     * A set of name/value pairs with arbitrary names. Each value is a multiformatMessageString object, which holds message strings in plain text and (optionally) Markdown format.
     * The strings can include placeholders, which can be used to construct a message in combination with an arbitrary number of additional string arguments.
     */
    public Optional<MessageStrings> getOptionalMessageStrings() {
        return Optional.ofNullable(messageStrings);
    }

    /**
     * Default reporting configuration information.
     */
    public Optional<ReportingConfiguration> getOptionalDefaultConfiguration() {
        return Optional.ofNullable(defaultConfiguration);
    }

    /**
     * A URI where the primary documentation for the report can be found.
     */
    public Optional<URI> getOptionalHelpUri() {
        return Optional.ofNullable(helpUri);
    }

    /**
     * A message string or message format string rendered in multiple formats.
     */
    public Optional<MultiformatMessageString> getOptionalHelp() {
        return Optional.ofNullable(help);
    }

    /**
     * Key/value pairs that provide additional information about the object.
     */
    public Optional<PropertyBag> getOptionalProperties() {
        return Optional.ofNullable(properties);
    }
}
