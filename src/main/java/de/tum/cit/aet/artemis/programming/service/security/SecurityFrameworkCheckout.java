package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.ENTITY_NAME;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * Checks of the private checkout of an exercise-tests repository in which the {@link SecurityFrameworkService} applies a change, before it reads the checkout
 * and before it commits the change.
 */
final class SecurityFrameworkCheckout {

    private static final Logger log = LoggerFactory.getLogger(SecurityFrameworkCheckout.class);

    private SecurityFrameworkCheckout() {
    }

    /**
     * The Security Framework reads and rewrites files of the checkout, and both follow symbolic links. A link committed to the tests repository could
     * therefore make a change read or modify a file outside the checkout with the server's permissions, so a repository with links is not changed at all.
     * A checkout without symbolic links writes a committed link as a plain file that holds its target, and rewriting that file would change where the link
     * points, so the links are also read from the index.
     */
    static void rejectSymbolicLinks(ProgrammingExercise exercise, Repository repository) throws IOException {
        Set<String> linkSet = new TreeSet<>(SecurityPolicyRepositoryFiles.findSymbolicLinks(repository.getLocalPath()));
        DirCache index = repository.readDirCache();
        for (int i = 0; i < index.getEntryCount(); i++) {
            DirCacheEntry entry = index.getEntry(i);
            if (FileMode.SYMLINK.equals(entry.getRawMode())) {
                linkSet.add(entry.getPathString());
            }
        }
        List<String> links = List.copyOf(linkSet);
        if (!links.isEmpty()) {
            log.warn("Refusing to change the tests repository of exercise {}, because it contains symbolic links: {}", exercise.getId(), links);
            throw new BadRequestAlertException("The tests repository contains symbolic links, which the Security Framework does not follow: " + String.join(", ", links),
                    ENTITY_NAME, "securityFrameworkSymbolicLinks");
        }
    }

    /**
     * Rejects a change whose policy files would not be part of the commit. Staging skips a file that Git ignores, for example through a {@code .gitignore}
     * entry for {@code SecurityPolicy.yaml}: the checkout would then contain the policy and look configured, while the pushed tests reference a missing one.
     *
     * @throws BadRequestAlertException if a policy file the tests reference exists in the checkout but is not staged
     */
    static void requireStagedPolicyFiles(Repository repository) throws IOException {
        Path root = repository.getLocalPath();
        DirCache index = repository.readDirCache();
        for (SecurityFrameworkState.PolicyFile policyFile : SecurityPolicyRepositoryFiles.evaluate(root).policyFiles()) {
            Optional<String> path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath());
            if (path.isPresent() && Files.exists(root.resolve(path.get()), LinkOption.NOFOLLOW_LINKS) && index.findEntry(path.get()) < 0) {
                throw new BadRequestAlertException("The policy file " + path.get() + " is ignored by Git, for example by an entry in .gitignore, so it would not be"
                        + " committed. Remove the entry and try again.", ENTITY_NAME, "securityFrameworkPolicyFileIgnored");
            }
        }
    }
}
