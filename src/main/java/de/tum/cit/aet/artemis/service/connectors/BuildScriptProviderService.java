package de.tum.cit.aet.artemis.service.connectors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.service.connectors.aeolus.AeolusTemplateService;

/**
 * Service for providing build scripts for programming exercises
 * The scripts are loaded from the resources/templates/aeolus directory
 */
@Service
@Profile("aeolus | localci")
public class BuildScriptProviderService {

    private static final Logger log = LoggerFactory.getLogger(BuildScriptProviderService.class);

    private final ResourceLoaderService resourceLoaderService;

    private final Map<String, String> scriptCache = new ConcurrentHashMap<>();

    /**
     * Constructor for BuildScriptProvider, which loads all scripts into the cache to speed up retrieval
     * during the runtime of the application
     *
     * @param resourceLoaderService resourceLoaderService
     */
    public BuildScriptProviderService(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Loads all scripts from the resources/templates/aeolus directory into the cache.
     *
     * <p>
     * Windfiles are ignored since they are only used for the windfile and are cached in {@link AeolusTemplateService}.
     * Each script is read, processed, and stored in the {@code scriptCache}. Errors during loading are logged.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cacheOnBoot() {
        var resources = this.resourceLoaderService.getFileResources(Path.of("templates", "aeolus"));
        for (var resource : resources) {
            try {
                String filename = resource.getFilename();
                if (filename == null || filename.endsWith(".yaml")) {
                    continue;
                }
                String directory = resource.getURL().getPath().split("templates/aeolus/")[1].split("/")[0];
                String uniqueKey = directory + "_" + filename;
                byte[] fileContent = IOUtils.toByteArray(resource.getInputStream());
                String script = new String(fileContent, StandardCharsets.UTF_8);
                scriptCache.put(uniqueKey, script);
            }
            catch (IOException e) {
                log.error("Failed to load script {}", resource.getFilename(), e);
            }
        }
    }

    /**
     * Returns the file content of the template file for the given language and project type with the different options
     *
     * @param key the key of the script to be returned
     * @return the requested template as a bash script or windfile
     */
    public String getCachedScript(String key) {
        return scriptCache.getOrDefault(key, null);
    }

    /**
     * Returns the file content of the template file for the given language and project type with the different options
     *
     * @param programmingLanguage the programming language for which the template file should be returned
     * @param projectType         the project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis      whether the static analysis template should be used
     * @param sequentialRuns      whether the sequential runs template should be used
     * @param testCoverage        whether the test coverage template should be used
     * @return the requested template as a bash script
     * @throws IOException if the file does not exist
     */
    public String getScriptFor(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns, Boolean testCoverage)
            throws IOException {
        String templateFileName = buildTemplateName(projectType, staticAnalysis, sequentialRuns, testCoverage, "sh");
        String uniqueKey = programmingLanguage.name().toLowerCase() + "_" + templateFileName;
        if (scriptCache.containsKey(uniqueKey)) {
            log.debug("Returning cached script for {}", uniqueKey);
            return scriptCache.get(uniqueKey);
        }
        Resource fileResource = resourceLoaderService.getResource(Path.of("templates", "aeolus", programmingLanguage.name().toLowerCase(), templateFileName));
        if (!fileResource.exists()) {
            throw new IOException("File " + templateFileName + " not found");
        }
        byte[] fileContent = IOUtils.toByteArray(fileResource.getInputStream());
        String script = new String(fileContent, StandardCharsets.UTF_8);
        scriptCache.put(uniqueKey, script);
        log.debug("Caching script for {}", uniqueKey);
        return script;
    }

    /**
     * Returns the default build script for the given programming exercise
     *
     * @param exercise the programming exercise for which the build script should be provided
     * @return the script for the given programming exercise
     */
    public String getScriptFor(ProgrammingExercise exercise) {
        try {
            ProgrammingExerciseBuildConfig buildConfig = exercise.getBuildConfig();
            return getScriptFor(exercise.getProgrammingLanguage(), Optional.ofNullable(exercise.getProjectType()), exercise.isStaticCodeAnalysisEnabled(),
                    buildConfig.hasSequentialTestRuns(), buildConfig.isTestwiseCoverageEnabled());
        }
        catch (IOException e) {
            log.error("Failed to provide build script for programming exercise " + exercise.getId(), e);
        }
        return null;
    }

    /**
     * Returns the file content of the template file for the given language and project type with the different options
     *
     * @param projectType    The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis whether the static analysis template should be used
     * @param sequentialRuns whether the sequential runs template should be used
     * @param testCoverage   whether the test coverage template should be used
     * @param fileExtension  the file extension of the template file
     * @return The filename of the requested configuration
     */
    public String buildTemplateName(Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns, Boolean testCoverage, String fileExtension) {
        List<String> fileNameComponents = new ArrayList<>();

        if (ProjectType.MAVEN_BLACKBOX.equals(projectType.orElse(null))) {
            fileNameComponents.add("plain_" + projectType.get().name().toLowerCase());
        }
        else {
            fileNameComponents.add(projectType.map(Enum::name).orElse("default").toLowerCase());
        }

        if (staticAnalysis) {
            fileNameComponents.add("static");
        }
        if (sequentialRuns) {
            fileNameComponents.add("sequential");
        }
        if (testCoverage) {
            fileNameComponents.add("coverage");
        }
        return String.join("_", fileNameComponents) + "." + fileExtension;
    }
}
