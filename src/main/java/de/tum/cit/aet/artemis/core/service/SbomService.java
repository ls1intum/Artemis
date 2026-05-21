package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.dto.CombinedSbomDTO;
import de.tum.cit.aet.artemis.core.dto.SbomComponentDTO;
import de.tum.cit.aet.artemis.core.dto.SbomDTO;
import de.tum.cit.aet.artemis.core.dto.SbomMetadataDTO;

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

    private final ObjectMapper objectMapper;

    public SbomService(ObjectMapper objectMapper) {
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
            JsonNode root = objectMapper.readTree(inputStream);
            return parseCycloneDxSbom(root);
        }
        catch (IOException e) {
            log.error("Failed to read SBOM file: {}", path, e);
            return null;
        }
    }

    private SbomDTO parseCycloneDxSbom(JsonNode root) {
        String bomFormat = getTextValue(root, "bomFormat");
        String specVersion = getTextValue(root, "specVersion");
        String serialNumber = getTextValue(root, "serialNumber");
        int version = root.has("version") ? root.get("version").asInt() : 1;

        SbomMetadataDTO metadata = parseMetadata(root.get("metadata"));
        List<SbomComponentDTO> components = parseComponents(root.get("components"));

        return new SbomDTO(bomFormat, specVersion, serialNumber, version, metadata, components);
    }

    @Nullable
    private SbomMetadataDTO parseMetadata(JsonNode metadataNode) {
        if (metadataNode == null) {
            return null;
        }

        Instant timestamp = null;
        if (metadataNode.has("timestamp")) {
            try {
                timestamp = Instant.parse(metadataNode.get("timestamp").asText());
            }
            catch (Exception e) {
                log.debug("Failed to parse SBOM timestamp", e);
            }
        }

        String componentName = null;
        String componentVersion = null;
        JsonNode componentNode = metadataNode.get("component");
        if (componentNode != null) {
            componentName = getTextValue(componentNode, "name");
            componentVersion = getTextValue(componentNode, "version");
        }

        return new SbomMetadataDTO(timestamp, componentName, componentVersion);
    }

    private List<SbomComponentDTO> parseComponents(JsonNode componentsNode) {
        List<SbomComponentDTO> components = new ArrayList<>();
        if (componentsNode == null || !componentsNode.isArray()) {
            return components;
        }

        for (JsonNode componentNode : componentsNode) {
            SbomComponentDTO component = parseComponent(componentNode);
            components.add(component);
        }

        return components;
    }

    private SbomComponentDTO parseComponent(JsonNode node) {
        String group = getTextValue(node, "group");
        String name = getTextValue(node, "name");
        String version = getTextValue(node, "version");
        String type = getTextValue(node, "type");
        String purl = getTextValue(node, "purl");
        String description = getTextValue(node, "description");

        List<String> licenses = parseLicenses(node.get("licenses"));

        return new SbomComponentDTO(group, name, version, type, purl, licenses, description);
    }

    private List<String> parseLicenses(JsonNode licensesNode) {
        List<String> licenses = new ArrayList<>();
        if (licensesNode == null || !licensesNode.isArray()) {
            return licenses;
        }

        for (JsonNode licenseEntry : licensesNode) {
            JsonNode licenseNode = licenseEntry.get("license");
            if (licenseNode != null) {
                String licenseId = getTextValue(licenseNode, "id");
                if (licenseId != null) {
                    licenses.add(licenseId);
                }
                else {
                    String licenseName = getTextValue(licenseNode, "name");
                    if (licenseName != null) {
                        licenses.add(licenseName);
                    }
                }
            }
            // Handle expression format (e.g., "MIT OR Apache-2.0")
            String expression = getTextValue(licenseEntry, "expression");
            if (expression != null) {
                licenses.add(expression);
            }
        }

        return licenses;
    }

    @Nullable
    private String getTextValue(JsonNode node, String fieldName) {
        return Optional.ofNullable(node.get(fieldName)).filter(JsonNode::isTextual).map(JsonNode::asText).orElse(null);
    }
}
