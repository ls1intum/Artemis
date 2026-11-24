package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCPushProcessingService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkAction;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkChoiceDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkVersionsDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyRepositoryFiles.BuildTool;

/**
 * Activates and deactivates the Ares 2 Security Framework of a Java programming exercise by changing its exercise-tests repository.
 * <p>
 * The exercise-tests repository is the single source of truth, so there is neither a database column nor any server-side state: the state is what Ares
 * applies when the tests run, see {@link SecurityPolicyRepositoryFiles#evaluate(Map, SecurityPolicyRepositoryFiles.PolicyFileReader,
 * SecurityPolicyRepositoryFiles.BuildFiles)}, and the framework version is the Ares version pinned in the build file. Because the Java templates ship with
 * Ares enabled, a new exercise starts out active.
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
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SecurityFrameworkService {

    private static final Logger log = LoggerFactory.getLogger(SecurityFrameworkService.class);

    public static final String ENTITY_NAME = "programmingExercise.securityFramework";

    private static final String TEMPLATES_DIRECTORY = "templates";

    private static final String PROJECT_TEMPLATE_DIRECTORY = "projectTemplate";

    static final String GRADLE_BUILD_FILE = "build.gradle";

    static final String MAVEN_BUILD_FILE = "pom.xml";

    /** The oldest Ares version the Security Framework supports: the Java templates, the agent setup and the reserved packages script require it. */
    public static final String MINIMUM_FRAMEWORK_VERSION = "2.2.1";

    /**
     * The build file of the tests repository template for each supported project type. Exercises are created from these files, so the Ares version they pin
     * is the version a new exercise starts with. They are read through the {@link ResourceLoaderService}, so an instance that overrides the templates gets
     * its own versions.
     */
    private static final Map<ProjectType, Path> TEMPLATE_BUILD_FILES = templateBuildFiles();

    /** The policy of the tests repository template for each build tool, whose permissions a generated policy takes over. */
    private static final Map<BuildTool, Path> TEMPLATE_POLICY_FILES = Map.of(BuildTool.GRADLE,
            Path.of(TEMPLATES_DIRECTORY, "java", "test", "gradle", PROJECT_TEMPLATE_DIRECTORY, SecurityPolicyRepositoryFiles.POLICY_FILE_NAME), BuildTool.MAVEN,
            Path.of(TEMPLATES_DIRECTORY, "java", "test", "maven", PROJECT_TEMPLATE_DIRECTORY, SecurityPolicyRepositoryFiles.POLICY_FILE_NAME));

    /** Which policy fields each Ares release accepts, see {@link AresCompatibilityManifest}. */
    private static final Path COMPATIBILITY_MANIFEST = Path.of("security-framework", "ares-compatibility.yml");

    private static final String COMMIT_TRAILER_KEY = "Changed-by";

    private static final String COMMIT_TRAILER_VALUE = "Artemis Security Framework";

    /** Marks the commits of this service whose push processing it runs itself, so a retry can tell an unprocessed change of its own from any other commit. */
    private static final String COMMIT_TRAILER = COMMIT_TRAILER_KEY + ": " + COMMIT_TRAILER_VALUE;

    static final String ACTION_TRAILER_KEY = "Security-Framework-Action";

    /**
     * Records what a commit of this service did, also for the commits made while an exercise is imported. Activating looks up the last deactivation by it,
     * to restore the annotations as they were before. It is read from the trailers of a commit only, because the message above them may be written by an
     * instructor and say anything.
     */
    private static final String ACTION_TRAILER = ACTION_TRAILER_KEY + ": ";

    static final String DEACTIVATE_ACTION = "deactivate";

    /**
     * The Security Framework commits whose push processing has not completed, by exercise id and commit hash. An entry is added before the commit is pushed and removed only
     * after the whole processing has returned, so a retry also resumes a processing that failed after it had already saved the solution submission.
     */
    private static final String PENDING_PROCESSING_MAP = "securityFrameworkPendingProcessing";

    /** How long a retry can still resume a failed processing from the pending entry; afterwards only the submission check below applies. */
    private static final Duration PENDING_PROCESSING_TIME_TO_LIVE = Duration.ofDays(7);

    private final GitService gitService;

    private final BareGitRepositoryService bareGitRepositoryService;

    private final TempFileUtilService tempFileUtilService;

    private final ProgrammingExerciseBuildConfigRepository buildConfigRepository;

    private final String defaultBranch;

    private final ResourceLoaderService resourceLoaderService;

    private final Optional<LocalVCPushProcessingService> localVCPushProcessingService;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final List<String> configuredFrameworkVersions;

    private final DistributedDataProvider distributedDataProvider;

    private final AresCompatibilityManifest compatibilityManifest;

    public SecurityFrameworkService(GitService gitService, BareGitRepositoryService bareGitRepositoryService, TempFileUtilService tempFileUtilService,
            ProgrammingExerciseBuildConfigRepository buildConfigRepository, ResourceLoaderService resourceLoaderService,
            Optional<LocalVCPushProcessingService> localVCPushProcessingService, SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, DistributedDataProvider distributedDataProvider,
            @Value("${artemis.version-control.default-branch:main}") String defaultBranch,
            @Value("${artemis.programming.security-framework.supported-versions:}") String[] configuredFrameworkVersions) {
        this.gitService = gitService;
        this.bareGitRepositoryService = bareGitRepositoryService;
        this.tempFileUtilService = tempFileUtilService;
        this.buildConfigRepository = buildConfigRepository;
        this.resourceLoaderService = resourceLoaderService;
        this.localVCPushProcessingService = localVCPushProcessingService;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.distributedDataProvider = distributedDataProvider;
        this.defaultBranch = defaultBranch;
        this.configuredFrameworkVersions = Arrays.stream(configuredFrameworkVersions).map(String::trim).filter(version -> !version.isEmpty()).distinct()
                .filter(SecurityFrameworkService::isSupportedConfiguredVersion).toList();
        this.compatibilityManifest = loadCompatibilityManifest(resourceLoaderService);
    }

    /**
     * @return which policy fields each Ares release accepts
     */
    AresCompatibilityManifest compatibilityManifest() {
        return compatibilityManifest;
    }

    /**
     * Without a readable manifest, no release can be checked. Every release is then reported as not verified, which is what the instructor is told, instead of
     * blocking every activation.
     */
    private static AresCompatibilityManifest loadCompatibilityManifest(ResourceLoaderService resourceLoaderService) {
        try {
            return AresCompatibilityManifest.parse(resourceLoaderService.getResource(COMPATIBILITY_MANIFEST).getContentAsString(StandardCharsets.UTF_8));
        }
        catch (IOException | IllegalArgumentException e) {
            log.error("Could not read the Ares compatibility manifest {}; no Ares version can be checked against the policies", COMPATIBILITY_MANIFEST, e);
            return AresCompatibilityManifest.empty();
        }
    }

    private static boolean isSupportedConfiguredVersion(String version) {
        if (!isAtLeastMinimumVersion(version)) {
            log.warn("Ignoring the configured Security Framework version {}: the oldest supported version is {}", version, MINIMUM_FRAMEWORK_VERSION);
            return false;
        }
        return true;
    }

    /**
     * @param version an Ares version
     * @return whether the version can be parsed and is not older than {@link #MINIMUM_FRAMEWORK_VERSION}
     */
    static boolean isAtLeastMinimumVersion(String version) {
        try {
            return ModuleDescriptor.Version.parse(version).compareTo(ModuleDescriptor.Version.parse(MINIMUM_FRAMEWORK_VERSION)) >= 0;
        }
        catch (IllegalArgumentException _) {
            return false;
        }
    }

    private static Map<ProjectType, Path> templateBuildFiles() {
        Map<ProjectType, Path> buildFiles = new EnumMap<>(ProjectType.class);
        Path gradleBuildFile = Path.of(TEMPLATES_DIRECTORY, "java", "test", "gradle", PROJECT_TEMPLATE_DIRECTORY, GRADLE_BUILD_FILE);
        buildFiles.put(ProjectType.PLAIN_GRADLE, gradleBuildFile);
        buildFiles.put(ProjectType.GRADLE_GRADLE, gradleBuildFile);
        buildFiles.put(ProjectType.PLAIN_MAVEN, Path.of(TEMPLATES_DIRECTORY, "java", "test", "maven", PROJECT_TEMPLATE_DIRECTORY, MAVEN_BUILD_FILE));
        buildFiles.put(ProjectType.MAVEN_MAVEN, Path.of(TEMPLATES_DIRECTORY, "java", "maven_maven", "test", PROJECT_TEMPLATE_DIRECTORY, MAVEN_BUILD_FILE));
        return Collections.unmodifiableMap(buildFiles);
    }

    /**
     * The versions an instructor can choose and the version each template starts with.
     * <p>
     * The selectable versions are the ones configured via {@code artemis.programming.security-framework.supported-versions} and the template versions, each
     * only if it is not older than {@link #MINIMUM_FRAMEWORK_VERSION}, newest first. The template versions are reported as the templates pin them, even if
     * a template that an instance overrides pins an unsupported version, so the client never shows a default that differs from what an exercise is created
     * with.
     *
     * @return the selectable versions and the template version per project type
     */
    public SecurityFrameworkVersionsDTO getFrameworkVersions() {
        Map<ProjectType, String> templateVersions = templateVersions();
        Set<String> selectableVersions = new LinkedHashSet<>(configuredFrameworkVersions);
        templateVersions.values().stream().filter(SecurityFrameworkService::isAtLeastMinimumVersion).forEach(selectableVersions::add);
        List<String> sortedVersions = selectableVersions.stream().sorted(Comparator.comparing(ModuleDescriptor.Version::parse).reversed()).toList();
        return new SecurityFrameworkVersionsDTO(sortedVersions, templateVersions);
    }

    private Map<ProjectType, String> templateVersions() {
        Map<ProjectType, String> templateVersions = new EnumMap<>(ProjectType.class);
        TEMPLATE_BUILD_FILES.forEach((projectType, buildFile) -> readTemplateAresVersion(buildFile).ifPresent(version -> templateVersions.put(projectType, version)));
        return templateVersions;
    }

    private Optional<String> readTemplateAresVersion(Path buildFile) {
        try {
            String content = resourceLoaderService.getResource(buildFile).getContentAsString(StandardCharsets.UTF_8);
            boolean gradle = buildFile.getFileName().toString().endsWith(".gradle");
            return SecurityPolicyRepositoryFiles.parseAresVersion(gradle ? content : null, gradle ? null : content);
        }
        catch (IOException e) {
            log.warn("Could not read the Ares version of the template {}", buildFile, e);
            return Optional.empty();
        }
    }

    /**
     * Reads the Security Framework state of an exercise from the pushed state of its exercise-tests repository. It reads the bare repository directly, so it
     * never touches a working copy and cannot discard changes someone else has not pushed yet.
     *
     * @param exercise the programming exercise
     * @return the current state
     */
    public SecurityFrameworkConfigDTO getConfig(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return SecurityFrameworkConfigDTO.of(new SecurityFrameworkState(SecurityActivationStatus.INACTIVE, List.of(), List.of(), List.of(), List.of()), null, List.of());
        }
        LocalVCRepositoryUri testRepositoryUri = testRepositoryUri(exercise);
        String branch = repositoryBranch(exercise);
        try (Repository repository = bareGitRepositoryService.getBareRepository(testRepositoryUri, branch, false); RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(Constants.R_HEADS + branch);
            if (head == null) {
                return SecurityFrameworkConfigDTO.of(new SecurityFrameworkState(SecurityActivationStatus.INACTIVE, List.of(), List.of(), List.of(), List.of()), null, List.of());
            }
            return configAt(repository, revWalk.parseCommit(head).getTree());
        }
        catch (IOException e) {
            log.error("Could not read the tests repository of exercise {}", exercise.getId(), e);
            throw new InternalServerErrorAlertException("Could not read the tests repository.", ENTITY_NAME, "securityFrameworkReadFailed");
        }
    }

    /**
     * @param repository the tests repository
     * @param tree       the tree of one of its commits
     * @return the Security Framework state of that commit
     * @throws IOException if the commit cannot be read
     */
    public SecurityFrameworkConfigDTO configAt(Repository repository, RevTree tree) throws IOException {
        String frameworkVersion = SecurityFrameworkCommitFiles.aresVersion(repository, tree).orElse(null);
        SecurityFrameworkState state = SecurityFrameworkCommitFiles.evaluate(repository, tree);
        UnaryOperator<@Nullable String> policyContent = path -> SecurityFrameworkCommitFiles.readPolicyFile(repository, tree, path).content();
        return SecurityFrameworkConfigDTO.of(state, frameworkVersion, versionCompatibilities(state, policyContent));
    }

    /**
     * Activates the Security Framework: switches the {@code @Policy} annotations back on as they were before the last deactivation, restores or generates the
     * policy files they need, adds missing test classes to them, pins the framework version and commits and pushes the change. For a misconfigured exercise,
     * the annotations stay as they are and only the policy files are repaired.
     *
     * @param exercise         the programming exercise
     * @param frameworkVersion the Ares version to pin
     * @param user             the acting user, who authors the commit
     * @return the resulting state
     */
    public SecurityFrameworkConfigDTO activate(ProgrammingExercise exercise, String frameworkVersion, User user) {
        validateJavaExercise(exercise);
        validateFrameworkVersion(frameworkVersion);
        return changeTestRepository(exercise, user, "Activate the Ares security policy", "activate", true,
                repository -> applyActivation(repository, frameworkVersion, packageName(exercise)));
    }

    /**
     * Deactivates the Security Framework: deletes the policy file, disables the {@code @Policy} annotations and commits and pushes the change to the
     * exercise-tests repository. An exercise that is already inactive is not changed.
     *
     * @param exercise the programming exercise
     * @param user     the acting user, who authors the commit
     * @return the resulting state
     */
    public SecurityFrameworkConfigDTO deactivate(ProgrammingExercise exercise, User user) {
        validateJavaExercise(exercise);
        return changeTestRepository(exercise, user, "Deactivate the Ares security policy", DEACTIVATE_ACTION, true, SecurityFrameworkService::applyDeactivation);
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
        return changeTestRepository(exercise, user, "Use Ares " + frameworkVersion + " for the security policy", "version", true, repository -> {
            Path root = repository.getLocalPath();
            SecurityFrameworkState state = requireSupported(SecurityPolicyRepositoryFiles.evaluate(root));
            if (state.status() != SecurityActivationStatus.CONFIGURED) {
                throw new BadRequestAlertException("The framework version cannot be changed because the Security Framework is not active for this exercise.", ENTITY_NAME,
                        "securityFrameworkNotActive");
            }
            requireCompatiblePolicyFiles(root, state, frameworkVersion);
            SecurityPolicyRepositoryFiles.writeAresVersion(root, frameworkVersion);
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
    public SecurityFrameworkConfigDTO addDiscoveredTestClasses(ProgrammingExercise exercise, User user) {
        validateJavaExercise(exercise);
        return changeTestRepository(exercise, user, "Add the discovered test classes to the security policy", "test-classes", true, repository -> {
            Path root = repository.getLocalPath();
            SecurityFrameworkState state = requireSupported(SecurityPolicyRepositoryFiles.evaluate(root));
            if (state.status() != SecurityActivationStatus.CONFIGURED) {
                throw new BadRequestAlertException("Test classes can only be added while the Security Framework is configured for this exercise.", ENTITY_NAME,
                        "securityFrameworkNotActive");
            }
            // Reporting success without a change would leave the classes missing while the card stops offering the action.
            addMissingTestClasses(root, state);
        });
    }

    /**
     * Adds the test classes that the policy files do not list yet (decision B5 of Ares2 issue #386). A class that is missing from the policy is treated as
     * student code by Ares, which can block its reflection or file access, so a list that cannot be rewritten rejects the operation before anything is
     * committed.
     *
     * @throws BadRequestAlertException if a policy file lists its test classes in a form Artemis does not rewrite
     */
    private static void addMissingTestClasses(Path root, SecurityFrameworkState state) {
        for (SecurityFrameworkState.PolicyFile policyFile : state.policyFiles()) {
            if (policyFile.problem() == null && !policyFile.missingTestClasses().isEmpty()
                    && !SecurityPolicyRepositoryFiles.addTestClassesToPolicy(root, policyFile.policyPath(), policyFile.missingTestClasses())) {
                throw new BadRequestAlertException("The test classes cannot be added to " + policyFile.policyPath() + " automatically, because its list of test classes is"
                        + " written in a form Artemis does not rewrite. Add them to the file yourself.", ENTITY_NAME, "securityFrameworkTestClassesNotAdded");
            }
        }
    }

    /**
     * Validates the Security Framework choice of an exercise that is being created or imported. It runs before anything is persisted, so a rejected choice
     * never leaves a half-created exercise behind.
     *
     * @param programmingLanguage the language of the new exercise
     * @param projectType         the project type of the new exercise
     * @param choice              the choice
     */
    public void validateChoice(ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType, SecurityFrameworkChoiceDTO choice) {
        if (choice.action() == null) {
            throw new BadRequestAlertException("The Security Framework choice must say whether the sandbox is activated or deactivated.", ENTITY_NAME,
                    "securityFrameworkChoiceIncomplete");
        }
        if (programmingLanguage != ProgrammingLanguage.JAVA || projectType == ProjectType.MAVEN_BLACKBOX) {
            throw new BadRequestAlertException("The Security Framework is only supported for Java programming exercises.", ENTITY_NAME, "onlyJavaSupported");
        }
        if (choice.frameworkVersion() != null) {
            validateFrameworkVersion(choice.frameworkVersion());
        }
    }

    /**
     * Validates the choice of an exercise that is created from a template. It starts with the policy of its template, so pinning a release that does not
     * accept that policy would fail every test of the new exercise (decision B10 of Ares2 issue #386). An imported exercise keeps its own policy, which is
     * checked when the choice is applied. A template whose policy cannot be read is left to the checks that run when the repository is set up.
     *
     * @param projectType the project type of the new exercise
     * @param choice      a choice that {@link #validateChoice} accepted
     */
    public void validateChoiceAgainstTemplate(@Nullable ProjectType projectType, SecurityFrameworkChoiceDTO choice) {
        if (choice.action() != SecurityFrameworkAction.ACTIVATE || choice.frameworkVersion() == null || projectType == null) {
            return;
        }
        String frameworkVersion = choice.frameworkVersion();
        BuildTool buildTool = projectType == ProjectType.PLAIN_MAVEN || projectType == ProjectType.MAVEN_MAVEN ? BuildTool.MAVEN : BuildTool.GRADLE;
        try {
            String templatePolicy = resourceLoaderService.getResource(TEMPLATE_POLICY_FILES.get(buildTool)).getContentAsString(StandardCharsets.UTF_8);
            describeIncompatibility(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, compatibilityManifest.check(frameworkVersion, templatePolicy)).ifPresent(details -> {
                throw versionIncompatible(frameworkVersion, details);
            });
        }
        catch (IOException e) {
            log.warn("Could not read the policy of the {} template to check it against Ares {}", buildTool, frameworkVersion, e);
        }
    }

    /**
     * @param projectType the project type of the new exercise
     * @param choice      a validated choice
     * @return whether the choice differs from the template the exercise is created from, i.e. whether it changes the sandbox and therefore needs the
     *         instructor role
     */
    public boolean deviatesFromTemplate(@Nullable ProjectType projectType, SecurityFrameworkChoiceDTO choice) {
        if (choice.action() != SecurityFrameworkAction.ACTIVATE) {
            return true;
        }
        String templateVersion = projectType == null ? null : templateVersions().get(projectType);
        return choice.frameworkVersion() != null && !choice.frameworkVersion().equals(templateVersion);
    }

    /**
     * Applies the choice to the tests repository of a new exercise while it is created from the template, before its first commit. Deactivating only
     * switches the annotations off and keeps the template's policy file, so a later activation finds the template's permissions again instead of generating
     * a default policy.
     *
     * @param repositoryRoot the working tree of the new tests repository
     * @param choice         a validated choice
     */
    public static void applyChoiceToNewTestRepository(Path repositoryRoot, SecurityFrameworkChoiceDTO choice) {
        requireSupported(SecurityPolicyRepositoryFiles.evaluate(repositoryRoot));
        if (choice.action() == SecurityFrameworkAction.DEACTIVATE) {
            SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(repositoryRoot, false);
        }
        if (choice.frameworkVersion() != null) {
            pinVersionOfNewTestRepository(repositoryRoot, choice.frameworkVersion(), choice.action() == SecurityFrameworkAction.ACTIVATE);
        }
    }

    /**
     * Applies the choice to the test template of a new exercise before its first commit. If the template is one the Security Framework cannot manage, e.g.
     * because an instance overrides it, the template is kept as it is: an exception here would make the repository setup replace all repositories with
     * empty commits. The client compares the resulting state with the choice and warns the instructor.
     *
     * @param repositoryRoot the working tree of the new tests repository
     * @param choice         a validated choice
     */
    public static void applyChoiceToTestTemplate(Path repositoryRoot, SecurityFrameworkChoiceDTO choice) {
        try {
            applyChoiceToNewTestRepository(repositoryRoot, choice);
        }
        catch (BadRequestAlertException e) {
            log.warn("The Security Framework choice was not applied to the test template: {}", e.getMessage());
        }
    }

    /**
     * Pins the chosen version. The check against the template before the exercise is created compares only the fields its policy uses, so a release whose
     * rules the policy does not meet shows only once it is pinned: an active policy that the release cannot use is refused, and the build files are restored,
     * so the template stays as it is.
     *
     * @param active whether the policy stays active; a deactivated policy is not used until it is activated, which checks it again
     */
    private static void pinVersionOfNewTestRepository(Path root, String frameworkVersion, boolean active) {
        try {
            Map<Path, String> buildFiles = new HashMap<>();
            for (String buildFile : List.of(GRADLE_BUILD_FILE, MAVEN_BUILD_FILE)) {
                Path file = root.resolve(buildFile);
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    buildFiles.put(file, Files.readString(file));
                }
            }
            SecurityPolicyRepositoryFiles.writeAresVersion(root, frameworkVersion);
            SecurityFrameworkState state = SecurityPolicyRepositoryFiles.evaluate(root);
            if (active && state.status() == SecurityActivationStatus.MISCONFIGURED) {
                for (Map.Entry<Path, String> buildFile : buildFiles.entrySet()) {
                    FileUtils.writeStringToFile(buildFile.getKey().toFile(), buildFile.getValue(), StandardCharsets.UTF_8);
                }
                String details = state.policyFiles().stream().filter(policyFile -> policyFile.problem() != null)
                        .map(policyFile -> policyFile.policyPath() + " (" + policyFile.problem() + ")").collect(Collectors.joining(", "));
                throw versionIncompatible(frameworkVersion, details);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Applies the choice to the tests repository of an imported exercise, after the repositories were copied and before the first builds run. The commit is
     * not processed like a push, because the import triggers the builds itself afterwards, and it carries no processing trailer, so a later action never
     * mistakes it for an unprocessed change.
     *
     * @param exercise the imported exercise
     * @param choice   a validated choice
     * @param user     the acting user, who authors the commit
     * @throws BadRequestAlertException if the copied repository cannot be changed, e.g. because it is {@code UNSUPPORTED}; nothing was pushed then
     */
    public void applyChoiceToImportedExercise(ProgrammingExercise exercise, SecurityFrameworkChoiceDTO choice, User user) {
        validateJavaExercise(exercise);
        if (choice.action() == SecurityFrameworkAction.DEACTIVATE) {
            changeTestRepository(exercise, user, "Deactivate the Ares security policy for the imported exercise", DEACTIVATE_ACTION, false, repository -> {
                applyDeactivation(repository);
                // The requested version applies also to an exercise that is already inactive; without one, the copied version stays.
                if (choice.frameworkVersion() != null) {
                    SecurityPolicyRepositoryFiles.writeAresVersion(repository.getLocalPath(), choice.frameworkVersion());
                }
            });
        }
        else {
            changeTestRepository(exercise, user, "Activate the Ares security policy for the imported exercise", "activate", false, repository -> {
                String version = choice.frameworkVersion() != null ? choice.frameworkVersion() : supportedVersionFor(repository.getLocalPath());
                applyActivation(repository, version, packageName(exercise));
            });
        }
    }

    /**
     * @return the version the repository pins if it is still supported; otherwise the newest supported version, because activating never pins an
     *         unsupported version
     */
    private String supportedVersionFor(Path repositoryRoot) {
        List<String> selectableVersions = getFrameworkVersions().selectableVersions();
        if (selectableVersions.isEmpty()) {
            throw new BadRequestAlertException("No supported Security Framework version is available.", ENTITY_NAME, "unsupportedFrameworkVersion");
        }
        return SecurityPolicyRepositoryFiles.readAresVersion(repositoryRoot).filter(selectableVersions::contains).orElse(selectableVersions.getFirst());
    }

    private void applyActivation(Repository repository, String frameworkVersion, Optional<String> packageName) {
        Path root = repository.getLocalPath();
        requireSupported(SecurityPolicyRepositoryFiles.evaluate(root));
        BuildTool buildTool = SecurityPolicyRepositoryFiles.detectBuildTool(root)
                .orElseThrow(() -> new BadRequestAlertException("The Security Framework requires a Gradle or Maven test repository that uses Ares.", ENTITY_NAME,
                        "securityFrameworkUnsupportedProject"));
        // The pinned version decides which rules a policy file has to satisfy, so it is written before the files are evaluated.
        SecurityPolicyRepositoryFiles.writeAresVersion(root, frameworkVersion);
        SecurityFrameworkState state = SecurityPolicyRepositoryFiles.evaluate(root);
        if (state.status() == SecurityActivationStatus.INACTIVE) {
            SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, SecurityFrameworkRestorePoint.annotationStatesBeforeLastDeactivation(repository));
            state = SecurityPolicyRepositoryFiles.evaluate(root);
        }
        if (state.status() == SecurityActivationStatus.INACTIVE) {
            // The test classes that used the sandbox before the deactivation are gone, and the others stay switched off as the instructor left them.
            throw new BadRequestAlertException("No test class would use the sandbox after the activation. Switch on the @Policy annotation of a test class first.", ENTITY_NAME,
                    "securityFrameworkNoActiveTestClass");
        }
        // A policy that uses fields the release does not know would otherwise be reported as not repairable, which hides the actual reason.
        requireCompatiblePolicyFiles(root, state, frameworkVersion);
        for (SecurityFrameworkState.PolicyFile policyFile : state.policyFiles()) {
            if (policyFile.problem() == SecurityFrameworkState.PolicyFileProblem.MISSING) {
                String path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath()).orElseThrow();
                String policy = SecurityFrameworkCommitFiles.findPreviousPolicy(repository, path).orElseGet(() -> generatePolicy(root, buildTool, packageName));
                SecurityPolicyRepositoryFiles.writePolicy(root, path, policy);
            }
            else if (policyFile.problem() != null && policyFile.problem().breaksTheSandbox()) {
                throw policyNotRepairable(policyFile);
            }
        }
        addMissingTestClasses(root, SecurityPolicyRepositoryFiles.evaluate(root));
        // A policy restored from the history may use fields the release does not know.
        requireCompatiblePolicyFiles(root, SecurityPolicyRepositoryFiles.evaluate(root), frameworkVersion);
        requireUsablePolicyFiles(root);
    }

    /**
     * Rejects pinning a release that does not accept a field or the format version of a policy file, because Ares would then fail every test (decision B10
     * of Ares2 issue #386). Files that cannot be read are left to {@link #requireUsablePolicyFiles(Path)}, and a release the manifest does not list is not
     * checked.
     *
     * @throws BadRequestAlertException with the incompatible files and fields as translation parameters
     */
    private void requireCompatiblePolicyFiles(Path root, SecurityFrameworkState state, String frameworkVersion) {
        List<String> incompatibilities = new ArrayList<>();
        for (SecurityFrameworkState.PolicyFile policyFile : state.policyFiles()) {
            Optional<String> path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath());
            if (path.isEmpty()) {
                continue;
            }
            String content = SecurityPolicyRepositoryFiles.readPolicyFile(root, path.get()).content();
            if (content != null) {
                describeIncompatibility(path.get(), compatibilityManifest.check(frameworkVersion, content)).ifPresent(incompatibilities::add);
            }
        }
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
                ENTITY_NAME, "securityFrameworkVersionIncompatible",
                Map.of("message", "error.securityFrameworkVersionIncompatible", "params", Map.of("version", frameworkVersion, "details", details)));
    }

    /**
     * @return the file and what the release does not accept in it, e.g. {@code SecurityPolicy.yaml: a.b, a.c}; empty if the release accepts the file or it
     *         could not be checked
     */
    static Optional<String> describeIncompatibility(String policyPath, AresCompatibilityManifest.Check check) {
        if (check.verdict() != AresCompatibilityManifest.Verdict.INCOMPATIBLE) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>(check.unsupportedFields());
        if (check.unsupportedFormatVersion() != null) {
            parts.addFirst("thisPolicyFileCompliesToThePolicyVersion " + check.unsupportedFormatVersion());
        }
        return Optional.of(policyPath + ": " + String.join(", ", parts));
    }

    /**
     * Generates a policy for a repository that has none in its history, e.g. after a file import, which does not keep the history. It permits what the template
     * of the build tool permits, because a policy without any permission fails the template's own solution. If that is not possible, it permits nothing.
     */
    private String generatePolicy(Path root, BuildTool buildTool, Optional<String> packageName) {
        Optional<String> templatePermissions = readTemplatePermissions(buildTool);
        if (templatePermissions.isPresent()) {
            String policy = SecurityPolicyRepositoryFiles.generateDefaultPolicy(root, buildTool, packageName, templatePermissions.get());
            if (SecurityPolicySchema.read(policy).valid()) {
                return policy;
            }
            log.warn("The permissions of the {} template do not form a valid policy, generating a policy without permissions instead", buildTool);
        }
        return SecurityPolicyRepositoryFiles.generateDefaultPolicy(root, buildTool, packageName, SecurityPolicyRepositoryFiles.NO_RESOURCE_PERMISSIONS);
    }

    private Optional<String> readTemplatePermissions(BuildTool buildTool) {
        try {
            String template = resourceLoaderService.getResource(TEMPLATE_POLICY_FILES.get(buildTool)).getContentAsString(StandardCharsets.UTF_8);
            Optional<String> permissions = SecurityPolicyRepositoryFiles.extractResourcePermissions(template);
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
        for (SecurityFrameworkState.PolicyFile policyFile : requireSupported(SecurityPolicyRepositoryFiles.evaluate(root)).policyFiles()) {
            if (policyFile.problem() != null && policyFile.problem().breaksTheSandbox()) {
                throw policyNotRepairable(policyFile);
            }
        }
    }

    private static BadRequestAlertException policyNotRepairable(SecurityFrameworkState.PolicyFile policyFile) {
        return new BadRequestAlertException("The policy file " + policyFile.policyPath() + " cannot be repaired automatically (" + policyFile.problem() + ").", ENTITY_NAME,
                "securityFrameworkPolicyNotRepairable");
    }

    private static void applyDeactivation(Repository repository) {
        Path root = repository.getLocalPath();
        SecurityFrameworkState state = requireSupported(SecurityPolicyRepositoryFiles.evaluate(root));
        if (state.status() == SecurityActivationStatus.INACTIVE) {
            // Nothing to do; a commit here would also become a misleading restore point for the next activation.
            return;
        }
        // Every policy file an active annotation points to is deleted, not only SecurityPolicy.yaml, because the policy editor can add more of them. A file
        // that is no policy, e.g. an annotation that points to build.gradle or a YAML configuration, is not deleted.
        Stream.concat(Stream.of(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME), state.policyFiles().stream().map(SecurityFrameworkState.PolicyFile::policyPath))
                .map(SecurityPolicyRepositoryFiles::validatePolicyPath).flatMap(Optional::stream).distinct()
                .forEach(path -> SecurityPolicyRepositoryFiles.deletePolicyFileIfPolicy(root, path));
        SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, false);
    }

    /**
     * Applies a change in a private checkout of the tests repository and pushes it. The shared working copy that the online editor uses is never touched,
     * so neither side can reset or commit the other's files. The push is leased on the commit the checkout started from: if someone pushed in between, it is
     * rejected, the request fails and nothing is overwritten.
     *
     * @param action  what the change does, recorded in the commit so a later activation can find the last deactivation
     * @param process whether to run the push processing, which triggers the builds; false while an exercise is imported, because the import triggers them
     */
    SecurityFrameworkConfigDTO changeTestRepository(ProgrammingExercise exercise, User user, String commitMessage, String action, boolean process, Consumer<Repository> change) {
        return changeTestRepository(exercise, user, commitMessage, action, "", process, change);
    }

    /**
     * Like {@link #changeTestRepository(ProgrammingExercise, User, String, String, boolean, Consumer)}, with further trailers for the commit.
     *
     * @param trailers further trailer lines of the commit, each ending with a line break; empty for none
     */
    SecurityFrameworkConfigDTO changeTestRepository(ProgrammingExercise exercise, User user, String commitMessage, String action, String trailers, boolean process,
            Consumer<Repository> change) {
        LocalVCRepositoryUri testRepositoryUri = testRepositoryUri(exercise);
        String branch = repositoryBranch(exercise);
        Repository repository = null;
        Path temporaryCheckout = null;
        try {
            temporaryCheckout = tempFileUtilService.createTempDirectory("security-framework-");
            repository = gitService.getOrCheckoutRepositoryOnBranch(testRepositoryUri, temporaryCheckout.resolve("repository"), branch);
            String checkedOutHead = gitService.getLocalHeadHash(repository);
            SecurityFrameworkCheckout.rejectSymbolicLinks(exercise, repository);
            change.accept(repository);
            boolean changed = gitService.stageAllChanges(repository);
            SecurityFrameworkCheckout.requireStagedPolicyFiles(repository);
            if (changed) {
                String allTrailers = "\n\n" + trailers + ACTION_TRAILER + action + (process ? "\n" + COMMIT_TRAILER : "");
                String commitHash = gitService.commitStagedChanges(repository, commitMessage + allTrailers, user);
                pushAndProcess(exercise, user, repository, branch, commitHash, checkedOutHead, process);
            }
            else if (process && checkedOutHead != null) {
                resumeProcessing(exercise, user, repository, branch, checkedOutHead);
            }
            return readConfig(repository);
        }
        catch (GitAPIException | IOException | UncheckedIOException e) {
            // Writing a file of the checkout throws UncheckedIOException; like a failed push, it leaves nothing pushed.
            log.error("Could not apply the Security Framework change to the tests repository of exercise {}", exercise.getId(), e);
            throw new InternalServerErrorAlertException("Could not push the Security Framework change to the tests repository.", ENTITY_NAME, "securityFrameworkPushFailed");
        }
        finally {
            if (repository != null) {
                repository.closeBeforeDelete();
            }
            if (temporaryCheckout != null && !FileUtils.deleteQuietly(temporaryCheckout.toFile())) {
                log.warn("Could not delete the temporary Security Framework checkout {}", temporaryCheckout);
            }
        }
    }

    /**
     * Pushes the commit and, if requested, runs the push processing. A processed change is recorded as pending first, so a retry can tell that its processing
     * has not completed if the push succeeds but the processing fails.
     */
    private void pushAndProcess(ProgrammingExercise exercise, User user, Repository repository, String branch, String commitHash, String checkedOutHead, boolean process)
            throws GitAPIException, IOException {
        if (process && localVCPushProcessingService.isPresent()) {
            pendingProcessing().put(pendingProcessingKey(exercise, commitHash), commitHash);
        }
        pushWithLease(exercise, repository, commitHash, branch, checkedOutHead);
        if (process) {
            processPushToTestRepository(exercise, testRepositoryUri(exercise), branch, commitHash, user);
        }
    }

    /**
     * A retry after the processing of an earlier change failed: the change is already pushed, so the processing is resumed at the head, which contains it.
     */
    private void resumeProcessing(ProgrammingExercise exercise, User user, Repository repository, String branch, String head) throws IOException {
        Optional<String> unprocessed = unprocessedSecurityFrameworkCommit(exercise, repository, head);
        if (unprocessed.isPresent()) {
            processPushToTestRepository(exercise, testRepositoryUri(exercise), branch, head, user);
            pendingProcessing().remove(pendingProcessingKey(exercise, unprocessed.get()));
        }
    }

    /**
     * Pushes the commit if the branch still points to the commit the checkout started from.
     *
     * @throws ConflictException if someone pushed to the branch meanwhile, which rejected the lease; the caller can reload and try again
     */
    private void pushWithLease(ProgrammingExercise exercise, Repository repository, String commitHash, String branch, String checkedOutHead) throws GitAPIException, IOException {
        try {
            gitService.pushCommitWithLease(repository, commitHash, branch, checkedOutHead);
        }
        catch (GitAPIException e) {
            try (Repository serverRepository = bareGitRepositoryService.getBareRepository(testRepositoryUri(exercise), branch, false)) {
                ObjectId head = serverRepository.resolve(Constants.R_HEADS + branch);
                if (head != null && head.name().equals(commitHash)) {
                    // The push reached the server and failed only afterwards, e.g. while reading its answer, so the change is saved.
                    log.warn("The push of {} to the tests repository of exercise {} failed after the server took it", commitHash, exercise.getId(), e);
                    return;
                }
                if (head != null && !head.name().equals(checkedOutHead)) {
                    log.info("The tests repository of exercise {} changed while a Security Framework change was prepared, so it was not pushed", exercise.getId(), e);
                    throw new ConflictException("Someone changed the tests repository while this change was prepared, so it was not saved. Reload and try again.", ENTITY_NAME,
                            "securityFrameworkConcurrentChange");
                }
            }
            throw e;
        }
    }

    /**
     * Runs the processing that follows every push to a tests repository, like a push through git or the online editor: it creates an exercise version,
     * marks the existing results as outdated and triggers the template and solution builds, which now run with the changed sandbox. It needs the server-side
     * repository, because it derives the exercise and the repository type from its location.
     */
    private void processPushToTestRepository(ProgrammingExercise exercise, LocalVCRepositoryUri testRepositoryUri, String branch, String commitHash, User user) {
        localVCPushProcessingService.ifPresent(pushProcessingService -> {
            try (Repository serverRepository = bareGitRepositoryService.getBareRepository(testRepositoryUri, branch, false)) {
                pushProcessingService.processNewPush(commitHash, serverRepository, user, Optional.empty(), Optional.empty());
                // Only now is the processing complete: the submission is saved, the results are marked as outdated and the builds are triggered.
                pendingProcessing().remove(pendingProcessingKey(exercise, commitHash));
            }
            catch (RuntimeException e) {
                // The change itself is pushed. Retrying the same action resumes the processing, see unprocessedSecurityFrameworkCommit.
                log.error("Could not process the Security Framework commit {} of the tests repository {}", commitHash, testRepositoryUri, e);
                throw new InternalServerErrorAlertException("The Security Framework change was saved, but the builds could not be started. Please retry.", ENTITY_NAME,
                        "securityFrameworkProcessingFailed");
            }
        });
    }

    /**
     * A Security Framework commit is unprocessed if its processing has not completed: either it is still recorded as pending, or, as the head, the solution
     * participation has no submission for it. The pending entry covers a processing that failed after it had saved the submission, e.g. while triggering the
     * builds. The submission check covers a pending entry that is gone, e.g. after all nodes restarted, as long as the processing failed before saving the
     * submission. Only the latest Security Framework commit counts: once its processing completed, it built every earlier one. If others pushed after it, the
     * processing of the head builds it, too, so it is resumed unless the head was processed.
     *
     * @return the latest Security Framework commit up to the head, if its processing and the processing of the head have not completed
     */
    private Optional<String> unprocessedSecurityFrameworkCommit(ProgrammingExercise exercise, Repository repository, String head) throws IOException {
        boolean headProcessed = solutionParticipationRepository.findByProgrammingExerciseId(exercise.getId())
                .map(solutionParticipation -> programmingSubmissionRepository.existsByParticipationIdAndCommitHash(solutionParticipation.getId(), head)).orElse(true);
        try (RevWalk revWalk = new RevWalk(repository)) {
            revWalk.markStart(revWalk.parseCommit(repository.resolve(head)));
            for (RevCommit commit : revWalk) {
                if (commit.getFooterLines(COMMIT_TRAILER_KEY).contains(COMMIT_TRAILER_VALUE)) {
                    boolean isHead = commit.name().equals(head);
                    boolean pending = pendingProcessing().get(pendingProcessingKey(exercise, commit.name())) != null;
                    return (pending && (isHead || !headProcessed)) || (isHead && !headProcessed) ? Optional.of(commit.name()) : Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The key of a commit's pending entry. Keying by exercise and commit keeps the entries of different commits apart: the entry of a commit whose push was
     * rejected, e.g. because another instructor pushed first, can never replace the entry of the commit that was pushed.
     */
    private static String pendingProcessingKey(ProgrammingExercise exercise, String commitHash) {
        return exercise.getId() + ":" + commitHash;
    }

    /**
     * A repository that uses Ares in a way this service cannot evaluate, see {@link SecurityFrameworkState.UnsupportedReasonType}, is never changed: a change
     * could leave tests without the sandbox while reporting it as active, or the other way round.
     *
     * @return the state, if it is supported
     */
    static SecurityFrameworkState requireSupported(SecurityFrameworkState state) {
        if (state.status() == SecurityActivationStatus.UNSUPPORTED) {
            String reasons = state.unsupportedReasons().stream().map(reason -> reason.type() + " (" + reason.location() + ")").distinct().reduce((a, b) -> a + ", " + b).orElse("");
            throw new BadRequestAlertException("The Security Framework cannot manage the tests repository automatically: " + reasons, ENTITY_NAME,
                    "securityFrameworkUnsupportedRepository");
        }
        return state;
    }

    /**
     * The map is requested on each use rather than in the constructor, so creating the service never contacts the distributed data provider. Every provider
     * returns a cheap view of the named map, so there is nothing worth keeping in a field.
     */
    private DistributedMap<String, String> pendingProcessing() {
        return distributedDataProvider.getExpiringMap(PENDING_PROCESSING_MAP, PENDING_PROCESSING_TIME_TO_LIVE);
    }

    private SecurityFrameworkConfigDTO readConfig(Repository repository) {
        Path root = repository.getLocalPath();
        SecurityFrameworkState state = SecurityPolicyRepositoryFiles.evaluate(root);
        return SecurityFrameworkConfigDTO.of(state, SecurityPolicyRepositoryFiles.readAresVersion(root).orElse(null),
                versionCompatibilities(state, path -> SecurityPolicyRepositoryFiles.readPolicyFile(root, path).content()));
    }

    /**
     * Checks every selectable Ares version against the policy files of a state, so the client can tell which versions can be pinned before the instructor
     * picks one. A version is {@code INCOMPATIBLE} as soon as one file uses something it does not accept, and {@code UNVERIFIED} if it is not incompatible but
     * one file could not be checked.
     *
     * @param state         the state whose policy files are checked
     * @param contentByPath reads a policy file by its validated path; null if it cannot be read
     * @return one entry per selectable version, newest first; empty if none of the policy files of the state can be read
     */
    private List<SecurityFrameworkConfigDTO.VersionCompatibilityDTO> versionCompatibilities(SecurityFrameworkState state, UnaryOperator<@Nullable String> contentByPath) {
        Map<String, String> contents = new LinkedHashMap<>();
        // A file that cannot be read cannot be checked, so a version that accepts all other files is not verified.
        boolean someFileUnchecked = false;
        for (SecurityFrameworkState.PolicyFile policyFile : state.policyFiles()) {
            String path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath()).orElse(null);
            String content = path == null ? null : contentByPath.apply(path);
            if (path != null && content != null) {
                contents.put(path, content);
            }
            else {
                someFileUnchecked = true;
            }
        }
        if (contents.isEmpty()) {
            return List.of();
        }
        List<SecurityFrameworkConfigDTO.VersionCompatibilityDTO> compatibilities = new ArrayList<>();
        for (String version : getFrameworkVersions().selectableVersions()) {
            List<String> incompatibilities = new ArrayList<>();
            boolean unverified = someFileUnchecked;
            for (Map.Entry<String, String> file : contents.entrySet()) {
                AresCompatibilityManifest.Check check = compatibilityManifest.check(version, file.getValue());
                unverified |= check.verdict() == AresCompatibilityManifest.Verdict.UNVERIFIED;
                describeIncompatibility(file.getKey(), check).ifPresent(incompatibilities::add);
            }
            AresCompatibilityManifest.Verdict verdict;
            if (!incompatibilities.isEmpty()) {
                verdict = AresCompatibilityManifest.Verdict.INCOMPATIBLE;
            }
            else if (unverified) {
                verdict = AresCompatibilityManifest.Verdict.UNVERIFIED;
            }
            else {
                verdict = AresCompatibilityManifest.Verdict.COMPATIBLE;
            }
            compatibilities.add(new SecurityFrameworkConfigDTO.VersionCompatibilityDTO(version, verdict.name(), incompatibilities));
        }
        return compatibilities;
    }

    LocalVCRepositoryUri testRepositoryUri(ProgrammingExercise exercise) {
        LocalVCRepositoryUri testRepositoryUri = exercise.getVcsTestRepositoryUri();
        if (testRepositoryUri == null) {
            throw new BadRequestAlertException("The exercise has no tests repository.", ENTITY_NAME, "securityFrameworkNoTestRepository");
        }
        return testRepositoryUri;
    }

    String repositoryBranch(ProgrammingExercise exercise) {
        String branch = buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(exercise.getId()).getBranch();
        return branch == null || branch.isBlank() ? defaultBranch : branch;
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
    void validateJavaExercise(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            throw new BadRequestAlertException("The Security Framework is only supported for Java programming exercises.", ENTITY_NAME, "onlyJavaSupported");
        }
    }

    void validateFrameworkVersion(String frameworkVersion) {
        if (!getFrameworkVersions().selectableVersions().contains(frameworkVersion)) {
            throw new BadRequestAlertException("Unsupported Security Framework version: " + frameworkVersion, ENTITY_NAME, "unsupportedFrameworkVersion");
        }
    }
}
