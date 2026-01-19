package de.tum.cit.aet.artemis.core.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a component from a CycloneDX SBOM.
 *
 * @param group       the group/namespace of the component (e.g., org.springframework)
 * @param name        the name of the component
 * @param version     the version of the component
 * @param type        the type of component (library, application, framework, etc.)
 * @param purl        the Package URL for the component
 * @param licenses    the licenses associated with the component
 * @param description the description of the component
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SbomComponentDTO(String group, String name, String version, String type, String purl, List<String> licenses, String description) {
}
