package de.tum.in.www1.artemis.domain.enumeration;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumeration for supported static code analysis tools
 */
public enum StaticCodeAnalysisTool {

    SPOTBUGS(ProgrammingLanguage.JAVA);

    private final ProgrammingLanguage language;

    StaticCodeAnalysisTool(ProgrammingLanguage language) {
        this.language = language;
    }

    /**
     * Returns the artifact labels of all static code analysis tools.
     * String representations are used as the artifact labels.
     *
     * @return List of static code analysis tool artifact labels
     */
    public static List<String> getArtifactLabels() {
        List<String> artifactLabels = new ArrayList<>();
        for (var tool : StaticCodeAnalysisTool.values()) {
            artifactLabels.add(tool.toString());
        }
        return artifactLabels;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
