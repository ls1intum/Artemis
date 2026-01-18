package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.core.connector.OsvRequestMockProvider;
import de.tum.cit.aet.artemis.core.dto.ArtemisVersionDTO;
import de.tum.cit.aet.artemis.core.dto.CombinedSbomDTO;
import de.tum.cit.aet.artemis.core.dto.ComponentVulnerabilitiesDTO;
import de.tum.cit.aet.artemis.core.dto.SbomDTO;
import de.tum.cit.aet.artemis.core.dto.osv.OsvVulnerabilityDTO;
import de.tum.cit.aet.artemis.core.service.ArtemisVersionService;
import de.tum.cit.aet.artemis.core.web.admin.AdminSbomResource;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for {@link AdminSbomResource}.
 * These tests verify the REST endpoints for SBOM and vulnerability management.
 * Uses test SBOM files from src/test/resources/sbom/ and mocks OSV API calls.
 */
class AdminSbomResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "adminsbom";

    private static final String VULNERABILITY_CACHE_NAME = "sbomVulnerabilities";

    @Autowired
    private OsvRequestMockProvider osvRequestMockProvider;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private ArtemisVersionService artemisVersionService;

    @BeforeEach
    void setUp() {
        osvRequestMockProvider.enableMockingOfRequests();
        // Clear vulnerability cache to ensure each test starts fresh
        var cache = cacheManager.getCache(VULNERABILITY_CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    @AfterEach
    void tearDown() {
        osvRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getCombinedSbom_returnsOk_whenSbomAvailable() throws Exception {
        var result = request.get("/api/core/admin/sbom", HttpStatus.OK, CombinedSbomDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.server()).isNotNull();
        assertThat(result.client()).isNotNull();
        assertThat(result.server().bomFormat()).isEqualTo("CycloneDX");
        assertThat(result.server().specVersion()).isEqualTo("1.6");
        assertThat(result.server().components()).hasSize(2);
        assertThat(result.server().components().getFirst().name()).isEqualTo("spring-core");
        assertThat(result.client().bomFormat()).isEqualTo("CycloneDX");
        assertThat(result.client().components()).hasSize(2);
        assertThat(result.client().components().getFirst().name()).isEqualTo("core");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getCombinedSbom_returnsForbidden_whenNotAdmin() throws Exception {
        request.get("/api/core/admin/sbom", HttpStatus.FORBIDDEN, CombinedSbomDTO.class);
    }

    @Test
    void getCombinedSbom_returnsUnauthorized_whenNotLoggedIn() throws Exception {
        request.get("/api/core/admin/sbom", HttpStatus.UNAUTHORIZED, CombinedSbomDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getServerSbom_returnsOk_whenServerSbomAvailable() throws Exception {
        var result = request.get("/api/core/admin/sbom/server", HttpStatus.OK, SbomDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.bomFormat()).isEqualTo("CycloneDX");
        assertThat(result.specVersion()).isEqualTo("1.6");
        assertThat(result.serialNumber()).isEqualTo("urn:uuid:test-server-sbom-12345");
        assertThat(result.components()).hasSize(2);
        assertThat(result.components().getFirst().name()).isEqualTo("spring-core");
        assertThat(result.components().getFirst().group()).isEqualTo("org.springframework");
        assertThat(result.components().getFirst().version()).isEqualTo("6.0.0");
        assertThat(result.components().getFirst().purl()).isEqualTo("pkg:maven/org.springframework/spring-core@6.0.0");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getClientSbom_returnsOk_whenClientSbomAvailable() throws Exception {
        var result = request.get("/api/core/admin/sbom/client", HttpStatus.OK, SbomDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.bomFormat()).isEqualTo("CycloneDX");
        assertThat(result.specVersion()).isEqualTo("1.6");
        assertThat(result.serialNumber()).isEqualTo("urn:uuid:test-client-sbom-67890");
        assertThat(result.components()).hasSize(2);
        assertThat(result.components().getFirst().name()).isEqualTo("core");
        assertThat(result.components().getFirst().group()).isEqualTo("@angular");
        assertThat(result.components().getFirst().version()).isEqualTo("17.0.0");
        assertThat(result.components().getFirst().purl()).isEqualTo("pkg:npm/@angular/core@17.0.0");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getServerSbom_returnsForbidden_whenNotAdmin() throws Exception {
        request.get("/api/core/admin/sbom/server", HttpStatus.FORBIDDEN, SbomDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getClientSbom_returnsForbidden_whenNotAdmin() throws Exception {
        request.get("/api/core/admin/sbom/client", HttpStatus.FORBIDDEN, SbomDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getVulnerabilities_returnsOk_withNoVulnerabilities() throws Exception {
        // Mock OSV API to return no vulnerabilities for the 4 components (2 server + 2 client)
        osvRequestMockProvider.mockBatchQueryWithNoVulnerabilities(4);

        var result = request.get("/api/core/admin/sbom/vulnerabilities", HttpStatus.OK, ComponentVulnerabilitiesDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.totalVulnerabilities()).isZero();
        assertThat(result.criticalCount()).isZero();
        assertThat(result.highCount()).isZero();
        assertThat(result.mediumCount()).isZero();
        assertThat(result.lowCount()).isZero();
        assertThat(result.lastChecked()).isNotNull();

        osvRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getVulnerabilities_returnsOk_withVulnerability() throws Exception {
        String vulnId = "GHSA-test-1234-5678";

        // Create a vulnerability with HIGH severity that affects the installed version
        OsvVulnerabilityDTO.OsvDatabaseSpecificDTO dbSpecific = new OsvVulnerabilityDTO.OsvDatabaseSpecificDTO("HIGH");
        OsvVulnerabilityDTO.OsvAffectedDatabaseSpecificDTO affectedDbSpecific = new OsvVulnerabilityDTO.OsvAffectedDatabaseSpecificDTO("<= 6.0.0");
        OsvVulnerabilityDTO.OsvEventDTO introducedEvent = new OsvVulnerabilityDTO.OsvEventDTO("0", null, null);
        OsvVulnerabilityDTO.OsvEventDTO fixedEvent = new OsvVulnerabilityDTO.OsvEventDTO(null, "6.1.0", null);
        OsvVulnerabilityDTO.OsvRangeDTO range = new OsvVulnerabilityDTO.OsvRangeDTO("ECOSYSTEM", List.of(introducedEvent, fixedEvent));
        OsvVulnerabilityDTO.OsvAffectedDTO affected = new OsvVulnerabilityDTO.OsvAffectedDTO(null, List.of(range), affectedDbSpecific);
        OsvVulnerabilityDTO.OsvReferenceDTO reference = new OsvVulnerabilityDTO.OsvReferenceDTO("ADVISORY", "https://example.com/advisory");
        OsvVulnerabilityDTO fullVulnerability = new OsvVulnerabilityDTO(vulnId, "Test vulnerability summary", "Test vulnerability details", List.of("CVE-2024-1234"), null,
                List.of(affected), List.of(reference), dbSpecific);

        // Mock the batch query to return the vulnerability
        osvRequestMockProvider.mockBatchQueryWithVulnerability(vulnId);
        // Mock fetching full vulnerability details
        osvRequestMockProvider.mockVulnerabilityDetails(fullVulnerability);

        var result = request.get("/api/core/admin/sbom/vulnerabilities", HttpStatus.OK, ComponentVulnerabilitiesDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.totalVulnerabilities()).isEqualTo(1);
        assertThat(result.highCount()).isEqualTo(1);
        assertThat(result.lastChecked()).isNotNull();
        assertThat(result.vulnerabilities()).hasSize(1);
        assertThat(result.vulnerabilities().getFirst().vulnerabilities()).hasSize(1);
        assertThat(result.vulnerabilities().getFirst().vulnerabilities().getFirst().id()).isEqualTo(vulnId);
        assertThat(result.vulnerabilities().getFirst().vulnerabilities().getFirst().severity()).isEqualTo("HIGH");

        osvRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getVulnerabilities_returnsForbidden_whenNotAdmin() throws Exception {
        request.get("/api/core/admin/sbom/vulnerabilities", HttpStatus.FORBIDDEN, ComponentVulnerabilitiesDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void refreshVulnerabilities_returnsOk() throws Exception {
        // Mock OSV API to return no vulnerabilities
        osvRequestMockProvider.mockBatchQueryWithNoVulnerabilities(4);

        var result = request.get("/api/core/admin/sbom/vulnerabilities/refresh", HttpStatus.OK, ComponentVulnerabilitiesDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.totalVulnerabilities()).isZero();
        assertThat(result.lastChecked()).isNotNull();

        osvRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void refreshVulnerabilities_returnsForbidden_whenNotAdmin() throws Exception {
        request.get("/api/core/admin/sbom/vulnerabilities/refresh", HttpStatus.FORBIDDEN, ComponentVulnerabilitiesDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void sendVulnerabilityEmail_returnsOk_whenEmailSentSuccessfully() throws Exception {
        // Mock OSV API to return no vulnerabilities
        osvRequestMockProvider.mockBatchQueryWithNoVulnerabilities(4);

        // Mock version service to avoid GitHub API call
        ArtemisVersionDTO versionInfo = new ArtemisVersionDTO("7.8.0", "7.8.0", false, null, null, Instant.now().toString());
        doReturn(versionInfo).when(artemisVersionService).getVersionInfo();

        request.postWithoutLocation("/api/core/admin/sbom/vulnerabilities/send-email", null, HttpStatus.OK, null);

        osvRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void sendVulnerabilityEmail_returnsForbidden_whenNotAdmin() throws Exception {
        request.postWithoutLocation("/api/core/admin/sbom/vulnerabilities/send-email", null, HttpStatus.FORBIDDEN, null);
    }

    @Test
    void sendVulnerabilityEmail_returnsUnauthorized_whenNotLoggedIn() throws Exception {
        request.postWithoutLocation("/api/core/admin/sbom/vulnerabilities/send-email", null, HttpStatus.UNAUTHORIZED, null);
    }
}
