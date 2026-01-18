package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.ArtemisVersionDTO;
import de.tum.cit.aet.artemis.core.dto.CombinedSbomDTO;
import de.tum.cit.aet.artemis.core.dto.ComponentVulnerabilitiesDTO;
import de.tum.cit.aet.artemis.core.dto.SbomDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.ArtemisVersionService;
import de.tum.cit.aet.artemis.core.service.SbomService;
import de.tum.cit.aet.artemis.core.service.VulnerabilityService;

/**
 * REST controller for managing Software Bill of Materials (SBOM) as admin.
 * Provides endpoints for retrieving server and client dependency information.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/core/admin/")
public class AdminSbomResource {

    private static final Logger log = LoggerFactory.getLogger(AdminSbomResource.class);

    private final SbomService sbomService;

    private final VulnerabilityService vulnerabilityService;

    private final ArtemisVersionService artemisVersionService;

    public AdminSbomResource(SbomService sbomService, VulnerabilityService vulnerabilityService, ArtemisVersionService artemisVersionService) {
        this.sbomService = sbomService;
        this.vulnerabilityService = vulnerabilityService;
        this.artemisVersionService = artemisVersionService;
    }

    /**
     * GET /sbom: Get the combined SBOM containing both server and client dependencies.
     *
     * @return the ResponseEntity with status 200 (OK) and the combined SBOM in the body,
     *         or status 404 (Not Found) if no SBOMs are available
     */
    @GetMapping("sbom")
    public ResponseEntity<CombinedSbomDTO> getCombinedSbom() {
        log.debug("REST request to get combined SBOM");
        if (!sbomService.isSbomAvailable()) {
            return ResponseEntity.notFound().build();
        }
        CombinedSbomDTO combinedSbom = sbomService.getCombinedSbom();
        return ResponseEntity.ok(combinedSbom);
    }

    /**
     * GET /sbom/server: Get the server-side SBOM (Java/Gradle dependencies).
     *
     * @return the ResponseEntity with status 200 (OK) and the server SBOM in the body,
     *         or status 404 (Not Found) if not available
     */
    @GetMapping("sbom/server")
    public ResponseEntity<SbomDTO> getServerSbom() {
        log.debug("REST request to get server SBOM");
        SbomDTO serverSbom = sbomService.getServerSbom();
        if (serverSbom == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(serverSbom);
    }

    /**
     * GET /sbom/client: Get the client-side SBOM (npm dependencies).
     *
     * @return the ResponseEntity with status 200 (OK) and the client SBOM in the body,
     *         or status 404 (Not Found) if not available
     */
    @GetMapping("sbom/client")
    public ResponseEntity<SbomDTO> getClientSbom() {
        log.debug("REST request to get client SBOM");
        SbomDTO clientSbom = sbomService.getClientSbom();
        if (clientSbom == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(clientSbom);
    }

    /**
     * GET /sbom/vulnerabilities: Get vulnerability information for all SBOM components.
     * Results are cached for 24 hours to minimize external API calls.
     *
     * @return the ResponseEntity with status 200 (OK) and the vulnerability data in the body,
     *         or status 404 (Not Found) if no SBOMs are available
     */
    @GetMapping("sbom/vulnerabilities")
    public ResponseEntity<ComponentVulnerabilitiesDTO> getVulnerabilities() {
        log.debug("REST request to get SBOM vulnerabilities");
        if (!sbomService.isSbomAvailable()) {
            return ResponseEntity.notFound().build();
        }
        ComponentVulnerabilitiesDTO vulnerabilities = vulnerabilityService.getVulnerabilities();
        return ResponseEntity.ok(vulnerabilities);
    }

    /**
     * GET /sbom/vulnerabilities/refresh: Force refresh vulnerability information from OSV API.
     * This bypasses the cache and fetches fresh data.
     *
     * @return the ResponseEntity with status 200 (OK) and the fresh vulnerability data in the body,
     *         or status 404 (Not Found) if no SBOMs are available
     */
    @GetMapping("sbom/vulnerabilities/refresh")
    public ResponseEntity<ComponentVulnerabilitiesDTO> refreshVulnerabilities() {
        log.info("REST request to refresh SBOM vulnerabilities");
        if (!sbomService.isSbomAvailable()) {
            return ResponseEntity.notFound().build();
        }
        ComponentVulnerabilitiesDTO vulnerabilities = vulnerabilityService.refreshVulnerabilities();
        return ResponseEntity.ok(vulnerabilities);
    }

    /**
     * GET /sbom/version: Get Artemis version information including update status.
     * Checks GitHub releases for newer versions.
     *
     * @return the ResponseEntity with status 200 (OK) and version info in the body
     */
    @GetMapping("sbom/version")
    public ResponseEntity<ArtemisVersionDTO> getVersionInfo() {
        log.debug("REST request to get Artemis version info");
        ArtemisVersionDTO versionInfo = artemisVersionService.getVersionInfo();
        return ResponseEntity.ok(versionInfo);
    }
}
