package de.tum.cit.aet.artemis.sandbox.service;

import static de.tum.cit.aet.artemis.sandbox.service.SandboxConfigurationService.ENTITY_NAME;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevWalkException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.sandbox.config.SandboxEnabled;
import de.tum.cit.aet.artemis.sandbox.domain.BuildTool;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFile;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileProblem;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxState;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxStatus;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReasonType;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxConfigurationDTO;
import de.tum.cit.aet.artemis.sandbox.util.SecurityPolicySchema;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryCheckout;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryCommitFiles;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryFiles;

/**
 * Activates and deactivates the sandbox of a Java programming exercise, i.e. the Ares 2 security policy, by changing its exercise-tests repository.
 * <p>
 * The configuration is read by {@link SandboxConfigurationService} and every change is committed by {@link SandboxTestRepositoryService}.
 * <ul>
 * <li>Activating an inactive exercise switches the {@code @Policy} annotations back on as they were before the last deactivation, so a test class an
 * instructor had switched off stays off. It restores every policy file an active annotation needs from the repository history, or generates one with the
 * permissions of the template if the history has none, and adds test classes the policy does not list yet. Activating a misconfigured exercise only repairs
 * its policy files.</li>
 * <li>Deactivating deletes {@code SecurityPolicy.yaml} and adds {@code activated = false} to every {@code @Policy} annotation while keeping its other
 * arguments, so the student code is no longer sandboxed.</li>
 * <li>Changing the framework version pins another Ares version in the build file.</li>
 * </ul>
 * Each change is one commit authored by the acting user, so it is visible in the repository history and can be reverted there. A repository that uses Ares
 * in a way this service cannot manage reliably is reported as {@code UNSUPPORTED} and never changed.
 */
@Conditional(SandboxEnabled.class)
@Lazy
@Service
public class SandboxActivationService {

    private static final Logger log = LoggerFactory.getLogger(SandboxActivationService.class);

    /** The action of a deactivation commit, which activating looks for to restore the annotations as they were before. */
    static final String DEACTIVATE_ACTION = "deactivate";

    /** The policy of the tests repository template for each build tool, whose permissions a generated policy takes over. */
    private static final Map<BuildTool, Path> TEMPLATE_POLICY_FILES = Map.of(BuildTool.GRADLE,
            Path.of("templates", "java", "test", "gradle", "projectTemplate", TestRepositoryFiles.POLICY_FILE_NAME), BuildTool.MAVEN,
            Path.of("templates", "java", "test", "maven", "projectTemplate", TestRepositoryFiles.POLICY_FILE_NAME));

    private final SandboxConfigurationService configurationService;

    private final SandboxTestRepositoryService testRepositoryService;

    private final ResourceLoaderService resourceLoaderService;

    public SandboxActivationService(SandboxConfigurationService configurationService, SandboxTestRepositoryService testRepositoryService,
            ResourceLoaderService resourceLoaderService) {
        this.configurationService = configurationService;
        this.testRepositoryService = testRepositoryService;
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Activates the sandbox: switches the {@code @Policy} annotations back on as they were before the last deactivation, restores or generates the
     * policy files they need, adds missing test classes to them, pins the framework version and commits and pushes the change. For a misconfigured exercise,
     * the annotations stay as they are and only the policy files are repaired.
     *
     * @param exercise         the programming exercise
     * @param frameworkVersion the Ares version to pin
     * @param user             the acting user, who authors the commit
     * @return the resulting state
     */
    public SandboxConfigurationDTO activate(ProgrammingExercise exercise, String frameworkVersion, User user) {
        validateJavaExercise(exercise);
        configurationService.requireSelectableVersion(frameworkVersion);
        return testRepositoryService.changeTestRepository(exercise, user, "Activate the Ares security policy", "activate",
                repository -> applyActivation(repository, frameworkVersion, packageName(exercise)));
    }

    /**
     * Deactivates the sandbox: deletes the policy file, disables the {@code @Policy} annotations and commits and pushes the change to the
     * exercise-tests repository. An exercise that is already inactive is not changed.
     *
     * @param exercise the programming exercise
     * @param user     the acting user, who authors the commit
     * @return the resulting state
     */
    public SandboxConfigurationDTO deactivate(ProgrammingExercise exercise, User user) {
        validateJavaExercise(exercise);
        return testRepositoryService.changeTestRepository(exercise, user, "Deactivate the Ares security policy", DEACTIVATE_ACTION, SandboxActivationService::applyDeactivation);
    }

    /**
     * Pins another Ares version for an active exercise and commits and pushes the change to the exercise-tests repository.
     *
     * @param exercise         the programming exercise
     * @param frameworkVersion the Ares version to pin
     * @param user             the acting user, who authors the commit
     * @return the resulting state
     */
    public SandboxConfigurationDTO updateFrameworkVersion(ProgrammingExercise exercise, String frameworkVersion, User user) {
        validateJavaExercise(exercise);
        configurationService.requireSelectableVersion(frameworkVersion);
        return testRepositoryService.changeTestRepository(exercise, user, "Use Ares " + frameworkVersion + " for the security policy", "version", repository -> {
            Path root = repository.getLocalPath();
            SandboxState state = requireSupported(TestRepositoryCheckout.evaluate(root));
            if (state.status() != SandboxStatus.CONFIGURED) {
                throw new BadRequestAlertException("The framework version cannot be changed because the sandbox is not active for this exercise.", ENTITY_NAME, "sandboxNotActive");
            }
            requireCompatiblePolicyFiles(root, state, frameworkVersion);
            TestRepositoryCheckout.writeAresVersion(root, frameworkVersion);
            requireUsablePolicyFiles(root);
        });
    }

    /**
     * Adds the test classes that point to a policy file but are missing from its {@code theFollowingClassesAreTestClasses} to that file, and commits and pushes
     * the change (decision B5 of Ares2 issue #386). Ares treats a missing class like student code, so its own file access or reflection fails the test. The
     * list is never shortened, and an exercise whose lists are complete is not changed.
     *
     * @param exercise the programming exercise
     * @param user     the acting user, who authors the commit
     * @return the resulting state
     */
    public SandboxConfigurationDTO addMissingTestClasses(ProgrammingExercise exercise, User user) {
        validateJavaExercise(exercise);
        return testRepositoryService.changeTestRepository(exercise, user, "Add the discovered test classes to the security policy", "test-classes", repository -> {
            Path root = repository.getLocalPath();
            SandboxState state = requireSupported(TestRepositoryCheckout.evaluate(root));
            if (state.status() != SandboxStatus.CONFIGURED) {
                throw new BadRequestAlertException("Test classes can only be added while the sandbox is configured for this exercise.", ENTITY_NAME, "sandboxNotActive");
            }
            // Reporting success without a change would leave the classes missing while the card stops offering the action.
            addMissingTestClassesToPolicies(root, state);
        });
    }

    /**
     * Adds the test classes that the policy files do not list yet (decision B5 of Ares2 issue #386). A class that is missing from the policy is treated as
     * student code by Ares, which can block its reflection or file access, so a list that cannot be rewritten rejects the operation before anything is
     * committed.
     *
     * @throws BadRequestAlertException if a policy file lists its test classes in a form Artemis does not rewrite
     */
    private static void addMissingTestClassesToPolicies(Path root, SandboxState state) {
        for (PolicyFile policyFile : state.policyFiles()) {
            if (policyFile.problem() == null && !policyFile.missingTestClasses().isEmpty()
                    && !TestRepositoryCheckout.addTestClassesToPolicy(root, policyFile.policyPath(), policyFile.missingTestClasses())) {
                throw new BadRequestAlertException("The test classes cannot be added to " + policyFile.policyPath() + " automatically, because its list of test classes is"
                        + " written in a form Artemis does not rewrite. Add them to the file yourself.", ENTITY_NAME, "sandboxTestClassesNotAdded");
            }
        }
    }

    private void applyActivation(Repository repository, String frameworkVersion, Optional<String> packageName) {
        Path root = repository.getLocalPath();
        requireSupported(TestRepositoryCheckout.evaluate(root));
        BuildTool buildTool = TestRepositoryCheckout.detectBuildTool(root).orElseThrow(
                () -> new BadRequestAlertException("The sandbox requires a Gradle or Maven test repository that uses Ares.", ENTITY_NAME, "sandboxUnsupportedProject"));
        // The pinned version decides which rules a policy file has to satisfy, so it is written before the files are evaluated.
        TestRepositoryCheckout.writeAresVersion(root, frameworkVersion);
        SandboxState state = TestRepositoryCheckout.evaluate(root);
        if (state.status() == SandboxStatus.INACTIVE) {
            TestRepositoryCheckout.setPolicyAnnotationsActive(root, annotationStatesBeforeLastDeactivation(repository));
            state = TestRepositoryCheckout.evaluate(root);
        }
        if (state.status() == SandboxStatus.INACTIVE) {
            // The test classes that used the sandbox before the deactivation are gone, and the others stay switched off as the instructor left them.
            throw new BadRequestAlertException("No test class would use the sandbox after the activation. Switch on the @Policy annotation of a test class first.", ENTITY_NAME,
                    "sandboxNoActiveTestClass");
        }
        // A policy that uses fields the release does not know would otherwise be reported as not repairable, which hides the actual reason.
        requireCompatiblePolicyFiles(root, state, frameworkVersion);
        for (PolicyFile policyFile : state.policyFiles()) {
            if (policyFile.problem() == PolicyFileProblem.MISSING) {
                String path = TestRepositoryFiles.validatePolicyPath(policyFile.policyPath()).orElseThrow();
                String policy = TestRepositoryCommitFiles.findPreviousPolicy(repository, path).orElseGet(() -> generatePolicy(root, buildTool, packageName));
                TestRepositoryCheckout.writePolicy(root, path, policy);
            }
            else if (policyFile.problem() != null && policyFile.problem().breaksTheSandbox()) {
                throw policyNotRepairable(policyFile);
            }
        }
        addMissingTestClassesToPolicies(root, TestRepositoryCheckout.evaluate(root));
        // A policy restored from the history may use fields the release does not know.
        requireCompatiblePolicyFiles(root, TestRepositoryCheckout.evaluate(root), frameworkVersion);
        requireUsablePolicyFiles(root);
    }

    /**
     * Rejects pinning a release that does not accept a field or the format version of a policy file, because Ares would then fail every test (decision B10
     * of Ares2 issue #386). Files that cannot be read are left to {@link #requireUsablePolicyFiles(Path)}, and a release the manifest does not list is not
     * checked.
     *
     * @throws BadRequestAlertException with the incompatible files and fields as translation parameters
     */
    private void requireCompatiblePolicyFiles(Path root, SandboxState state, String frameworkVersion) {
        Map<String, String> contents = new LinkedHashMap<>();
        for (PolicyFile policyFile : state.policyFiles()) {
            Optional<String> path = TestRepositoryFiles.validatePolicyPath(policyFile.policyPath());
            String content = path.isEmpty() ? null : TestRepositoryCheckout.readPolicyFile(root, path.get()).content();
            if (content != null) {
                contents.put(path.get(), content);
            }
        }
        List<String> incompatibilities = configurationService.checkCompatibility(frameworkVersion, contents, false).incompatibilities();
        if (!incompatibilities.isEmpty()) {
            throw versionIncompatible(frameworkVersion, String.join("; ", incompatibilities));
        }
    }

    /**
     * @param details per policy file, what the release does not accept
     * @return the error that refuses to pin the release; the client shows its translation with the files and fields, so the message names the translation
     *         and the parameters are passed along
     */
    private static BadRequestAlertException versionIncompatible(String frameworkVersion, String details) {
        return new BadRequestAlertException(ErrorConstants.PARAMETERIZED_TYPE, "Ares " + frameworkVersion + " does not accept these parts of the security policy: " + details,
                ENTITY_NAME, "sandboxVersionIncompatible",
                Map.of("message", "error.sandboxVersionIncompatible", "params", Map.of("version", frameworkVersion, "details", details)));
    }

    /**
     * Generates a policy for a repository that has none in its history, e.g. after a file import, which does not keep the history. It permits what the template
     * of the build tool permits, because a policy without any permission fails the template's own solution. If that is not possible, it permits nothing.
     */
    private String generatePolicy(Path root, BuildTool buildTool, Optional<String> packageName) {
        Optional<String> templatePermissions = readTemplatePermissions(buildTool);
        if (templatePermissions.isPresent()) {
            String policy = TestRepositoryCheckout.generateDefaultPolicy(root, buildTool, packageName, templatePermissions.get());
            if (SecurityPolicySchema.isValid(policy)) {
                return policy;
            }
            log.warn("The permissions of the {} template do not form a valid policy, generating a policy without permissions instead", buildTool);
        }
        return TestRepositoryCheckout.generateDefaultPolicy(root, buildTool, packageName, TestRepositoryCheckout.NO_RESOURCE_PERMISSIONS);
    }

    private Optional<String> readTemplatePermissions(BuildTool buildTool) {
        try {
            String template = resourceLoaderService.getResource(TEMPLATE_POLICY_FILES.get(buildTool)).getContentAsString(StandardCharsets.UTF_8);
            Optional<String> permissions = TestRepositoryCheckout.extractResourcePermissions(template);
            if (permissions.isEmpty()) {
                log.warn("The policy of the {} template does not list any permitted resource accesses", buildTool);
            }
            return permissions;
        }
        catch (IOException e) {
            log.warn("Could not read the policy of the {} template", buildTool, e);
            return Optional.empty();
        }
    }

    /**
     * The last check before a change is committed: a policy file that Ares cannot use, e.g. an invalid one restored from the history, is never pushed.
     */
    private static void requireUsablePolicyFiles(Path root) {
        for (PolicyFile policyFile : requireSupported(TestRepositoryCheckout.evaluate(root)).policyFiles()) {
            if (policyFile.problem() != null && policyFile.problem().breaksTheSandbox()) {
                throw policyNotRepairable(policyFile);
            }
        }
    }

    private static BadRequestAlertException policyNotRepairable(PolicyFile policyFile) {
        return new BadRequestAlertException("The policy file " + policyFile.policyPath() + " cannot be repaired automatically (" + policyFile.problem() + ").", ENTITY_NAME,
                "sandboxPolicyNotRepairable");
    }

    private static void applyDeactivation(Repository repository) {
        Path root = repository.getLocalPath();
        SandboxState state = requireSupported(TestRepositoryCheckout.evaluate(root));
        if (state.status() == SandboxStatus.INACTIVE) {
            // Nothing to do; a commit here would also become a misleading restore point for the next activation.
            return;
        }
        TestRepositoryCheckout.deletePolicy(root);
        TestRepositoryCheckout.setPolicyAnnotationsActive(root, false);
    }

    /**
     * A repository that uses Ares in a way this service cannot evaluate, see {@link UnsupportedReasonType}, is never changed: a change
     * could leave tests without the sandbox while reporting it as active, or the other way round.
     *
     * @return the state, if it is supported
     */
    private static SandboxState requireSupported(SandboxState state) {
        if (state.status() == SandboxStatus.UNSUPPORTED) {
            String reasons = state.unsupportedReasons().stream().map(reason -> reason.type() + " (" + reason.location() + ")").distinct().reduce((a, b) -> a + ", " + b).orElse("");
            throw new BadRequestAlertException("The sandbox cannot be managed automatically for this tests repository: " + reasons, ENTITY_NAME, "sandboxUnsupportedRepository");
        }
        return state;
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
                if (commit.getParentCount() > 0 && commit.getFullMessage().contains(SandboxTestRepositoryService.ACTION_TRAILER + DEACTIVATE_ACTION)) {
                    Map<String, Boolean> states = annotationStatesBefore(repository, commit);
                    if (states.containsValue(true)) {
                        return testClass -> states.getOrDefault(testClass, true);
                    }
                }
            }
            return _ -> true;
        }
        // The history is read while it is iterated, so a commit that cannot be read there fails with a RevWalkException.
        catch (GitAPIException | IOException | RevWalkException e) {
            // Switching every annotation on could override test classes an instructor switched off on purpose, so nothing is changed.
            log.error("Could not read the history of {} to restore the annotations", repository.getLocalPath(), e);
            throw new BadRequestAlertException("The state before the last deactivation cannot be read from the history of the tests repository.", ENTITY_NAME,
                    "sandboxRestorePointUnreadable");
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
        if (TestRepositoryCommitFiles.evaluate(repository, parent.getTree()).status() == SandboxStatus.UNSUPPORTED) {
            throw new BadRequestAlertException("The state before the last deactivation cannot be restored, because the tests used Ares in an unsupported way then.", ENTITY_NAME,
                    "sandboxRestorePointUnsupported");
        }
        return TestRepositoryFiles.annotationStates(TestRepositoryCommitFiles.javaSources(repository, parent.getTree()));
    }

    private static Optional<String> packageName(ProgrammingExercise exercise) {
        return Optional.ofNullable(exercise.getPackageName());
    }

    /**
     * The sandbox only targets Java exercises, the sole language whose templates integrate Ares. The client gates on the same; enforcing it here
     * also covers a staged change that is sent after the language was switched in the form.
     *
     * @param exercise the programming exercise to check
     */
    private void validateJavaExercise(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            throw new BadRequestAlertException("The sandbox is only supported for Java programming exercises.", ENTITY_NAME, "onlyJavaSupported");
        }
    }
}
