package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.aeolus.*;

/**
 * Service for retrieving aeolus template files based on the programming language, project type, and
 * the different options (static analysis, sequential runs, test coverage) as well as the default
 * image for the programming language and project type for the artemis instance.
 */
@RestController
@RequestMapping("/api/aeolus")
public class AeolusTemplateResource {

    private final Logger log = LoggerFactory.getLogger(AeolusTemplateResource.class);

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final ResourceLoaderService resourceLoaderService;

    public AeolusTemplateResource(ProgrammingLanguageConfiguration programmingLanguageConfiguration, ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    /**
     * GET /api/aeolus/templates/:language/:projectType : Get the aeolus template file with the given filename<br/>
     * GET /api/aeolus/templates/:language : Get the aeolus template file with the given filename
     * <p>
     * The windfile contains the default build plan configuration for new programming exercises.
     *
     * @param language       The programming language for which the aeolus template file should be returned
     * @param projectType    The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis Whether the static analysis template should be used
     * @param sequentialRuns Whether the sequential runs template should be used
     * @param testCoverage   Whether the test coverage template should be used
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping({ "/templates/{language}/{projectType}", "/templates/{language}" })
    @EnforceAtLeastEditor
    public ResponseEntity<String> getAeolusTemplate(@PathVariable ProgrammingLanguage language, @PathVariable Optional<ProjectType> projectType,
            @RequestParam(value = "staticAnalysis", defaultValue = "false") boolean staticAnalysis,
            @RequestParam(value = "sequentialRuns", defaultValue = "false") boolean sequentialRuns,
            @RequestParam(value = "testCoverage", defaultValue = "false") boolean testCoverage) {
        log.debug("REST request to get aeolus template for programming language {} and project type {}, static Analysis: {}, sequential Runs {}, testCoverage: {}", language,
                projectType, staticAnalysis, sequentialRuns, testCoverage);

        String languagePrefix = language.name().toLowerCase();
        String projectTypePrefix = projectType.map(type -> type.name().toLowerCase()).orElse("");

        return getAeolusTemplateFileContentWithResponse(languagePrefix, projectTypePrefix, staticAnalysis, sequentialRuns, testCoverage);
    }

    /**
     * Returns the file content of the template file for the given language and project type as JSON. The windfile in
     * the templates directory only specifies the actual actions, since an Artemis admin is allowed to change the
     * docker image and flags we intersect and inject the values for the particular instance before sending it to the
     * client.
     *
     * @param languagePrefix    The programming language for which the template file should be returned
     * @param projectTypePrefix The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis    Whether the static analysis template should be used
     * @param sequentialRuns    Whether the sequential runs template should be used
     * @param testCoverage      Whether the test coverage template should be used
     * @return The requested file, or 404 if the file doesn't exist
     */
    private ResponseEntity<String> getAeolusTemplateFileContentWithResponse(String languagePrefix, String projectTypePrefix, boolean staticAnalysis, boolean sequentialRuns,
            boolean testCoverage) {
        try {
            Optional<ProjectType> optionalProjectType = Optional.empty();
            if (!projectTypePrefix.isEmpty()) {
                optionalProjectType = Optional.of(ProjectType.valueOf(projectTypePrefix.toUpperCase()));
            }
            String fileName = buildAeolusTemplateName(optionalProjectType, staticAnalysis, sequentialRuns, testCoverage);
            Resource fileResource = resourceLoaderService.getResource(Path.of("templates", "aeolus", languagePrefix, fileName));
            if (!fileResource.exists()) {
                throw new IOException("File " + fileName + " not found");
            }
            byte[] fileContent = IOUtils.toByteArray(fileResource.getInputStream());
            String yaml = new String(fileContent, StandardCharsets.UTF_8);
            Windfile windfile = readWindfile(yaml);
            this.addInstanceVariablesToWindfile(windfile, ProgrammingLanguage.valueOf(languagePrefix.toUpperCase()), optionalProjectType);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(windfile);
            return new ResponseEntity<>(json, responseHeaders, HttpStatus.OK);
        }
        catch (IOException ex) {
            log.warn("Error when retrieving aeolus template file", ex);
            HttpHeaders responseHeaders = new HttpHeaders();
            return new ResponseEntity<>(null, responseHeaders, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Returns the file content of the template file for the given language and project type with the different options
     *
     * @param projectType    The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis whether the static analysis template should be used
     * @param sequentialRuns whether the sequential runs template should be used
     * @param testCoverage   whether the test coverage template should be used
     * @return The filename of the requested configuration
     */
    private String buildAeolusTemplateName(Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns, Boolean testCoverage) {
        List<String> fileNameComponents = new ArrayList<>();

        fileNameComponents.add(projectType.map(Enum::name).orElse("default").toLowerCase());

        if (staticAnalysis) {
            fileNameComponents.add("static");
        }
        if (sequentialRuns) {
            fileNameComponents.add("sequential");
        }
        if (testCoverage) {
            fileNameComponents.add("coverage");
        }
        return String.join("_", fileNameComponents) + ".yaml";
    }

    /**
     * Reads the yaml file and returns a Windfile object
     *
     * @param yaml the yaml file
     * @return the Windfile object
     * @throws IOException if the yaml file is not valid
     */
    private static Windfile readWindfile(String yaml) throws IOException {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Action.class, new ActionDeserializer());
        Gson gson = builder.create();
        return gson.fromJson(convertYamlToJson(yaml), Windfile.class);
    }

    /**
     * Converts a YAML string to a JSON string for easier communication with the client and usage with Gson
     *
     * @param yaml YAML string
     * @return JSON string
     */
    private static String convertYamlToJson(String yaml) throws JsonProcessingException {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        Object obj = yamlReader.readValue(yaml, Object.class);

        ObjectMapper jsonWriter = new ObjectMapper();
        return jsonWriter.writeValueAsString(obj);
    }

    /**
     * We take the template and add the default docker image and flags for the programming language and project type
     * of the artemis instance. This way, an Artemis admin can change the docker image and flags for the particular
     * instance without having to change the template file.
     *
     * @param windfile    the template file
     * @param language    the programming language
     * @param projectType the project type
     */
    private void addInstanceVariablesToWindfile(Windfile windfile, ProgrammingLanguage language, Optional<ProjectType> projectType) {

        if (windfile.getMetadata() == null) {
            windfile.setMetadata(new WindfileMetadata());
        }
        if (projectType.isPresent() && ProjectType.XCODE.equals(projectType.get())) {
            // xcode does not support docker
            windfile.getMetadata().setDocker(null);
            return;
        }
        if (windfile.getMetadata().getDocker() == null) {
            windfile.getMetadata().setDocker(new DockerConfig());
        }
        WindfileMetadata metadata = windfile.getMetadata();
        DockerConfig dockerConfig = windfile.getMetadata().getDocker();
        dockerConfig.setImage(programmingLanguageConfiguration.getImage(language, projectType));
        dockerConfig.setParameters(programmingLanguageConfiguration.getDefaultDockerFlags());
        metadata.setDocker(dockerConfig);
        windfile.setMetadata(metadata);
    }
}
