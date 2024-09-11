package de.tum.cit.aet.artemis.core.service.connectors.aeolus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.cit.aet.artemis.programming.web.localci.AeolusTemplateResource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.connectors.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.service.ResourceLoaderService;

/**
 * Handles the request to {@link AeolusTemplateResource} and Artemis internal
 * requests to fetch aeolus templates for programming exercises.
 */
@Service
@Profile("aeolus | localci")
public class AeolusTemplateService {

    private static final Logger log = LoggerFactory.getLogger(AeolusTemplateService.class);

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final Map<String, Windfile> templateCache = new ConcurrentHashMap<>();

    private final ResourceLoaderService resourceLoaderService;

    private final BuildScriptProviderService buildScriptProviderService;

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public AeolusTemplateService(ProgrammingLanguageConfiguration programmingLanguageConfiguration, ResourceLoaderService resourceLoaderService,
            BuildScriptProviderService buildScriptProviderService) {
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.resourceLoaderService = resourceLoaderService;
        this.buildScriptProviderService = buildScriptProviderService;
    }

    /**
     * Loads all YAML scripts from the "templates/aeolus" directory into the cache when the application is ready.
     *
     * <p>
     * Scripts are read, processed, and stored in the {@code templateCache}. Errors during loading are logged.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cacheOnBoot() {
        // load all scripts into the cache
        var resources = this.resourceLoaderService.getFileResources(Path.of("templates", "aeolus"));
        for (var resource : resources) {
            try {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".yaml")) {
                    continue;
                }
                String directory = resource.getURL().getPath().split("templates/aeolus/")[1].split("/")[0];
                Optional<ProjectType> optionalProjectType = extractProjectType(filename);
                String uniqueKey = directory + "_" + filename;
                byte[] fileContent = IOUtils.toByteArray(resource.getInputStream());
                String script = new String(fileContent, StandardCharsets.UTF_8);
                Windfile windfile = readWindfile(script);
                this.addInstanceVariablesToWindfile(windfile, ProgrammingLanguage.valueOf(directory.toUpperCase()), optionalProjectType);
                templateCache.put(uniqueKey, windfile);
            }
            catch (IOException | IllegalArgumentException e) {
                log.error("Failed to load windfile {}", resource.getFilename(), e);
            }
        }
    }

    /**
     * Reads a YAML representation of a Windfile from a string and deserializes it into a {@link Windfile} object.
     * This method leverages the Jackson {@code ObjectMapper} configured with {@code YAMLFactory} to parse
     * the YAML content directly. It registers a custom deserializer for handling instances of {@link Action}
     * to accommodate polymorphic deserialization based on the specific fields present in the YAML content.
     *
     * <p>
     * The method supports the dynamic instantiation of {@code Action} subclasses based on the content
     * of the YAML, enabling the flexible representation of different types of actions within the serialized
     * data. For instance, it can differentiate between {@code ScriptAction} and {@code PlatformAction}
     * based on specific identifying fields in the YAML structure.
     * </p>
     *
     * @param yaml The YAML string that represents the content of a Windfile.
     * @return A {@link Windfile} object deserialized from the provided YAML string.
     * @throws IOException If there is an error reading the YAML content or if the YAML is not
     *                         valid according to the Windfile structure or the custom action types expected.
     *                         This includes scenarios where the YAML content cannot be parsed or
     *                         does not match the expected schema.
     */
    private static Windfile readWindfile(String yaml) throws IOException {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Action.class, new ActionDeserializer());
        yamlMapper.registerModule(module);
        return yamlMapper.readValue(yaml, Windfile.class);
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
        if (programmingLanguage.equals(ProgrammingLanguage.JAVA) && projectType.isEmpty()) {
            // to be backwards compatible, we assume that java exercises without project type are plain maven projects
            projectType = Optional.of(ProjectType.PLAIN_MAVEN);
        }
        String templateFileName = buildScriptProviderService.buildTemplateName(projectType, staticAnalysis, sequentialRuns, testCoverage, "yaml");
        String uniqueKey = programmingLanguage.name().toLowerCase() + "_" + templateFileName;
        if (templateCache.containsKey(uniqueKey)) {
            return templateCache.get(uniqueKey);
        }
        String scriptCache = buildScriptProviderService.getCachedScript(uniqueKey);
        if (scriptCache == null) {
            log.error("No windfile found for key {}", uniqueKey);
            return null;
        }
        Windfile windfile = readWindfile(scriptCache);
        this.addInstanceVariablesToWindfile(windfile, programmingLanguage, projectType);
        templateCache.put(uniqueKey, windfile);
        return windfile;
    }

    /**
     * Extracts the project type from the filename, maven_blackbox is a special case
     *
     * @param filename the filename
     * @return the project type
     */
    private static Optional<ProjectType> extractProjectType(String filename) {
        String[] split = filename.replace(".yaml", "").split("_");
        String projectType = split[0];
        if (!projectType.equals("default")) {
            if (split.length > 2 && split[1].equals("maven") && split[2].equals("blackbox")) {
                return Optional.of(ProjectType.MAVEN_BLACKBOX);
            }
            return Optional.of(ProjectType.valueOf(projectType.toUpperCase()));
        }
        return Optional.empty();
    }

    /**
     * Returns the file content of the template file for the given exercise
     *
     * @param exercise the exercise for which the template file should be returned
     * @return the requested template as a {@link Windfile} object
     */
    public Windfile getDefaultWindfileFor(ProgrammingExercise exercise) {
        try {
            ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
            return getWindfileFor(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType()), exercise.isStaticCodeAnalysisEnabled(),
                    buildConfig.hasSequentialTestRuns(), buildConfig.isTestwiseCoverageEnabled());
        }
        catch (IOException e) {
            log.info("No windfile for the settings of exercise {}", exercise.getId(), e);
        }
        return null;
    }

    /**
     * Enhances a given {@code Windfile} instance by configuring its Docker settings based on the specified programming
     * language and project type. This method allows for dynamic configuration of Docker settings for Artemis instances,
     * enabling administrators to specify custom Docker images and flags without altering the core template.
     * <p>
     * If the project type is Xcode, which does not support Docker, the Docker configuration is explicitly set to {@code null}.
     *
     * @param windfile    the Windfile template to be updated with Docker configuration
     * @param language    the programming language used, which determines the Docker image and flags
     * @param projectType an optional specifying the project type; influences the Docker configuration
     */
    private void addInstanceVariablesToWindfile(Windfile windfile, ProgrammingLanguage language, Optional<ProjectType> projectType) {

        WindfileMetadata metadata = windfile.getMetadata();
        if (metadata == null) {
            metadata = new WindfileMetadata();
        }
        if (projectType.isPresent() && ProjectType.XCODE.equals(projectType.get())) {
            // xcode does not support docker
            metadata = new WindfileMetadata();
            windfile.setMetadata(metadata);
            return;
        }
        String image = programmingLanguageConfiguration.getImage(language, projectType);
        DockerConfig dockerConfig = new DockerConfig(image, null, null, programmingLanguageConfiguration.getDefaultDockerFlags());
        metadata = new WindfileMetadata(metadata.name(), metadata.id(), metadata.description(), metadata.author(), metadata.gitCredentials(), dockerConfig, metadata.resultHook(),
                metadata.resultHookCredentials());
        windfile.setMetadata(metadata);
    }
}
