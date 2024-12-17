package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A component, such as a plug-in or the driver, of the analysis tool that was run.
 *
 * @param name                 The name of the tool component.
 *                                 (Required)
 * @param globalMessageStrings A dictionary, each of whose keys is a resource identifier and each of whose values is a multiformatMessageString object, which holds message strings
 *                                 in plain text and (optionally) Markdown format. The strings can include placeholders, which can be used to construct a message in combination
 *                                 with an arbitrary number of additional string arguments.
 * @param rules                An array of reportingDescriptor objects relevant to the analysis performed by the tool component.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolComponent(String name, GlobalMessageStrings globalMessageStrings, List<ReportingDescriptor> rules) {

    /**
     * A dictionary, each of whose keys is a resource identifier and each of whose values is a multiformatMessageString object, which holds message strings in plain text and
     * (optionally) Markdown format. The strings can include placeholders, which can be used to construct a message in combination with an arbitrary number of additional string
     * arguments.
     */
    public Optional<GlobalMessageStrings> getOptionalGlobalMessageStrings() {
        return Optional.ofNullable(globalMessageStrings);
    }

    /**
     * An array of reportingDescriptor objects relevant to the analysis performed by the tool component.
     */
    public Optional<List<ReportingDescriptor>> getOptionalRules() {
        return Optional.ofNullable(rules);
    }

}
