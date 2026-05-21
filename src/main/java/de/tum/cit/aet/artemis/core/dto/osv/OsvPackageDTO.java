package de.tum.cit.aet.artemis.core.dto.osv;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing package information in an OSV query.
 * Can be specified either by purl (Package URL) or by name and ecosystem.
 *
 * @param name      the package name (e.g., "spring-core" or "@angular/core")
 * @param ecosystem the package ecosystem (e.g., "Maven", "npm", "PyPI", "Go")
 * @param purl      the Package URL following the purl specification
 * @see <a href="https://github.com/package-url/purl-spec">Package URL Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OsvPackageDTO(String name, String ecosystem, String purl) {
}
