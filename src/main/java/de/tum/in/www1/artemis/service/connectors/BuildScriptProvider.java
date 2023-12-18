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

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    private final Map<String, String> scriptCache = new ConcurrentHashMap<>();

    public BuildScriptProvider(ProgrammingExerciseRepository programmingExerciseRepository, ResourceLoaderService resourceLoaderService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.resourceLoaderService = resourceLoaderService;
    }

    public String getTemplateScriptFor(ProgrammingExercise programmingExercise) throws IOException {
        return getScriptFor(programmingExercise.getProgrammingLanguage(), Optional.ofNullable(programmingExercise.getProjectType()),
                programmingExercise.isStaticCodeAnalysisEnabled(), programmingExercise.hasSequentialTestRuns(), programmingExercise.isTestwiseCoverageEnabled());
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

    /**
     * Returns the file content of the template file for the given language and project type with the different options
     * If a build script has already been generated for this exercise, it will be returned from the
     * database instead of returning the template.
     *
     * @param programmingExercise the programming exercise for which the script should be returned
     * @return the requested template as a bash script
     */
    public String getScriptFor(ProgrammingExercise programmingExercise) {
        var buildScript = programmingExercise.getBuildScript();
        if (buildScript != null) {
            return buildScript;
        }
        try {
            buildScript = this.getScriptFor(programmingExercise.getProgrammingLanguage(), Optional.ofNullable(programmingExercise.getProjectType()),
                    programmingExercise.isStaticCodeAnalysisEnabled(), programmingExercise.hasSequentialTestRuns(), programmingExercise.isTestwiseCoverageEnabled());
            this.storeBuildScriptInDatabase(programmingExercise, buildScript);
            return buildScript;
        }
        catch (IOException e) {
            LOGGER.error("Could not load template for programming exercise " + programmingExercise.getId(), e);
        }
        return null;
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
        if (templateCache.containsKey(uniqueKey)) {
            return templateCache.get(uniqueKey);
        }
        Resource fileResource = resourceLoaderService.getResource(Path.of("templates", "aeolus", programmingLanguage.name().toLowerCase(), templateFileName));
        if (!fileResource.exists()) {
            throw new IOException("File " + templateFileName + " not found");
        }
        byte[] fileContent = IOUtils.toByteArray(fileResource.getInputStream());
        String script = new String(fileContent, StandardCharsets.UTF_8);
        templateCache.put(uniqueKey, script);
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
