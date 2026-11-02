package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.admin.dto.CombinedSbomDTO;
import de.tum.cit.aet.artemis.admin.dto.SbomComponentDTO;
import de.tum.cit.aet.artemis.admin.dto.SbomDTO;
import de.tum.cit.aet.artemis.admin.dto.SbomMetadataDTO;

/**
 * Service for reading and parsing Software Bill of Materials (SBOM) files.
 * Supports CycloneDX format for both server (Java/Gradle) and client (npm) dependencies.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class SbomService {

    private static final Logger log = LoggerFactory.getLogger(SbomService.class);

    private static final String SERVER_SBOM_PATH = "sbom/server-sbom.json";

    private static final String CLIENT_SBOM_PATH = "sbom/client-sbom.json";

    private final JsonMapper objectMapper;

    public SbomService(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves the combined SBOM containing both server and client dependencies.
     *
     * @return the combined SBOM DTO with server and client SBOMs
     */
    public CombinedSbomDTO getCombinedSbom() {
        SbomDTO serverSbom = getServerSbom();
        SbomDTO clientSbom = getClientSbom();
        return new CombinedSbomDTO(serverSbom, clientSbom);
    }

    /**
     * Retrieves the server-side SBOM (Java/Gradle dependencies).
     *
     * @return the server SBOM DTO, or null if not available
     */
    @Nullable
    public SbomDTO getServerSbom() {
        return parseSbomFile(SERVER_SBOM_PATH);
    }

    /**
     * Retrieves the client-side SBOM (npm dependencies).
     *
     * @return the client SBOM DTO, or null if not available
     */
    @Nullable
    public SbomDTO getClientSbom() {
        return parseSbomFile(CLIENT_SBOM_PATH);
    }

    /**
     * Checks if any SBOM files are available.
     *
     * @return true if at least one SBOM file exists
     */
    public boolean isSbomAvailable() {
        return isResourceAvailable(SERVER_SBOM_PATH) || isResourceAvailable(CLIENT_SBOM_PATH);
    }

    private boolean isResourceAvailable(String path) {
        Resource resource = new ClassPathResource(path);
        return resource.exists();
    }

    @Nullable
    private SbomDTO parseSbomFile(String path) {
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.debug("SBOM file not found: {}", path);
            return null;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            CycloneDxDocument document = objectMapper.readValue(inputStream, CycloneDxDocument.class);
            return parseCycloneDxSbom(document);
        }
        // Jackson 3 exceptions are unchecked and no longer extend IOException, so a malformed payload no longer
        // arrives here on its own — JacksonException has to be named explicitly.
        catch (IOException | JacksonException e) {
            log.error("Failed to read SBOM file: {}", path, e);
            return null;
        }
    }

    /* Multiple records that model the part of a CycloneDX document that is used here, for Jackson deserialization */

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CycloneDxDocument(String bomFormat, String specVersion, String serialNumber, Integer version, CycloneDxMetadata metadata, List<CycloneDxComponent> components) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CycloneDxMetadata(String timestamp, CycloneDxComponent component) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CycloneDxComponent(String group, String name, String version, String type, String purl, String description, List<CycloneDxLicenseEntry> licenses) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CycloneDxLicenseEntry(CycloneDxLicense license, String expression) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CycloneDxLicense(String id, String name) {
    }

    private SbomDTO parseCycloneDxSbom(CycloneDxDocument document) {
        int version = document.version() != null ? document.version() : 1;

        SbomMetadataDTO metadata = parseMetadata(document.metadata());
        List<SbomComponentDTO> components = parseComponents(document.components());

        return new SbomDTO(document.bomFormat(), document.specVersion(), document.serialNumber(), version, metadata, components);
    }

    @Nullable
    private SbomMetadataDTO parseMetadata(@Nullable CycloneDxMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        Instant timestamp = null;
        if (metadata.timestamp() != null) {
            try {
                timestamp = Instant.parse(metadata.timestamp());
            }
            catch (Exception e) {
                log.debug("Failed to parse SBOM timestamp", e);
            }
        }

        String componentName = null;
        String componentVersion = null;
        if (metadata.component() != null) {
            componentName = metadata.component().name();
            componentVersion = metadata.component().version();
        }

        return new SbomMetadataDTO(timestamp, componentName, componentVersion);
    }

    private List<SbomComponentDTO> parseComponents(@Nullable List<CycloneDxComponent> componentInputs) {
        List<SbomComponentDTO> components = new ArrayList<>();
        if (componentInputs == null) {
            return components;
        }

        for (CycloneDxComponent componentInput : componentInputs) {
            if (componentInput != null) {
                components.add(parseComponent(componentInput));
            }
        }

        return components;
    }

    private SbomComponentDTO parseComponent(CycloneDxComponent component) {
        List<String> licenses = parseLicenses(component.licenses());

        return new SbomComponentDTO(component.group(), component.name(), component.version(), component.type(), component.purl(), licenses, component.description());
    }

    private List<String> parseLicenses(@Nullable List<CycloneDxLicenseEntry> licenseEntries) {
        List<String> licenses = new ArrayList<>();
        if (licenseEntries == null) {
            return licenses;
        }

        for (CycloneDxLicenseEntry licenseEntry : licenseEntries) {
            if (licenseEntry == null) {
                continue;
            }
            CycloneDxLicense license = licenseEntry.license();
            if (license != null) {
                if (license.id() != null) {
                    licenses.add(license.id());
                }
                else if (license.name() != null) {
                    licenses.add(license.name());
                }
            }
            // Handle expression format (e.g., "MIT OR Apache-2.0")
            if (licenseEntry.expression() != null) {
                licenses.add(licenseEntry.expression());
            }
        }

        return licenses;
    }
}
