package de.tum.cit.aet.artemis.core.dto.osv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for a single package query in the OSV batch request.
 * Represents either a purl-based query or a package name/ecosystem/version query.
 *
 * @param packageInfo the package information (name, ecosystem, purl)
 * @param version     the version to check (required when using name/ecosystem)
 * @see <a href="https://osv.dev/docs/#tag/api/operation/OSV_QueryAffected">OSV API Documentation</a>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OsvQueryDTO(@JsonProperty("package") OsvPackageDTO packageInfo, String version) {

    /**
     * Creates a query using a Package URL (purl).
     *
     * @param purl the Package URL (e.g., "pkg:maven/org.springframework/spring-core@6.0.0")
     * @return a new OsvQueryDTO configured for purl-based lookup
     */
    public static OsvQueryDTO fromPurl(String purl) {
        return new OsvQueryDTO(new OsvPackageDTO(null, null, purl), null);
    }

    /**
     * Creates a query using package name, ecosystem, and version.
     *
     * @param name      the package name (e.g., "spring-core" or "org.springframework:spring-core")
     * @param ecosystem the package ecosystem (e.g., "Maven", "npm", "PyPI")
     * @param version   the package version to check
     * @return a new OsvQueryDTO configured for name/ecosystem/version lookup
     */
    public static OsvQueryDTO fromNameAndVersion(String name, String ecosystem, String version) {
        return new OsvQueryDTO(new OsvPackageDTO(name, ecosystem, null), version);
    }
}
