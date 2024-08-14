package de.tum.in.www1.artemis.service.connectors.localci.dto;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;

import java.io.Serializable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildConfig(String buildScript, String dockerImage, String commitHashToBuild, String assignmentCommitHash, String testCommitHash, String branch,
        ProgrammingLanguage programmingLanguage, ProjectType projectType, boolean scaEnabled, boolean sequentialTestRunsEnabled, boolean testwiseCoverageEnabled,
        List<String> resultPaths, String workingDirectory, int timeoutSeconds) implements Serializable {

    @Override
    public String dockerImage() {
        // make sure to avoid whitespace issues
        return dockerImage.trim();
    }

    /**
     * Get the working directory for the build. If the checkout path is not valid, the default working directory is returned.
     *
     * @param checkoutPath the checkout path
     * @return the working directory
     */
    public static String getWorkingDirectory(String checkoutPath) {
        if (checkoutPath != null && !checkoutPath.isBlank() && isValidUnixPath(checkoutPath)) {
            return checkoutPath;
        }
        else {
            return LOCALCI_WORKING_DIRECTORY;
        }
    }

    private static boolean isValidUnixPath(String path) {
        String regex = "^(/([a-zA-Z0-9._-]+))*/?$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(path);

        return matcher.matches();
    }
}
