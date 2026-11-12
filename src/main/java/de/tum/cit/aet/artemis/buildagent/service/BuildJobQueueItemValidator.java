package de.tum.cit.aet.artemis.buildagent.service;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;

/**
 * Checks the fields of a queued build job that the build agent turns into folder names and container paths, before the
 * job starts.
 *
 * <p>
 * The agent never uses the host of a repository URI from the queue: it rebuilds the clone address from its own
 * configuration, so only the project key and the repository slug of the URI take effect. They have to be plain names,
 * as do the directories the repositories are checked out to. Nothing else is restricted, on purpose: values such as the
 * docker network or the result paths are configured by instructors and administrators in ways the agent cannot judge, so
 * they are checked where they are configured.
 */
final class BuildJobQueueItemValidator {

    private static final Pattern PATH_SEPARATOR = Pattern.compile("[/\\\\]");

    private BuildJobQueueItemValidator() {
    }

    /**
     * @param buildJob the queued build job about to be executed
     * @throws LocalCIException if a repository URI or a checkout directory of the job is not a plain name
     */
    static void validate(BuildJobQueueItem buildJob) {
        RepositoryInfo repositoryInfo = buildJob.repositoryInfo();
        validateRepositoryUri(repositoryInfo.assignmentRepositoryUri(), true);
        validateRepositoryUri(repositoryInfo.testRepositoryUri(), true);
        validateRepositoryUri(repositoryInfo.solutionRepositoryUri(), false);
        String[] auxiliaryRepositoryUris = repositoryInfo.auxiliaryRepositoryUris();
        if (auxiliaryRepositoryUris != null) {
            for (String auxiliaryRepositoryUri : auxiliaryRepositoryUris) {
                validateRepositoryUri(auxiliaryRepositoryUri, true);
            }
        }
        String[] checkoutDirectories = repositoryInfo.auxiliaryRepositoryCheckoutDirectories();
        if (checkoutDirectories != null) {
            for (String checkoutDirectory : checkoutDirectories) {
                validateCheckoutPath(checkoutDirectory);
            }
        }
        var buildConfig = buildJob.buildConfig();
        for (String checkoutPath : List.of(StringUtils.defaultString(buildConfig.assignmentCheckoutPath()), StringUtils.defaultString(buildConfig.testCheckoutPath()),
                StringUtils.defaultString(buildConfig.solutionCheckoutPath()))) {
            validateCheckoutPath(checkoutPath);
        }
    }

    private static void validateRepositoryUri(String repositoryUri, boolean required) {
        if (repositoryUri == null) {
            if (required) {
                throw new LocalCIException("The build job does not contain all repository URIs");
            }
            return;
        }
        LocalVCRepositoryUri parsed;
        try {
            parsed = new LocalVCRepositoryUri(repositoryUri);
        }
        catch (RuntimeException e) {
            throw new LocalCIException("A repository URI of the build job is not valid", e);
        }
        validateName(parsed.getProjectKey());
        validateName(parsed.repositorySlug());
    }

    private static void validateName(String name) {
        if (StringUtils.isBlank(name) || name.equals(".") || name.equals("..") || PATH_SEPARATOR.matcher(name).find() || containsControlCharacter(name)) {
            throw new LocalCIException("A repository name of the build job is not valid");
        }
    }

    /**
     * Checkout paths are relative to the directory the repositories are placed in and may be nested, so only a segment
     * that leaves that directory is refused.
     */
    private static void validateCheckoutPath(String checkoutPath) {
        if (StringUtils.isBlank(checkoutPath)) {
            return;
        }
        if (containsControlCharacter(checkoutPath)) {
            throw new LocalCIException("A checkout path of the build job is not valid");
        }
        for (String segment : PATH_SEPARATOR.split(checkoutPath)) {
            if (segment.equals("..")) {
                throw new LocalCIException("A checkout path of the build job is not valid");
            }
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
