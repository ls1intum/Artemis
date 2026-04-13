package de.tum.cit.aet.artemis.programming.service.localci;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.web.localci.BuildPhasesTemplateResource;

/**
 * Handles the request to {@link BuildPhasesTemplateResource} and Artemis internal
 * requests to fetch template defaults for programming exercises.
 */
@Lazy
@Service
@Profile("localci")
public class BuildPhasesTemplateService {

    private static final Logger log = LoggerFactory.getLogger(BuildPhasesTemplateService.class);

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final Map<String, List<BuildPhaseDTO>> templateCache = new ConcurrentHashMap<>();

    private final ResourceLoaderService resourceLoaderService;

    private final BuildScriptProviderService buildScriptProviderService;

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public BuildPhasesTemplateService(ProgrammingLanguageConfiguration programmingLanguageConfiguration, ResourceLoaderService resourceLoaderService,
            BuildScriptProviderService buildScriptProviderService) {
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.resourceLoaderService = resourceLoaderService;
        this.buildScriptProviderService = buildScriptProviderService;
    }

    /**
     * Loads all YAML scripts from the "templates/phases" directory into the cache when the bean has been created.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     *
     * <p>
     * Scripts are read, processed, and stored in the {@code templateCache}. Errors during loading are logged.
     */
    @PostConstruct
    public void cacheOnBoot() {
        // load all scripts into the cache
        var resources = this.resourceLoaderService.getFileResources(Path.of("templates", "phases"));
        for (var resource : resources) {
            try {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".yamlString")) {
                    continue;
                }
                String directory = resource.getURL().getPath().split("templates/phases/")[1].split("/")[0];
                String uniqueKey = directory + "_" + filename;
                String yamlString = readResourceAsString(resource);
                List<BuildPhaseDTO> phases = readBuildPhases(yamlString);
                templateCache.put(uniqueKey, phases);
            }
            catch (IOException | IllegalArgumentException e) {
                log.error("Failed to load template {}", resource.getFilename(), e);
            }
        }
    }

    /**
     * Reads a YAML representation string and deserializes it into a list of {@link BuildPhaseDTO}.
     *
     * @param yaml the YAML string that represents a list of BuildPhaseDTOs
     * @return a list of {@link BuildPhaseDTO} objects deserialized from the provided YAML string
     * @throws IOException If there is an error reading the YAML content
     */
    private static List<BuildPhaseDTO> readBuildPhases(String yaml) throws IOException {
        SimpleModule module = new SimpleModule();
        yamlMapper.registerModule(module);

        CollectionType listOfBuildPhaseDTOType = yamlMapper.getTypeFactory().constructCollectionType(List.class, BuildPhaseDTO.class);

        return yamlMapper.readValue(yaml, listOfBuildPhaseDTOType);
    }

    /**
     * Returns the file content of the template file for the given language and project type with the different options
     *
     * @param programmingLanguage the programming language for which the template file should be returned
     * @param projectType         the project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis      whether the static analysis template should be used
     * @param sequentialRuns      whether the sequential runs template should be used
     * @return the requested template as a list of {@link BuildPhaseDTO} object
     * @throws IOException if the file does not exist
     */
    public List<BuildPhaseDTO> getBuildPlanPhasesFor(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns)
            throws IOException {
        if (programmingLanguage.equals(ProgrammingLanguage.JAVA) && projectType.isEmpty()) {
            // to be backwards compatible, we assume that java exercises without project type are plain maven projects
            projectType = Optional.of(ProjectType.PLAIN_MAVEN);
        }
        String templateFileName = buildScriptProviderService.buildTemplateName(projectType, staticAnalysis, sequentialRuns, "yaml");
        String uniqueKey = programmingLanguage.name().toLowerCase() + "_" + templateFileName;
        if (templateCache.containsKey(uniqueKey)) {
            return templateCache.get(uniqueKey);
        }

        String yamlString = null;
        try {
            Path resourcePath = Path.of("templates", "phases", programmingLanguage.name(), templateFileName);
            var resource = this.resourceLoaderService.getResource(resourcePath);
            if (resource != null && resource.exists()) {
                yamlString = readResourceAsString(resource);
            }
        }
        catch (IOException | IllegalArgumentException e) {
            log.error("No build plan phases found for key {}", uniqueKey);
        }

        if (yamlString == null) {
            return null;
        }

        List<BuildPhaseDTO> phases = readBuildPhases(yamlString);
        templateCache.put(uniqueKey, phases);
        return phases;
    }

    /**
     * Reads the given resource and returns its content as a string.
     *
     * @param resource the resource to read from
     * @return the content of the resource as a string
     * @throws IOException if there is an error reading the file content
     */
    private static String readResourceAsString(final Resource resource) throws IOException {
        byte[] fileContent;
        try (InputStream inputStream = resource.getInputStream()) {
            fileContent = inputStream.readAllBytes();
        }
        return new String(fileContent, StandardCharsets.UTF_8);
    }

    /**
     * Returns the file content of the template file for the given exercise
     *
     * @param exercise the exercise for which the template file should be returned
     * @return the requested template as a list of {@link BuildPhaseDTO} object
     */
    public List<BuildPhaseDTO> getDefaultBuildPlanPhasesFor(ProgrammingExercise exercise) {
        try {
            ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
            return getBuildPlanPhasesFor(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType()), exercise.isStaticCodeAnalysisEnabled(),
                    buildConfig.hasSequentialTestRuns());
        }
        catch (IOException e) {
            log.info("No build phases for the settings of exercise {}", exercise.getId(), e);
        }
        return null;
    }

    /**
     * Gets the default docker image based on the language and the project type specified.
     *
     * @param language    the programming language used, which determines the Docker image and flags
     * @param projectType an optional specifying the project type; influences the Docker configuration
     * @return the string of the docker image
     */
    private String getDefaultDockerImage(ProgrammingLanguage language, Optional<ProjectType> projectType) {
        if (projectType.isPresent() && ProjectType.XCODE.equals(projectType.get())) {
            // xcode does not support docker
            return null;
        }
        return programmingLanguageConfiguration.getImage(language, projectType);
    }

    /**
     * Gets the default docker image for the specified programming exercise.
     *
     * @param programmingExercise to get the default docker image for
     * @return the string of the docker image
     */
    public String getDefaultDockerImageFor(ProgrammingExercise programmingExercise) {
        return getDefaultDockerImageFor(programmingExercise.getProgrammingLanguage(), Optional.ofNullable(programmingExercise.getProjectType()));
    }

    /**
     * Gets the default docker image for the specified programming language and project type.
     *
     * @param programmingLanguage to get the image for
     * @param projectType         to get the image for
     * @return the string of the docker image
     */
    public String getDefaultDockerImageFor(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType) {
        if (programmingLanguage.equals(ProgrammingLanguage.JAVA) && projectType.isEmpty()) {
            // to be backwards compatible, we assume that java exercises without project type are plain maven projects
            projectType = Optional.of(ProjectType.PLAIN_MAVEN);
        }
        return getDefaultDockerImage(programmingLanguage, projectType);
    }
}
