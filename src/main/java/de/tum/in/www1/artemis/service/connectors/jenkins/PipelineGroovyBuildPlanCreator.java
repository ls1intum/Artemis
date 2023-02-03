package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.AbstractBuildPlanCreator;

@Component
public class PipelineGroovyBuildPlanCreator extends AbstractBuildPlanCreator {

    private static final String STATIC_CODE_ANALYSIS_REPORT_DIR = "staticCodeAnalysisReports";

    private static final String REPLACE_TESTWISE_COVERAGE_MAVEN_PROFILE = "#testwiseCoverageMavenProfile";

    private static final String REPLACE_TESTWISE_COVERAGE_GRADLE_TASK = "#testwiseCoverageGradleTask";

    private static final String REPLACE_TESTWISE_COVERAGE_SCRIPT = "#testwiseCoverageScript";

    private static final String REPLACE_DOCKER_IMAGE_NAME = "#dockerImage";

    private static final String REPLACE_DOCKER_ARGS = "#dockerArgs";

    public static final String REPLACE_IS_STATIC_CODE_ANALYSIS_ENABLED = "#isStaticCodeAnalysisEnabled";

    public static final String REPLACE_TESTWISE_COVERAGE = "#testWiseCoverage";

    private final ResourceLoaderService resourceLoaderService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    public PipelineGroovyBuildPlanCreator(BuildPlanRepository buildPlanRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ResourceLoaderService resourceLoaderService, ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        super(buildPlanRepository, programmingExerciseRepository);

        this.resourceLoaderService = resourceLoaderService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @Override
    protected String generateDefaultBuildPlan(ProgrammingExercise exercise) {
        final ProgrammingLanguage programmingLanguage = exercise.getProgrammingLanguage();
        final boolean isSequentialTestRuns = exercise.hasSequentialTestRuns();
        final Optional<ProjectType> projectType = Optional.ofNullable(exercise.getProjectType());

        final String[] pipelinePath = getResourcePath(programmingLanguage, projectType, isSequentialTestRuns);
        final Resource resource = resourceLoaderService.getResource(pipelinePath);
        final String pipelineScript = loadPipelineScript(resource);

        final boolean isStaticCodeAnalysisEnabled = exercise.isStaticCodeAnalysisEnabled();
        final boolean isTestwiseCoverageAnalysisEnabled = exercise.isTestwiseCoverageEnabled();
        final var replacements = getReplacements(programmingLanguage, projectType, isStaticCodeAnalysisEnabled, isTestwiseCoverageAnalysisEnabled);

        return replacePipelineScriptParameters(pipelineScript, replacements);
    }

    private String loadPipelineScript(final Resource resource) {
        try {
            return Files.readString(resource.getFile().toPath());
        }
        catch (IOException e) {
            throw new JenkinsException("Could not load pipeline skript definition.", e);
        }
    }

    private Map<String, String> getReplacements(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, boolean isStaticCodeAnalysisEnabled,
            boolean isTestwiseCoverageAnalysisEnabled) {
        Map<String, String> replacements = new HashMap<>();
        // at the moment, only Java and Swift are supported
        replacements.put(REPLACE_IS_STATIC_CODE_ANALYSIS_ENABLED, String.valueOf(isStaticCodeAnalysisEnabled));
        replacements.put(REPLACE_TESTWISE_COVERAGE, String.valueOf(isTestwiseCoverageAnalysisEnabled));

        // replace testwise coverage analysis placeholder
        String testwiseCoverageAnalysisMavenProfile = "";
        String testwiseCoverageAnalysisGradleTask = "";
        String testwiseCoverageAnalysisScript = "";
        if (isTestwiseCoverageAnalysisEnabled) {
            testwiseCoverageAnalysisMavenProfile = "-Pcoverage";
            testwiseCoverageAnalysisGradleTask = "tiaTests --run-all-tests";
            testwiseCoverageAnalysisScript = createTestwiseCoverageAnalysisScript(projectType);
        }
        replacements.put(REPLACE_TESTWISE_COVERAGE_MAVEN_PROFILE, testwiseCoverageAnalysisMavenProfile);
        replacements.put(REPLACE_TESTWISE_COVERAGE_GRADLE_TASK, testwiseCoverageAnalysisGradleTask);
        replacements.put(REPLACE_TESTWISE_COVERAGE_SCRIPT, testwiseCoverageAnalysisScript);
        replacements.put(REPLACE_DOCKER_IMAGE_NAME, programmingLanguageConfiguration.getImage(programmingLanguage, projectType));
        replacements.put(REPLACE_DOCKER_ARGS, String.join(" ", programmingLanguageConfiguration.getDefaultDockerFlags()));

        return replacements;
    }

    private String[] getResourcePath(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, boolean isSequentialRuns) {
        if (programmingLanguage == null) {
            throw new IllegalArgumentException("ProgrammingLanguage should not be null");
        }
        final var pipelineScriptFilename = "pipeline.groovy";
        final var regularOrSequentialDir = isSequentialRuns ? "sequentialRuns" : "regularRuns";
        final var programmingLanguageName = programmingLanguage.name().toLowerCase();

        Optional<String> projectTypeName;

        // Set a project type name in case the chosen Jenkinsfile also depend on the project type
        if (projectType.isPresent() && ProgrammingLanguage.C.equals(programmingLanguage)) {
            projectTypeName = Optional.of(projectType.get().name().toLowerCase(Locale.ROOT));
        }
        else if (projectType.isPresent() && projectType.get().isGradle()) {
            projectTypeName = Optional.of("gradle");
        }
        // Maven is also the project type for all other Java exercises (also if the project type is not present)
        else if (ProgrammingLanguage.JAVA.equals(programmingLanguage)) {
            projectTypeName = Optional.of("maven");
        }
        else {
            projectTypeName = Optional.empty();
        }

        return projectTypeName.map(name -> new String[] { "templates", "jenkins", programmingLanguageName, name, regularOrSequentialDir, pipelineScriptFilename })
                .orElseGet(() -> new String[] { "templates", "jenkins", programmingLanguageName, regularOrSequentialDir, pipelineScriptFilename });
    }

    // at the moment, only Java and Swift are supported
    private String createStaticCodeAnalysisScript(ProgrammingLanguage programmingLanguage, Optional<ProjectType> optionalProjectType) {
        StringBuilder script = new StringBuilder();
        String lineEnding = "&#xd;";
        // Delete a possible old directory for generated static code analysis reports and create a new one
        script.append("rm -rf ").append(STATIC_CODE_ANALYSIS_REPORT_DIR).append(lineEnding);
        script.append("mkdir ").append(STATIC_CODE_ANALYSIS_REPORT_DIR).append(lineEnding);
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            boolean isMaven = optionalProjectType.isEmpty() || optionalProjectType.get().isMaven();
            // Execute all static code analysis tools for Java
            if (isMaven) {
                script.append("mvn ");
                script.append(StaticCodeAnalysisTool.createBuildPlanCommandForProgrammingLanguage(programmingLanguage)).append(lineEnding);
            }
            else {
                script.append("./gradlew check -x test").append(lineEnding);
            }

            // Copy all static code analysis reports to new directory
            for (var tool : StaticCodeAnalysisTool.getToolsForProgrammingLanguage(programmingLanguage)) {
                script.append("cp target/").append(tool.getFilePattern()).append(" ").append(STATIC_CODE_ANALYSIS_REPORT_DIR).append(" || true").append(lineEnding);
            }
        }
        else if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            StaticCodeAnalysisTool tool = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(programmingLanguage).get(0);
            // Copy swiftlint configuration into student's repository
            script.append("cp .swiftlint.yml assignment || true").append(lineEnding);
            // Execute swiftlint within the student's repository and save the report into the sca directory
            // sh command: swiftlint lint assignment > <scaDir>/<result>.xml
            script.append("swiftlint lint assignment > ").append(STATIC_CODE_ANALYSIS_REPORT_DIR).append("/").append(tool.getFilePattern()).append(lineEnding);
        }
        return script.toString();
    }

    private String createTestwiseCoverageAnalysisScript(Optional<ProjectType> projectType) {
        StringBuilder script = new StringBuilder();
        String lineEnding = "&#xd;";

        script.append("success {").append(lineEnding);
        script.append("sh '''").append(lineEnding);
        script.append("rm -rf testwiseCoverageReport").append(lineEnding);
        script.append("mkdir testwiseCoverageReport").append(lineEnding);

        String reportDir;
        if (projectType.isPresent() && projectType.get().isGradle()) {
            reportDir = "build/reports/testwise-coverage/tiaTests/tiaTests.json";
        }
        else {
            reportDir = "target/tia/reports/*/*.json";
        }
        script.append("mv ").append(reportDir).append(" testwiseCoverageReport/").append(lineEnding);

        script.append("'''").append(lineEnding);
        script.append("}").append(lineEnding);

        return script.toString();
    }

    private String replacePipelineScriptParameters(String pipelineGroovyScript, Map<String, String> variablesToReplace) {
        if (variablesToReplace != null) {
            for (final var replacement : variablesToReplace.entrySet()) {
                pipelineGroovyScript = pipelineGroovyScript.replace(replacement.getKey(), replacement.getValue());
            }
        }
        return pipelineGroovyScript;
    }
}
