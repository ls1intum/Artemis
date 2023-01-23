package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.ResourceLoaderService;

@Component
public class PipelineGroovyBuildPlanCreator {

    private static final String STATIC_CODE_ANALYSIS_REPORT_DIR = "staticCodeAnalysisReports";

    private static final String REPLACE_STATIC_CODE_ANALYSIS_SCRIPT = "#staticCodeAnalysisScript";

    private static final String REPLACE_STATIC_CODE_ANALYSIS_ENABLED = "#staticCodeAnalysisEnabled";

    private static final String REPLACE_TESTWISE_COVERAGE_MAVEN_PROFILE = "#testwiseCoverageMavenProfile";

    private static final String REPLACE_TESTWISE_COVERAGE_GRADLE_TASK = "#testwiseCoverageGradleTask";

    private static final String REPLACE_TESTWISE_COVERAGE_SCRIPT = "#testwiseCoverageScript";

    private final ResourceLoaderService resourceLoaderService;

    public PipelineGroovyBuildPlanCreator(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    public String getPipelineGroovyScript(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, boolean isStaticCodeAnalysisEnabled, boolean isSequentialRuns,
            boolean isTestwiseCoverageEnabled) {
        var pipelinePath = getResourcePath(programmingLanguage, projectType, isSequentialRuns);
        var replacements = getReplacements(programmingLanguage, projectType, isStaticCodeAnalysisEnabled, isTestwiseCoverageEnabled);
        String pipeline = resourceLoaderService.getResource(pipelinePath).toString();
        replacePipelineScriptParameters(pipeline, replacements);
        return pipeline;
    }

    private Map<String, String> getReplacements(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType, boolean isStaticCodeAnalysisEnabled,
            boolean isTestwiseCoverageAnalysisEnabled) {
        Map<String, String> replacements = new HashMap<>();
        // at the moment, only Java and Swift are supported
        replacements.put(REPLACE_STATIC_CODE_ANALYSIS_ENABLED, String.valueOf(isStaticCodeAnalysisEnabled));
        if (isStaticCodeAnalysisEnabled) {
            String staticCodeAnalysisScript = createStaticCodeAnalysisScript(programmingLanguage, projectType);
            replacements.put(REPLACE_STATIC_CODE_ANALYSIS_SCRIPT, staticCodeAnalysisScript);
        }
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

    private void replacePipelineScriptParameters(String pipelineGroovyScript, Map<String, String> variablesToReplace) {
        if (variablesToReplace != null) {
            for (final var replacement : variablesToReplace.entrySet()) {
                pipelineGroovyScript = pipelineGroovyScript.replace(replacement.getKey(), replacement.getValue());
            }
        }
    }
}
