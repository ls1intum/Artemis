package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.ACTION_TRAILER;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.DEACTIVATE_ACTION;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.ENTITY_NAME;

import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;

/**
 * The state that an activation of the {@link SecurityFrameworkService} restores: how the annotations were set before the last deactivation.
 */
final class SecurityFrameworkRestorePoint {

    private static final Logger log = LoggerFactory.getLogger(SecurityFrameworkRestorePoint.class);

    private SecurityFrameworkRestorePoint() {
    }

    /**
     * Finds how the annotations were set before the most recent deactivation that actually switched something off, i.e. whose parent commit had at least
     * one active annotation. A test class is matched by its fully qualified name, so a class that replaced another one at the same path is not mistaken for
     * it; a class that did not exist then is switched on.
     *
     * @return whether the annotations of a test class, by its fully qualified name, should be switched on; all of them if the exercise was never deactivated
     *         by the service
     */
    static Predicate<String> annotationStatesBeforeLastDeactivation(Repository repository) {
        try (Git git = new Git(repository)) {
            for (RevCommit commit : git.log().call()) {
                if (commit.getParentCount() > 0 && commit.getFullMessage().contains(ACTION_TRAILER + DEACTIVATE_ACTION)) {
                    Map<String, Boolean> states = annotationStatesBefore(repository, commit);
                    if (states.containsValue(true)) {
                        return testClass -> states.getOrDefault(testClass, true);
                    }
                }
            }
            return _ -> true;
        }
        catch (GitAPIException | IOException e) {
            // Switching every annotation on could override test classes an instructor switched off on purpose, so nothing is changed.
            log.error("Could not read the history of {} to restore the annotations", repository.getLocalPath(), e);
            throw new BadRequestAlertException("The state before the last deactivation cannot be read from the history of the tests repository.", ENTITY_NAME,
                    "securityFrameworkRestorePointUnreadable");
        }
    }

    /**
     * @return how the annotations were set in the parent of the deactivation, by the fully qualified name of their test class
     */
    private static Map<String, Boolean> annotationStatesBefore(Repository repository, RevCommit deactivation) throws IOException {
        RevCommit parent;
        try (RevWalk revWalk = new RevWalk(repository)) {
            parent = revWalk.parseCommit(deactivation.getParent(0));
        }
        if (SecurityFrameworkCommitFiles.evaluate(repository, parent.getTree()).status() == SecurityActivationStatus.UNSUPPORTED) {
            throw new BadRequestAlertException("The state before the last deactivation cannot be restored, because the tests used Ares in an unsupported way then.", ENTITY_NAME,
                    "securityFrameworkRestorePointUnsupported");
        }
        return SecurityPolicyRepositoryFiles.annotationStates(SecurityFrameworkCommitFiles.javaSources(repository, parent.getTree()));
    }
}
