package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;
import de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyRepositoryFiles.BuildTool;

/**
 * Activates and deactivates the Ares 2 Security Framework of a Java programming exercise by changing its exercise-tests repository.
 * <p>
 * The exercise-tests repository is the single source of truth, so there is neither a database column nor any server-side state: the Security Framework is
 * active exactly when the repository contains {@link SecurityPolicyRepositoryFiles#POLICY_FILE_NAME}, and its framework version is the Ares version pinned in
 * the build file. Because the Java templates ship with Ares enabled, a new exercise starts out active.
 * <ul>
 * <li>Activating restores the policy the exercise last had (so an instructor's permissions survive a deactivate/activate cycle), or generates a default
 * policy if the exercise never had one, switches the {@code @Policy} annotations of the tests on, and commits and pushes the change.</li>
 * <li>Deactivating deletes the policy file, switches every {@code @Policy} annotation to {@code activated = false} and commits and pushes the change, so the
 * student code is no longer sandboxed.</li>
 * <li>Changing the framework version pins another Ares version in the build file and commits and pushes it.</li>
 * </ul>
 * Each change is one commit authored by the acting user, so it is visible in the repository history and can be reverted there.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SecurityFrameworkService {

    private static final Logger log = LoggerFactory.getLogger(SecurityFrameworkService.class);

    public static final String ENTITY_NAME = "programmingExercise.securityFramework";

    /**
     * The Ares version the Java exercise templates pin. {@code SecurityFrameworkTemplateTest} keeps it in sync with the templates.
     */
    static final String DEFAULT_FRAMEWORK_VERSION = "2.2.1";

    private final GitService gitService;

    private final List<String> supportedFrameworkVersions;

    public SecurityFrameworkService(GitService gitService,
            @Value("${artemis.programming.security-framework.supported-versions:" + SecurityFrameworkService.DEFAULT_FRAMEWORK_VERSION + "}") String[] supportedFrameworkVersions) {
        this.gitService = gitService;
        this.supportedFrameworkVersions = Arrays.stream(supportedFrameworkVersions).map(String::trim).filter(version -> !version.isEmpty()).toList();
    }

    /**
     * @return the Ares versions an instructor can pin, newest first. Configured via {@code artemis.programming.security-framework.supported-versions}.
     */
    public List<String> getSupportedFrameworkVersions() {
        return supportedFrameworkVersions;
    }

    /**
     * Reads the Security Framework state of an exercise from its exercise-tests repository.
     *
     * @param exercise the programming exercise
     * @return the current state
     */
    public SecurityFrameworkConfigDTO getConfig(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return inactiveConfig(null);
        }
        Repository repository = checkoutTestRepository(exercise, false);
        return readConfig(repository);
    }

    /**
     * Activates the Security Framework: restores or generates the policy, enables the {@code @Policy} annotations, pins the framework version and commits
     * and pushes the change to the exercise-tests repository.
     *
     * @param exercise         the programming exercise
     * @param frameworkVersion the Ares version to pin
     * @param user             the acting user, who authors the commit
     * @return the resulting state
     */
    public SecurityFrameworkConfigDTO activate(ProgrammingExercise exercise, String frameworkVersion, User user) {
        validateJavaExercise(exercise);
        validateFrameworkVersion(frameworkVersion);
        Repository repository = checkoutTestRepository(exercise, true);
        Path root = repository.getLocalPath();
        BuildTool buildTool = SecurityPolicyRepositoryFiles.detectBuildTool(root)
                .orElseThrow(() -> new BadRequestAlertException("The Security Framework requires a Gradle or Maven test repository that uses Ares.", ENTITY_NAME,
                        "securityFrameworkUnsupportedProject"));

        if (!SecurityPolicyRepositoryFiles.isPolicyPresent(root)) {
            String policy = findPreviousPolicy(repository).orElseGet(() -> SecurityPolicyRepositoryFiles.generateDefaultPolicy(root, buildTool, packageName(exercise)));
            SecurityPolicyRepositoryFiles.writePolicy(root, policy);
        }
        SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, true);
        SecurityPolicyRepositoryFiles.writeAresVersion(root, frameworkVersion);
        commitAndPushIfChanged(repository, "Activate the Ares security policy", user);
        log.debug("Activated the Security Framework for exercise {} with Ares {}", exercise.getId(), frameworkVersion);
        return readConfig(repository);
    }

    /**
     * Deactivates the Security Framework: deletes the policy file, disables the {@code @Policy} annotations and commits and pushes the change to the
     * exercise-tests repository.
     *
     * @param exercise the programming exercise
     * @param user     the acting user, who authors the commit
     * @return the resulting state
     */
    public SecurityFrameworkConfigDTO deactivate(ProgrammingExercise exercise, User user) {
        validateJavaExercise(exercise);
        Repository repository = checkoutTestRepository(exercise, true);
        Path root = repository.getLocalPath();
        SecurityPolicyRepositoryFiles.deletePolicy(root);
        SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, false);
        commitAndPushIfChanged(repository, "Deactivate the Ares security policy", user);
        log.debug("Deactivated the Security Framework for exercise {}", exercise.getId());
        return readConfig(repository);
    }

    /**
     * Pins another Ares version for an active exercise and commits and pushes the change to the exercise-tests repository.
     *
     * @param exercise         the programming exercise
     * @param frameworkVersion the Ares version to pin
     * @param user             the acting user, who authors the commit
     * @return the resulting state
     */
    public SecurityFrameworkConfigDTO updateFrameworkVersion(ProgrammingExercise exercise, String frameworkVersion, User user) {
        validateJavaExercise(exercise);
        validateFrameworkVersion(frameworkVersion);
        Repository repository = checkoutTestRepository(exercise, true);
        Path root = repository.getLocalPath();
        if (!SecurityPolicyRepositoryFiles.isPolicyPresent(root)) {
            throw new BadRequestAlertException("The framework version cannot be changed because the Security Framework is not active for this exercise.", ENTITY_NAME,
                    "securityFrameworkNotActive");
        }
        SecurityPolicyRepositoryFiles.writeAresVersion(root, frameworkVersion);
        commitAndPushIfChanged(repository, "Use Ares " + frameworkVersion + " for the security policy", user);
        log.debug("Changed the Security Framework of exercise {} to Ares {}", exercise.getId(), frameworkVersion);
        return readConfig(repository);
    }

    private SecurityFrameworkConfigDTO readConfig(Repository repository) {
        Path root = repository.getLocalPath();
        String frameworkVersion = SecurityPolicyRepositoryFiles.readAresVersion(root).orElse(null);
        if (!SecurityPolicyRepositoryFiles.isPolicyPresent(root)) {
            return inactiveConfig(frameworkVersion);
        }
        Optional<RevCommit> lastPolicyCommit = lastCommitTouchingPolicy(repository);
        return new SecurityFrameworkConfigDTO(SecurityActivationStatus.ACTIVE.name(), frameworkVersion,
                lastPolicyCommit.map(commit -> commit.getId().abbreviate(7).name()).orElse(null),
                lastPolicyCommit.map(commit -> Instant.ofEpochSecond(commit.getCommitTime()).toString()).orElse(null));
    }

    private SecurityFrameworkConfigDTO inactiveConfig(@Nullable String frameworkVersion) {
        return new SecurityFrameworkConfigDTO(SecurityActivationStatus.INACTIVE.name(), frameworkVersion, null, null);
    }

    private Repository checkoutTestRepository(ProgrammingExercise exercise, boolean writeAccess) {
        LocalVCRepositoryUri testRepositoryUri = exercise.getVcsTestRepositoryUri();
        if (testRepositoryUri == null) {
            throw new BadRequestAlertException("The exercise has no tests repository.", ENTITY_NAME, "securityFrameworkNoTestRepository");
        }
        try {
            Repository repository = gitService.getOrCheckoutRepository(testRepositoryUri, true, writeAccess);
            // The working copy is shared with the online editor, so start from the pushed state and never commit someone else's uncommitted changes.
            gitService.resetToOriginHead(repository);
            gitService.pullIgnoreConflicts(repository);
            return repository;
        }
        catch (GitAPIException e) {
            log.error("Could not check out the tests repository of exercise {}", exercise.getId(), e);
            throw new InternalServerErrorAlertException("Could not check out the tests repository.", ENTITY_NAME, "securityFrameworkCheckoutFailed");
        }
    }

    private void commitAndPushIfChanged(Repository repository, String message, User user) {
        try {
            if (gitService.stageAllChanges(repository)) {
                gitService.commitAndPush(repository, message, false, user);
            }
        }
        catch (GitAPIException e) {
            log.error("Could not commit the Security Framework change to {}", repository.getLocalPath(), e);
            // Do not leave a half-applied change in the shared working copy.
            gitService.resetToOriginHead(repository);
            throw new InternalServerErrorAlertException("Could not push the Security Framework change to the tests repository.", ENTITY_NAME, "securityFrameworkPushFailed");
        }
    }

    /**
     * @return the content of the policy file in the most recent commit that still contained it, if the repository ever had one
     */
    private Optional<String> findPreviousPolicy(Repository repository) {
        try (Git git = new Git(repository)) {
            for (RevCommit commit : git.log().addPath(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).call()) {
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, commit.getTree())) {
                    if (treeWalk != null) {
                        ObjectId blobId = treeWalk.getObjectId(0);
                        return Optional.of(new String(repository.open(blobId).getBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
            return Optional.empty();
        }
        catch (GitAPIException e) {
            log.warn("Could not read the policy history of {}, generating a default policy instead", repository.getLocalPath(), e);
            return Optional.empty();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<RevCommit> lastCommitTouchingPolicy(Repository repository) {
        try (Git git = new Git(repository)) {
            List<RevCommit> commits = new ArrayList<>();
            git.log().addPath(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).setMaxCount(1).call().forEach(commits::add);
            return commits.stream().findFirst();
        }
        catch (GitAPIException e) {
            log.warn("Could not read the policy history of {}", repository.getLocalPath(), e);
            return Optional.empty();
        }
    }

    private static Optional<String> packageName(ProgrammingExercise exercise) {
        return Optional.ofNullable(exercise.getPackageName());
    }

    /**
     * The Security Framework only targets Java exercises, the sole language whose templates integrate Ares. The client gates on the same; enforcing it here
     * also covers a staged change that is sent after the language was switched in the form.
     *
     * @param exercise the programming exercise to check
     */
    private void validateJavaExercise(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            throw new BadRequestAlertException("The Security Framework is only supported for Java programming exercises.", ENTITY_NAME, "onlyJavaSupported");
        }
    }

    private void validateFrameworkVersion(String frameworkVersion) {
        if (!supportedFrameworkVersions.contains(frameworkVersion)) {
            throw new BadRequestAlertException("Unsupported Security Framework version: " + frameworkVersion, ENTITY_NAME, "unsupportedFrameworkVersion");
        }
    }
}
