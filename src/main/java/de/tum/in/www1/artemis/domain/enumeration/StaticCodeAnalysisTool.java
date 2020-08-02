package de.tum.in.www1.artemis.domain.enumeration;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumeration for supported static code analysis tools
 */
public enum StaticCodeAnalysisTool {

    SPOTBUGS(ProgrammingLanguage.JAVA, "spotbugs:spotbugs", "spotbugs.xml");

    private final ProgrammingLanguage language;

    private final String command;

    private final String filePattern;

    StaticCodeAnalysisTool(ProgrammingLanguage language, String command, String filePattern) {
        this.language = language;
        this.command = command;
        this.filePattern = filePattern;
    }

    public String getTask() {
        return this.command;
    }

    public String getFilePattern() {
        return this.filePattern;
    }

    public String getArtifactLabel() {
        return this.name().toLowerCase();
    }

    /**
     * Returns the artifact labels of all static code analysis tools.
     *
     * @return List of static code analysis tool artifact labels
     */
    public static List<String> getAllArtifactLabels() {
        List<String> artifactLabels = new ArrayList<>();
        for (var tool : StaticCodeAnalysisTool.values()) {
            artifactLabels.add(tool.getArtifactLabel());
        }
        return artifactLabels;
    }

    /**
     * Returns all static code analysis tools supporting the given programming language.
     *
     * @param language Programming language for which the static code analysis tools should be returned
     * @return List of static code analysis
     */
    public static List<StaticCodeAnalysisTool> getToolsForProgrammingLanguage(ProgrammingLanguage language) {
        List<StaticCodeAnalysisTool> tools = new ArrayList<>();
        for (var tool : StaticCodeAnalysisTool.values()) {
            if (tool.language == language) {
                tools.add(tool);
            }
        }
        return tools;
    }

    /**
     * Creates the build plan task command for static code analysis tools of a specific language.
     *
     * @param language Programming language for which the static code analysis command should be created
     * @return the command used to run static code analysis for a specific language
     */
    public static String createBuildPlanCommandForProgrammingLanguage(ProgrammingLanguage language) {
        StringBuilder commandBuilder = new StringBuilder();
        for (var tool : StaticCodeAnalysisTool.values()) {
            if (tool.language == language) {
                commandBuilder.append(tool.command);
            }
        }
        return commandBuilder.deleteCharAt(commandBuilder.length() - 1).toString();
    }
}
