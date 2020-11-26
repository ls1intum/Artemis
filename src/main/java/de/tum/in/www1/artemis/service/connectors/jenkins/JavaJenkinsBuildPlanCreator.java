package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.net.URL;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.ResourceLoaderService;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

@Profile("jenkins")
@Component
public class JavaJenkinsBuildPlanCreator extends BaseJenkinsBuildPlanCreator {

    private static final String STATIC_CODE_ANALYSIS_REPORT_DIR = "staticCodeAnalysisReports";

    public JavaJenkinsBuildPlanCreator(ResourceLoaderService resourceLoaderService, @Lazy JenkinsService jenkinsService) {
        super(resourceLoaderService, jenkinsService);
    }

    @Override
    protected String[] getResourcePath(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL, boolean isStaticCodeAnalysisEnabled) {
        final var buildPlan = isStaticCodeAnalysisEnabled ? "Jenkinsfile-staticCodeAnalysis" : "Jenkinsfile";
        return new String[] { "templates", "jenkins", "java", buildPlan };
    }

    @Override
    protected Map<String, String> getReplacements(ProgrammingLanguage programmingLanguage, URL testRepositoryURL, URL assignmentRepositoryURL,
            boolean isStaticCodeAnalysisEnabled) {
        Map<String, String> replacements = super.getReplacements(programmingLanguage, testRepositoryURL, assignmentRepositoryURL, isStaticCodeAnalysisEnabled);
        if (isStaticCodeAnalysisEnabled) {
            String staticCodeAnalysisScript = createStaticCodeAnalysisScript();
            replacements.put(REPLACE_STATIC_CODE_ANALYSIS_SCRIPT, staticCodeAnalysisScript);
        }
        return replacements;
    }

    private String createStaticCodeAnalysisScript() {
        StringBuilder script = new StringBuilder();
        String lineEnding = "&#xd;";
        script.append("mvn ");
        // Execute all static code analysis tools for Java
        script.append(StaticCodeAnalysisTool.createBuildPlanCommandForProgrammingLanguage(ProgrammingLanguage.JAVA)).append(lineEnding);
        // Make directory for generated static code analysis reports
        script.append("mkdir ").append(STATIC_CODE_ANALYSIS_REPORT_DIR).append(lineEnding);
        // Copy all static code analysis reports to new directory
        for (var tool : StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.JAVA)) {
            script.append("cp target/").append(tool.getFilePattern()).append(" ").append(STATIC_CODE_ANALYSIS_REPORT_DIR).append(lineEnding);
        }
        return script.toString();
    }
}
