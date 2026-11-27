package de.tum.cit.aet.artemis.sandbox.service;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.sandbox.config.SandboxEnabled;
import de.tum.cit.aet.artemis.sandbox.domain.AresCompatibilityManifest;
import de.tum.cit.aet.artemis.sandbox.domain.BuildFiles;
import de.tum.cit.aet.artemis.sandbox.domain.CompatibilityCheck;
import de.tum.cit.aet.artemis.sandbox.domain.CompatibilityVerdict;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFile;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxState;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxStatus;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxConfigurationDTO;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxFrameworkVersionsDTO;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryCheckout;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryCommitFiles;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryFiles;

/**
 * Reads the sandbox configuration of a Java programming exercise and the Ares versions an instructor can pin.
 * <p>
 * The exercise-tests repository is the single source of truth, so there is neither a database column nor any server-side state: the configuration is what
 * Ares applies when the tests run, see {@link TestRepositoryFiles#evaluate(Map, java.util.function.Function, BuildFiles)}, and the framework version is the
 * Ares version pinned in the build file. Because the Java templates ship with Ares enabled, a new exercise starts out with an active sandbox.
 */
@Conditional(SandboxEnabled.class)
@Lazy
@Service
public class SandboxConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(SandboxConfigurationService.class);

    /** The entity name of the errors of the sandbox module. */
    public static final String ENTITY_NAME = "sandbox";

    private static final String TEMPLATES_DIRECTORY = "templates";

    private static final String PROJECT_TEMPLATE_DIRECTORY = "projectTemplate";

    private static final String GRADLE_BUILD_FILE = "build.gradle";

    private static final String MAVEN_BUILD_FILE = "pom.xml";

    /** The oldest Ares version the Security Framework supports: the Java templates, the agent setup and the reserved packages script require it. */
    public static final String MINIMUM_FRAMEWORK_VERSION = "2.2.1";

    /**
     * The build file of the tests repository template for each supported project type. Exercises are created from these files, so the Ares version they pin
     * is the version a new exercise starts with. They are read through the {@link ResourceLoaderService}, so an instance that overrides the templates gets
     * its own versions.
     */
    private static final Map<ProjectType, Path> TEMPLATE_BUILD_FILES = templateBuildFiles();

    /** Which policy fields each Ares release accepts, see {@link AresCompatibilityManifest}. */
    private static final Path COMPATIBILITY_MANIFEST = Path.of("sandbox", "ares-compatibility.yml");

    private final BareGitRepositoryService bareGitRepositoryService;

    private final ProgrammingExerciseBuildConfigRepository buildConfigRepository;

    private final String defaultBranch;

    private final ResourceLoaderService resourceLoaderService;

    private final List<String> configuredFrameworkVersions;

    private final AresCompatibilityManifest compatibilityManifest;

    public SandboxConfigurationService(BareGitRepositoryService bareGitRepositoryService, ProgrammingExerciseBuildConfigRepository buildConfigRepository,
            ResourceLoaderService resourceLoaderService, @Value("${artemis.version-control.default-branch:main}") String defaultBranch,
            @Value("${artemis.sandbox.supported-versions:}") String[] configuredFrameworkVersions) {
        this.bareGitRepositoryService = bareGitRepositoryService;
        this.buildConfigRepository = buildConfigRepository;
        this.resourceLoaderService = resourceLoaderService;
        this.defaultBranch = defaultBranch;
        this.configuredFrameworkVersions = Arrays.stream(configuredFrameworkVersions).map(String::trim).filter(version -> !version.isEmpty()).distinct()
                .filter(SandboxConfigurationService::isSupportedConfiguredVersion).toList();
        this.compatibilityManifest = loadCompatibilityManifest(resourceLoaderService);
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
     * The selectable versions are the ones configured via {@code artemis.sandbox.supported-versions} and the template versions, each
     * only if it is not older than {@link #MINIMUM_FRAMEWORK_VERSION}, newest first. The template versions are reported as the templates pin them, even if
     * a template that an instance overrides pins an unsupported version, so the client never shows a default that differs from what an exercise is created
     * with.
     *
     * @return the selectable versions and the template version per project type
     */
    public SandboxFrameworkVersionsDTO getFrameworkVersions() {
        Map<ProjectType, String> templateVersions = templateVersions();
        Set<String> selectableVersions = new LinkedHashSet<>(configuredFrameworkVersions);
        templateVersions.values().stream().filter(SandboxConfigurationService::isAtLeastMinimumVersion).forEach(selectableVersions::add);
        List<String> sortedVersions = selectableVersions.stream().sorted(Comparator.comparing(ModuleDescriptor.Version::parse).reversed()).toList();
        return new SandboxFrameworkVersionsDTO(sortedVersions, templateVersions);
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
            return TestRepositoryFiles.parseAresVersion(gradle ? content : null, gradle ? null : content);
        }
        catch (IOException e) {
            log.warn("Could not read the Ares version of the template {}", buildFile, e);
            return Optional.empty();
        }
    }

    /**
     * Reads the sandbox configuration of an exercise from the pushed state of its exercise-tests repository. It reads the bare repository directly, so it
     * never touches a working copy and cannot discard changes someone else has not pushed yet.
     *
     * @param exercise the programming exercise
     * @return the current state
     */
    public SandboxConfigurationDTO getConfiguration(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() != ProgrammingLanguage.JAVA) {
            return SandboxConfigurationDTO.of(new SandboxState(SandboxStatus.INACTIVE, List.of(), List.of(), List.of(), List.of()), null, List.of());
        }
        LocalVCRepositoryUri testRepositoryUri = testRepositoryUri(exercise);
        String branch = repositoryBranch(exercise);
        try (Repository repository = bareGitRepositoryService.getBareRepository(testRepositoryUri, branch, false); RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(Constants.R_HEADS + branch);
            if (head == null) {
                return SandboxConfigurationDTO.of(new SandboxState(SandboxStatus.INACTIVE, List.of(), List.of(), List.of(), List.of()), null, List.of());
            }
            RevTree tree = revWalk.parseCommit(head).getTree();
            String frameworkVersion = TestRepositoryCommitFiles.aresVersion(repository, tree).orElse(null);
            SandboxState state = TestRepositoryCommitFiles.evaluate(repository, tree);
            UnaryOperator<@Nullable String> policyContent = path -> TestRepositoryCommitFiles.readPolicyFile(repository, tree, path).content();
            return SandboxConfigurationDTO.of(state, frameworkVersion, versionCompatibilities(state, policyContent));
        }
        catch (IOException e) {
            log.error("Could not read the tests repository of exercise {}", exercise.getId(), e);
            throw new InternalServerErrorAlertException("Could not read the tests repository.", ENTITY_NAME, "sandboxReadFailed");
        }
    }

    /**
     * Reads the sandbox configuration of a checkout of the exercise-tests repository, e.g. the one a change was just committed in.
     *
     * @param root the root of the checkout
     * @return the configuration
     */
    public SandboxConfigurationDTO getConfiguration(Path root) {
        SandboxState state = TestRepositoryCheckout.evaluate(root);
        return SandboxConfigurationDTO.of(state, TestRepositoryCheckout.readAresVersion(root).orElse(null),
                versionCompatibilities(state, path -> TestRepositoryCheckout.readPolicyFile(root, path).content()));
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
    private List<SandboxConfigurationDTO.SandboxVersionCompatibilityDTO> versionCompatibilities(SandboxState state, UnaryOperator<@Nullable String> contentByPath) {
        Map<String, String> contents = new LinkedHashMap<>();
        // A file that cannot be read cannot be checked, so a version that accepts all other files is not verified.
        boolean someFileUnchecked = false;
        for (PolicyFile policyFile : state.policyFiles()) {
            String path = TestRepositoryFiles.validatePolicyPath(policyFile.policyPath()).orElse(null);
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
        boolean unchecked = someFileUnchecked;
        return getFrameworkVersions().selectableVersions().stream().map(version -> checkCompatibility(version, contents, unchecked)).toList();
    }

    /**
     * Checks an Ares version against policy files. The version is {@code INCOMPATIBLE} as soon as one file uses something it does not accept, and
     * {@code UNVERIFIED} if it is not incompatible but one file could not be checked.
     *
     * @param frameworkVersion  the Ares version
     * @param policyContents    the content of each policy file by its validated path
     * @param someFileUnchecked whether a policy file could not be read, so the version cannot be verified
     * @return the result, with what the version does not accept per policy file
     */
    public SandboxConfigurationDTO.SandboxVersionCompatibilityDTO checkCompatibility(String frameworkVersion, Map<String, String> policyContents, boolean someFileUnchecked) {
        List<String> incompatibilities = new ArrayList<>();
        boolean unverified = someFileUnchecked;
        for (Map.Entry<String, String> file : policyContents.entrySet()) {
            CompatibilityCheck check = compatibilityManifest.check(frameworkVersion, file.getValue());
            unverified |= check.verdict() == CompatibilityVerdict.UNVERIFIED;
            describeIncompatibility(file.getKey(), check).ifPresent(incompatibilities::add);
        }
        CompatibilityVerdict verdict;
        if (!incompatibilities.isEmpty()) {
            verdict = CompatibilityVerdict.INCOMPATIBLE;
        }
        else if (unverified) {
            verdict = CompatibilityVerdict.UNVERIFIED;
        }
        else {
            verdict = CompatibilityVerdict.COMPATIBLE;
        }
        return new SandboxConfigurationDTO.SandboxVersionCompatibilityDTO(frameworkVersion, verdict, incompatibilities);
    }

    /**
     * @return the file and what the release does not accept in it, e.g. {@code SecurityPolicy.yaml: a.b, a.c}; empty if the release accepts the file or it
     *         could not be checked
     */
    private static Optional<String> describeIncompatibility(String policyPath, CompatibilityCheck check) {
        if (check.verdict() != CompatibilityVerdict.INCOMPATIBLE) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>(check.unsupportedFields());
        if (check.unsupportedFormatVersion() != null) {
            parts.addFirst("thisPolicyFileCompliesToThePolicyVersion " + check.unsupportedFormatVersion());
        }
        return Optional.of(policyPath + ": " + String.join(", ", parts));
    }

    /**
     * @param exercise the programming exercise
     * @return the URI of its exercise-tests repository
     * @throws BadRequestAlertException if the exercise has none
     */
    public LocalVCRepositoryUri testRepositoryUri(ProgrammingExercise exercise) {
        LocalVCRepositoryUri testRepositoryUri = exercise.getVcsTestRepositoryUri();
        if (testRepositoryUri == null) {
            throw new BadRequestAlertException("The exercise has no tests repository.", ENTITY_NAME, "sandboxNoTestRepository");
        }
        return testRepositoryUri;
    }

    /**
     * @param exercise the programming exercise
     * @return the branch its repositories use
     */
    public String repositoryBranch(ProgrammingExercise exercise) {
        String branch = buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(exercise.getId()).getBranch();
        return branch == null || branch.isBlank() ? defaultBranch : branch;
    }

    /**
     * @param frameworkVersion an Ares version an instructor asked for
     * @throws BadRequestAlertException if it is not one of the selectable versions
     */
    public void requireSelectableVersion(String frameworkVersion) {
        if (!getFrameworkVersions().selectableVersions().contains(frameworkVersion)) {
            throw new BadRequestAlertException("Unsupported Security Framework version: " + frameworkVersion, ENTITY_NAME, "unsupportedFrameworkVersion");
        }
    }
}
