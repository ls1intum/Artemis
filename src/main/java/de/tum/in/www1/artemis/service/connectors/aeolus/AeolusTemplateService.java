package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.ResourceLoaderService;

/**
 * Handles the request to {@link de.tum.in.www1.artemis.web.rest.AeolusTemplateResource} and Artemis internal
 * requests to fetch aeolus templates for programming exercises.
 */
@Service
@Profile("aeolus | localci")
public class AeolusTemplateService {

    private final Logger LOGGER = LoggerFactory.getLogger(AeolusTemplateService.class);

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final ResourceLoaderService resourceLoaderService;

    private final Map<String, Windfile> templateCache = new ConcurrentHashMap<>();

    public AeolusTemplateService(ProgrammingLanguageConfiguration programmingLanguageConfiguration, ResourceLoaderService resourceLoaderService) {
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.resourceLoaderService = resourceLoaderService;
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
     * Returns the file content of the template file for the given language and project type with the different options
     *
     * @param programmingLanguage the programming language for which the template file should be returned
     * @param projectType         the project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis      whether the static analysis template should be used
     * @param sequentialRuns      whether the sequential runs template should be used
     * @param testCoverage        whether the test coverage template should be used
     * @return the requested template as a {@link Windfile} object
     * @throws IOException if the file does not exist
     */
    public Windfile getWindfileFor(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns, Boolean testCoverage)
            throws IOException {
        String templateFileName = buildAeolusTemplateName(projectType, staticAnalysis, sequentialRuns, testCoverage);
        String uniqueKey = programmingLanguage.name().toLowerCase() + "_" + templateFileName;
        if (templateCache.containsKey(uniqueKey)) {
            return templateCache.get(uniqueKey);
        }
        Resource fileResource = resourceLoaderService.getResource(Path.of("templates", "aeolus", programmingLanguage.name().toLowerCase(), templateFileName));
        if (!fileResource.exists()) {
            var existingFiles = Arrays.stream(resourceLoaderService.getResources(Path.of("templates", "aeolus"))).map(Resource::getFilename).toList();
            var allNames = new StringBuilder();
            for (var name : existingFiles) {
                allNames.append(name).append(", ");
            }
            throw new IOException("File " + Path.of("templates", "aeolus", programmingLanguage.name().toLowerCase(), templateFileName)
                    + " not found for settings: programming language: " + programmingLanguage.name() + ", project type: " + projectType.map(Enum::name).orElse("default")
                    + ", static analysis: " + staticAnalysis + ", sequential runs: " + sequentialRuns + ", test coverage: " + testCoverage + ". Existing files: " + allNames);
        }
        byte[] fileContent = IOUtils.toByteArray(fileResource.getInputStream());
        String yaml = new String(fileContent, StandardCharsets.UTF_8);
        Windfile windfile = readWindfile(yaml);
        this.addInstanceVariablesToWindfile(windfile, programmingLanguage, projectType);
        templateCache.put(uniqueKey, windfile);
        return windfile;
    }

    /**
     * Returns the file content of the template file for the given exercise
     *
     * @param exercise the exercise for which the template file should be returned
     * @return the requested template as a {@link Windfile} object
     */
    public Windfile getDefaultWindfileFor(ProgrammingExercise exercise) {
        try {
            return getWindfileFor(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType()), exercise.isStaticCodeAnalysisEnabled(),
                    exercise.hasSequentialTestRuns(), exercise.isTestwiseCoverageEnabled());
        }
        catch (IOException e) {
            LOGGER.info("No windfile for the settings of exercise {}", exercise.getId(), e);
        }
        return null;
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
}
