package de.tum.in.www1.artemis.service.connectors;

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

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.ResourceLoaderService;

@Service
@Profile("aeolus | localci")
public class BuildScriptProvider {

    private final Logger LOGGER = LoggerFactory.getLogger(BuildScriptProvider.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ResourceLoaderService resourceLoaderService;

    private final Map<String, String> scriptCache = new ConcurrentHashMap<>();

    public BuildScriptProvider(ProgrammingExerciseRepository programmingExerciseRepository, ResourceLoaderService resourceLoaderService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.resourceLoaderService = resourceLoaderService;
        // load all scripts into the cache
        cacheOnBoot();
    }

    private void cacheOnBoot() {
        var resources = this.resourceLoaderService.getResources(Path.of("templates", "aeolus"));
        for (var resource : resources) {
            try {
                String filename = resource.getFilename();
                String directory = resource.getURL().getPath().split("templates/aeolus/")[1].split("/")[0];
                String uniqueKey = directory + "_" + filename;
                byte[] fileContent = IOUtils.toByteArray(resource.getInputStream());
                String script = new String(fileContent, StandardCharsets.UTF_8);
                scriptCache.put(uniqueKey, script);
            }
            catch (IOException e) {
                LOGGER.error("Failed to load script {}", resource.getFilename(), e);
            }
        }
    }

    /**
     * Stores the given script in the database for the given programming exercise
     *
     * @param programmingExercise the programming exercise for which the script should be stored
     * @param script              the script to store
     */
    public void storeBuildScriptInDatabase(ProgrammingExercise programmingExercise, String script) {
        programmingExercise.setBuildScript(script);
        programmingExerciseRepository.save(programmingExercise);
    }

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
            LOGGER.debug("Returning cached script for {}", uniqueKey);
            return scriptCache.get(uniqueKey);
        }
        Resource fileResource = resourceLoaderService.getResource(Path.of("templates", "aeolus", programmingLanguage.name().toLowerCase(), templateFileName));
        if (!fileResource.exists()) {
            throw new IOException("File " + templateFileName + " not found");
        }
        byte[] fileContent = IOUtils.toByteArray(fileResource.getInputStream());
        String script = new String(fileContent, StandardCharsets.UTF_8);
        scriptCache.put(uniqueKey, script);
        LOGGER.debug("Caching script for {}", uniqueKey);
        return script;
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
    public String buildTemplateName(Optional<ProjectType> projectType, Boolean staticAnalysis, Boolean sequentialRuns, Boolean testCoverage, String fileExtension) {
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
        return String.join("_", fileNameComponents) + "." + fileExtension;
    }
}
