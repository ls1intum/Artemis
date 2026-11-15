package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.ENTITY_NAME;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyChangeDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyCommitResultDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyEditorDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyImportDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyPreviewDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityTemplateFilesDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityTestStructureDTO;

/**
 * Reads and changes the security policies of a programming exercise for the policy editor of the Security Framework (Objective 2 of the Security Framework).
 * <p>
 * The tests repository is the only source of truth: the editor loads the policy files that active {@code @Policy} annotations point to, the instructor
 * changes them in the client without saving anything, and one commit writes every change. The commit uses the private checkout and the leased push of
 * {@link SecurityFrameworkService}, so it never touches the working copy of the online editor and fails instead of overwriting a concurrent push. It is
 * refused with a conflict if the policies changed since the editor loaded them, and if the Ares version the repository pins does not accept a policy,
 * because Ares then fails every test.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SecurityPolicyEditorService {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyEditorService.class);

    /** The presets instructors can base a policy on, see the file itself. */
    private static final Path PRESET_CATALOG = Path.of("security-framework", "ares-presets.yml");

    /** The action recorded in the commits of the editor, see {@link SecurityFrameworkService}. */
    private static final String POLICY_ACTION = "policy";

    private static final String DEFAULT_COMMIT_MESSAGE = "Update the Ares security policy";

    private static final int MAX_COMMIT_MESSAGE_LENGTH = 1000;

    /** The template repository of an exercise is small, but the list is capped so a repository with generated files cannot flood the client. */
    static final int MAX_TEMPLATE_FILES = 5000;

    private static final Set<String> POLICY_FILE_EXTENSIONS = Set.of(".yaml", ".yml");

    private final SecurityFrameworkService securityFrameworkService;

    private final BareGitRepositoryService bareGitRepositoryService;

    private final List<SecurityPolicyEditorDTO.PresetDTO> presets;

    public SecurityPolicyEditorService(SecurityFrameworkService securityFrameworkService, BareGitRepositoryService bareGitRepositoryService,
            ResourceLoaderService resourceLoaderService) {
        this.securityFrameworkService = securityFrameworkService;
        this.bareGitRepositoryService = bareGitRepositoryService;
        this.presets = loadPresets(resourceLoaderService);
    }

    /**
     * Without a readable catalog, no preset is offered; policies that already name one still show the name.
     */
    private static List<SecurityPolicyEditorDTO.PresetDTO> loadPresets(ResourceLoaderService resourceLoaderService) {
        try {
            return parsePresets(resourceLoaderService.getResource(PRESET_CATALOG).getContentAsString(StandardCharsets.UTF_8));
        }
        catch (IOException | IllegalArgumentException e) {
            log.error("Could not read the Ares preset catalog {}; no preset is offered", PRESET_CATALOG, e);
            return List.of();
        }
    }

    /**
     * @param yaml the content of a preset catalog
     * @return its presets
     * @throws IllegalArgumentException if the catalog is not valid
     */
    static List<SecurityPolicyEditorDTO.PresetDTO> parsePresets(String yaml) {
        if (!(SecurityPolicySchema.readTree(yaml).orElse(null) instanceof Map<?, ?> catalog) || !(catalog.get("presets") instanceof List<?> entries)) {
            throw new IllegalArgumentException("The preset catalog has no list of presets");
        }
        List<SecurityPolicyEditorDTO.PresetDTO> parsed = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> preset) || !(preset.get("name") instanceof String name) || !(preset.get("label") instanceof String label)) {
                throw new IllegalArgumentException("A preset needs a name and a label");
            }
            SecurityPolicyDocumentDTO permissions = SecurityPolicyDocuments.resourceAccesses(preset.get("theFollowingResourceAccessesArePermitted"))
                    .orElseThrow(() -> new IllegalArgumentException("The resource accesses of the preset " + name + " are not valid"));
            parsed.add(new SecurityPolicyEditorDTO.PresetDTO(name, label, permissions.files(), permissions.network(), permissions.commands(), permissions.threads(),
                    permissions.packages()));
        }
        return List.copyOf(parsed);
    }

    /**
     * Reads the security policies of an exercise from the pushed state of its tests repository, without a checkout.
     *
     * @param exercise the programming exercise
     * @return the policies
     */
    public SecurityPolicyEditorDTO getEditor(ProgrammingExercise exercise) {
        securityFrameworkService.validateJavaExercise(exercise);
        return readTestRepository(exercise, (repository, commit) -> editorAt(repository, commit));
    }

    /**
     * Renders the changed policies without committing them, and checks them against the Ares version the tests repository pins.
     *
     * @param exercise the programming exercise
     * @param change   the changed policies
     * @return the files before and after the change and whether the pinned version accepts them
     */
    public SecurityPolicyPreviewDTO preview(ProgrammingExercise exercise, SecurityPolicyChangeDTO change) {
        securityFrameworkService.validateJavaExercise(exercise);
        requireValidDocuments(change);
        return readTestRepository(exercise, (repository, commit) -> {
            RevTree tree = commit.getTree();
            @Nullable
            String frameworkVersion = frameworkVersion(repository, tree);
            Set<String> existingPaths = SecurityFrameworkService.evaluate(repository, tree).policyFiles().stream().map(SecurityFrameworkState.PolicyFile::policyPath)
                    .collect(Collectors.toSet());
            List<SecurityPolicyPreviewDTO.FileDTO> files = new ArrayList<>();
            for (SecurityPolicyDocumentDTO document : change.policies()) {
                String path = requireValidPolicyPath(document.policyPath());
                String before = existingPaths.contains(path) ? SecurityFrameworkService.readPolicyFile(repository, tree, path).content() : null;
                files.add(new SecurityPolicyPreviewDTO.FileDTO(path, before, SecurityPolicyDocuments.render(document, before)));
            }
            List<String> problems = new ArrayList<>();
            AresCompatibilityManifest.Verdict verdict = AresCompatibilityManifest.Verdict.COMPATIBLE;
            for (SecurityPolicyPreviewDTO.FileDTO file : files) {
                VersionCheck check = checkAgainstVersion(file.policyPath(), file.after(), frameworkVersion);
                problems.addAll(check.problems());
                verdict = worse(verdict, check.verdict());
            }
            return new SecurityPolicyPreviewDTO(files, frameworkVersion, verdict.name(), problems);
        });
    }

    /**
     * Reads an imported policy file like a committed one, so the editor can replace a policy with it. Nothing is written.
     *
     * @param exercise     the programming exercise
     * @param importedFile the path of the policy it replaces and the content of the file
     * @return the policy, with its document or why it cannot be edited
     */
    public SecurityPolicyEditorDTO.PolicyDTO importPolicy(ProgrammingExercise exercise, SecurityPolicyImportDTO importedFile) {
        securityFrameworkService.validateJavaExercise(exercise);
        String path = requireValidPolicyPath(importedFile.policyPath());
        String content = importedFile.content() == null ? "" : importedFile.content();
        SecurityPolicyDocuments.Parsed parsed = content.length() > SecurityPolicyYaml.MAX_POLICY_SIZE
                ? new SecurityPolicyDocuments.Parsed(null, SecurityPolicyDocuments.ReadOnlyReason.TOO_LARGE, null)
                : SecurityPolicyDocuments.parse(path, content, null);
        return new SecurityPolicyEditorDTO.PolicyDTO(path, null, parsed.document(), parsed.reason() == null ? null : parsed.reason().name(), parsed.errorLine(), List.of());
    }

    /**
     * Writes the changed policies to the tests repository in one commit and pushes it. The test classes a new or changed policy lists by their fully qualified
     * name are pointed to that policy, so Ares applies it to them.
     *
     * @param exercise the programming exercise
     * @param change   the changed policies, with the commit the editor loaded them from
     * @param user     the acting user, who authors the commit
     * @return the policies and the Security Framework state after the commit
     */
    public SecurityPolicyCommitResultDTO commit(ProgrammingExercise exercise, SecurityPolicyChangeDTO change, User user) {
        securityFrameworkService.validateJavaExercise(exercise);
        requireValidDocuments(change);
        if (change.policies().isEmpty()) {
            throw new BadRequestAlertException("The change contains no policy.", ENTITY_NAME, "securityPolicyNoChange");
        }
        if (change.baseCommitHash() == null || !ObjectId.isId(change.baseCommitHash())) {
            throw new BadRequestAlertException("The change does not name the commit the policies were loaded from.", ENTITY_NAME, "securityPolicyNoBaseCommit");
        }
        SecurityFrameworkConfigDTO config = securityFrameworkService.changeTestRepository(exercise, user, commitMessage(change.commitMessage()), POLICY_ACTION, true,
                repository -> applyChange(repository, change));
        log.info("User {} changed the security policies {} of programming exercise {}", user.getLogin(),
                change.policies().stream().map(SecurityPolicyDocumentDTO::policyPath).toList(), exercise.getId());
        return new SecurityPolicyCommitResultDTO(getEditor(exercise), config);
    }

    /**
     * Lists the test classes of the tests repository with their test cases.
     *
     * @param exercise the programming exercise
     * @return the test structure
     */
    public SecurityTestStructureDTO getTestStructure(ProgrammingExercise exercise) {
        securityFrameworkService.validateJavaExercise(exercise);
        return readTestRepository(exercise, (repository, commit) -> {
            Map<String, String> javaSources = SecurityFrameworkService.javaSources(repository, commit.getTree());
            return AresTestCases.structure(javaSources, SecurityPolicyRepositoryFiles.testClassPaths(javaSources).keySet());
        });
    }

    /**
     * Lists the regular files of the template repository, at most {@link #MAX_TEMPLATE_FILES}.
     *
     * @param exercise the programming exercise
     * @return the paths of the files
     */
    public SecurityTemplateFilesDTO getTemplateFiles(ProgrammingExercise exercise) {
        securityFrameworkService.validateJavaExercise(exercise);
        LocalVCRepositoryUri templateRepositoryUri = exercise.getVcsTemplateRepositoryUri();
        if (templateRepositoryUri == null) {
            return new SecurityTemplateFilesDTO(List.of(), false);
        }
        String branch = securityFrameworkService.repositoryBranch(exercise);
        try (Repository repository = bareGitRepositoryService.getBareRepository(templateRepositoryUri, branch, false); RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(Constants.R_HEADS + branch);
            if (head == null) {
                return new SecurityTemplateFilesDTO(List.of(), false);
            }
            List<String> paths = new ArrayList<>();
            boolean truncated = false;
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(revWalk.parseCommit(head).getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    if ((treeWalk.getRawMode(0) & FileMode.TYPE_MASK) != FileMode.TYPE_FILE) {
                        continue;
                    }
                    if (paths.size() == MAX_TEMPLATE_FILES) {
                        truncated = true;
                        break;
                    }
                    paths.add(treeWalk.getPathString());
                }
            }
            paths.sort(String::compareTo);
            return new SecurityTemplateFilesDTO(paths, truncated);
        }
        catch (IOException e) {
            log.error("Could not read the template repository of exercise {}", exercise.getId(), e);
            throw new InternalServerErrorAlertException("Could not read the template repository.", ENTITY_NAME, "securityFrameworkReadFailed");
        }
    }

    /** Reads something from the head commit of the tests repository. */
    @FunctionalInterface
    private interface CommitReader<T> {

        T read(Repository repository, RevCommit commit) throws IOException;
    }

    private <T> T readTestRepository(ProgrammingExercise exercise, CommitReader<T> reader) {
        LocalVCRepositoryUri testRepositoryUri = securityFrameworkService.testRepositoryUri(exercise);
        String branch = securityFrameworkService.repositoryBranch(exercise);
        try (Repository repository = bareGitRepositoryService.getBareRepository(testRepositoryUri, branch, false); RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(Constants.R_HEADS + branch);
            if (head == null) {
                throw new BadRequestAlertException("The tests repository has no commit yet.", ENTITY_NAME, "securityPolicyEmptyRepository");
            }
            return reader.read(repository, revWalk.parseCommit(head));
        }
        catch (IOException e) {
            log.error("Could not read the tests repository of exercise {}", exercise.getId(), e);
            throw new InternalServerErrorAlertException("Could not read the tests repository.", ENTITY_NAME, "securityFrameworkReadFailed");
        }
    }

    private SecurityPolicyEditorDTO editorAt(Repository repository, RevCommit commit) throws IOException {
        RevTree tree = commit.getTree();
        SecurityFrameworkState state = SecurityFrameworkService.evaluate(repository, tree);
        List<SecurityPolicyEditorDTO.PolicyDTO> policies = new ArrayList<>();
        for (SecurityFrameworkState.PolicyFile policyFile : state.policyFiles()) {
            Optional<String> path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath());
            SecurityPolicyRepositoryFiles.PolicyFileContent content = path.isPresent() ? SecurityFrameworkService.readPolicyFile(repository, tree, path.get())
                    : SecurityPolicyRepositoryFiles.PolicyFileContent.failed(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH);
            SecurityPolicyDocuments.Parsed parsed = SecurityPolicyDocuments.parse(policyFile.policyPath(), content.content(), content.problem());
            policies.add(new SecurityPolicyEditorDTO.PolicyDTO(policyFile.policyPath(), content.content(), parsed.document(),
                    parsed.reason() == null ? null : parsed.reason().name(), parsed.errorLine(), policyFile.referencedBy()));
        }
        PersonIdent author = commit.getAuthorIdent();
        SecurityPolicyEditorDTO.CommitDTO lastCommit = new SecurityPolicyEditorDTO.CommitDTO(commit.name(), author.getName(),
                ZonedDateTime.ofInstant(author.getWhenAsInstant(), author.getZoneId()));
        return new SecurityPolicyEditorDTO(commit.name(), lastCommit, frameworkVersion(repository, tree), policies, presets);
    }

    private static @Nullable String frameworkVersion(Repository repository, RevTree tree) throws IOException {
        return SecurityPolicyRepositoryFiles.parseAresVersion(SecurityFrameworkService.readFile(repository, tree, SecurityFrameworkService.GRADLE_BUILD_FILE).orElse(null),
                SecurityFrameworkService.readFile(repository, tree, SecurityFrameworkService.MAVEN_BUILD_FILE).orElse(null)).orElse(null);
    }

    /**
     * Applies a change to the checkout: checks that the policies did not change since the editor loaded them, writes the policy files and points the listed
     * test classes to them.
     */
    private void applyChange(Repository repository, SecurityPolicyChangeDTO change) {
        Path root = repository.getLocalPath();
        try {
            requireUnchangedSince(repository, Objects.requireNonNull(change.baseCommitHash()));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SecurityFrameworkState state = SecurityFrameworkService.requireSupported(SecurityPolicyRepositoryFiles.evaluate(root));
        if (state.status() != SecurityActivationStatus.CONFIGURED) {
            throw new BadRequestAlertException("The security policies can only be edited while the Security Framework is active and configured.", ENTITY_NAME,
                    "securityPolicyNotConfigured");
        }
        Map<String, SecurityFrameworkState.PolicyFile> existing = state.policyFiles().stream()
                .collect(Collectors.toMap(SecurityFrameworkState.PolicyFile::policyPath, Function.identity()));
        @Nullable
        String frameworkVersion = SecurityPolicyRepositoryFiles.readAresVersion(root).orElse(null);
        Map<String, String> testClassPaths = SecurityPolicyRepositoryFiles.testClassPaths(SecurityPolicyRepositoryFiles.javaSources(root));
        Map<String, String> policyByTestClass = new HashMap<>();
        Set<String> newPolicies = new HashSet<>();
        Map<String, String> rendered = new LinkedHashMap<>();
        for (SecurityPolicyDocumentDTO document : change.policies()) {
            String path = requireValidPolicyPath(document.policyPath());
            @Nullable
            String previous = null;
            if (existing.containsKey(path)) {
                previous = SecurityPolicyRepositoryFiles.readPolicyFile(root, path).content();
                if (SecurityPolicyDocuments.parse(path, previous, existing.get(path).problem()).document() == null) {
                    throw new BadRequestAlertException("The policy " + path + " can only be changed in the repository, because the editor cannot keep all of its content.",
                            ENTITY_NAME, "securityPolicyReadOnly");
                }
            }
            else {
                if (Files.exists(root.resolve(path), LinkOption.NOFOLLOW_LINKS)) {
                    throw new BadRequestAlertException("The file " + path + " already exists in the tests repository.", ENTITY_NAME, "securityPolicyFileExists");
                }
                newPolicies.add(path);
            }
            for (String testClass : document.testClasses()) {
                if (testClassPaths.containsKey(testClass)) {
                    String other = policyByTestClass.put(testClass, path);
                    if (other != null && !other.equals(path)) {
                        throw new BadRequestAlertException("The test class " + testClass + " is assigned to the policies " + other + " and " + path + ".", ENTITY_NAME,
                                "securityPolicyTestClassAssignedTwice");
                    }
                }
            }
            String content = SecurityPolicyDocuments.render(document, previous);
            VersionCheck check = checkAgainstVersion(path, content, frameworkVersion);
            if (check.verdict() == AresCompatibilityManifest.Verdict.INCOMPATIBLE) {
                throw new BadRequestAlertException("The pinned Ares version " + frameworkVersion + " does not accept the policy: " + String.join("; ", check.problems()),
                        ENTITY_NAME, "securityPolicyVersionIncompatible");
            }
            rendered.put(path, content);
        }
        rendered.forEach((path, content) -> SecurityPolicyRepositoryFiles.writePolicy(root, path, content));
        Map<String, String> policyBySource = new TreeMap<>();
        policyByTestClass.forEach((testClass, path) -> policyBySource.put(testClassPaths.get(testClass), path));
        SecurityPolicyRepositoryFiles.pointTestClassesToPolicies(root, policyBySource);

        SecurityFrameworkState after = SecurityPolicyRepositoryFiles.evaluate(root);
        Set<String> referencedPolicies = after.policyFiles().stream().map(SecurityFrameworkState.PolicyFile::policyPath).collect(Collectors.toSet());
        for (String newPolicy : newPolicies) {
            if (!referencedPolicies.contains(newPolicy)) {
                throw new BadRequestAlertException("The new policy " + newPolicy + " is not used by any test class. Assign at least one test class to it.", ENTITY_NAME,
                        "securityPolicyUnused");
            }
        }
        if (after.status() != SecurityActivationStatus.CONFIGURED) {
            throw new BadRequestAlertException("The change would leave the Security Framework " + after.status() + ", so it was not committed.", ENTITY_NAME,
                    "securityPolicyBreaksConfiguration");
        }
    }

    /**
     * Refuses a change if a policy changed in the repository since the editor loaded the policies: a policy file, or which test classes point to which file.
     * Commits that change something else, e.g. a test, do not conflict.
     */
    private static void requireUnchangedSince(Repository repository, String baseCommitHash) throws IOException {
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head != null && head.name().equals(baseCommitHash)) {
            return;
        }
        ObjectId base = repository.resolve(baseCommitHash);
        try (RevWalk revWalk = new RevWalk(repository)) {
            if (base == null || head == null || !repository.getObjectDatabase().has(base)) {
                throw policiesChanged();
            }
            RevTree baseTree = revWalk.parseCommit(base).getTree();
            RevTree headTree = revWalk.parseCommit(head).getTree();
            SecurityFrameworkState baseState = SecurityFrameworkService.evaluate(repository, baseTree);
            SecurityFrameworkState headState = SecurityFrameworkService.evaluate(repository, headTree);
            if (!policyReferences(baseState).equals(policyReferences(headState))) {
                throw policiesChanged();
            }
            for (SecurityFrameworkState.PolicyFile policyFile : headState.policyFiles()) {
                Optional<String> path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath());
                if (path.isPresent() && !Objects.equals(SecurityFrameworkService.readPolicyFile(repository, baseTree, path.get()).content(),
                        SecurityFrameworkService.readPolicyFile(repository, headTree, path.get()).content())) {
                    throw policiesChanged();
                }
            }
        }
    }

    private static Map<String, List<String>> policyReferences(SecurityFrameworkState state) {
        return state.policyFiles().stream().collect(Collectors.toMap(SecurityFrameworkState.PolicyFile::policyPath, SecurityFrameworkState.PolicyFile::referencedBy));
    }

    private static ConflictException policiesChanged() {
        return new ConflictException("The security policies changed in the tests repository after the editor loaded them. Reload them and try again.", ENTITY_NAME,
                "securityPolicyChanged");
    }

    /**
     * Whether a pinned Ares version accepts a rendered policy.
     *
     * @param verdict  the verdict
     * @param problems why the version does not accept it; empty unless {@code INCOMPATIBLE}
     */
    record VersionCheck(AresCompatibilityManifest.Verdict verdict, List<String> problems) {
    }

    /**
     * Checks a rendered policy against the compatibility manifest of the pinned version and, for the version the rules of {@link SecurityPolicySchema} were
     * copied from, against those rules as well, which also check the values.
     */
    VersionCheck checkAgainstVersion(String policyPath, String content, @Nullable String frameworkVersion) {
        if (frameworkVersion == null) {
            return new VersionCheck(AresCompatibilityManifest.Verdict.UNVERIFIED, List.of());
        }
        AresCompatibilityManifest.Check check = securityFrameworkService.compatibilityManifest().check(frameworkVersion, content);
        List<String> problems = new ArrayList<>();
        SecurityFrameworkService.describeIncompatibility(policyPath, check).ifPresent(problems::add);
        if (problems.isEmpty() && SecurityPolicySchema.VERIFIED_ARES_VERSION.equals(frameworkVersion) && !SecurityPolicySchema.read(content).valid()) {
            problems.add(policyPath + ": " + describeInvalidValues(content));
        }
        if (!problems.isEmpty()) {
            return new VersionCheck(AresCompatibilityManifest.Verdict.INCOMPATIBLE, problems);
        }
        return new VersionCheck(check.verdict(), List.of());
    }

    /**
     * Names the value Ares most likely rejects. The rules only tell whether a policy is valid, so the common cases are named and the rest is described in
     * general.
     */
    private static String describeInvalidValues(String content) {
        Optional<SecurityPolicyDocumentDTO> document = Optional.ofNullable(SecurityPolicyDocuments.parse("", content, null).document());
        List<String> testCases = document.map(SecurityPolicyDocumentDTO::testClasses).orElse(List.of()).stream().filter(entry -> entry.contains("#")).toList();
        if (!testCases.isEmpty()) {
            return "single test cases (" + String.join(", ", testCases) + ") in theFollowingClassesAreTestClasses";
        }
        return "a value that the rules of Ares " + SecurityPolicySchema.VERIFIED_ARES_VERSION + " do not accept";
    }

    /**
     * @return the verdict that restricts more: incompatible over not verified over compatible
     */
    private static AresCompatibilityManifest.Verdict worse(AresCompatibilityManifest.Verdict first, AresCompatibilityManifest.Verdict second) {
        return severity(first) >= severity(second) ? first : second;
    }

    private static int severity(AresCompatibilityManifest.Verdict verdict) {
        return switch (verdict) {
            case COMPATIBLE -> 0;
            case UNVERIFIED -> 1;
            case INCOMPATIBLE -> 2;
        };
    }

    private static void requireValidDocuments(SecurityPolicyChangeDTO change) {
        Set<String> paths = new HashSet<>();
        for (SecurityPolicyDocumentDTO document : change.policies()) {
            String path = requireValidPolicyPath(document.policyPath());
            if (!paths.add(path)) {
                throw new BadRequestAlertException("The policy " + path + " is listed twice.", ENTITY_NAME, "securityPolicyDuplicate");
            }
            List<String> problems = SecurityPolicyDocuments.validate(document);
            if (!problems.isEmpty()) {
                throw new BadRequestAlertException("The policy " + path + " is not valid: " + String.join(" ", problems), ENTITY_NAME, "securityPolicyInvalid");
            }
        }
    }

    /**
     * A policy path must be valid for Ares, end with {@code .yaml} or {@code .yml}, and be writable as a plain string literal into a {@code @Policy}
     * annotation.
     */
    private static String requireValidPolicyPath(@Nullable String policyPath) {
        Optional<String> path = Optional.ofNullable(policyPath).flatMap(SecurityPolicyRepositoryFiles::validatePolicyPath)
                .filter(validPath -> POLICY_FILE_EXTENSIONS.stream().anyMatch(validPath::endsWith))
                .filter(validPath -> validPath.indexOf('"') < 0 && validPath.indexOf('\\') < 0 && validPath.chars().noneMatch(Character::isISOControl));
        return path.orElseThrow(() -> new BadRequestAlertException("The policy path " + policyPath + " is not valid.", ENTITY_NAME, "securityPolicyInvalidPath"));
    }

    private static String commitMessage(@Nullable String commitMessage) {
        if (commitMessage == null || commitMessage.isBlank()) {
            return DEFAULT_COMMIT_MESSAGE;
        }
        String message = commitMessage.strip();
        return message.length() > MAX_COMMIT_MESSAGE_LENGTH ? message.substring(0, MAX_COMMIT_MESSAGE_LENGTH) : message;
    }
}
