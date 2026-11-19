package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.ENTITY_NAME;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
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

    /** The trailer of a policy commit that identifies its change, see {@link #fingerprint(SecurityPolicyChangeDTO)}. */
    private static final String CHANGE_TRAILER = "Security-Policy-Change: ";

    private static final String DEFAULT_COMMIT_MESSAGE = "Update the Ares security policy";

    private static final int MAX_COMMIT_MESSAGE_LENGTH = 1000;

    /** The template repository of an exercise is small, but the list is capped so a repository with generated files cannot flood the client. */
    static final int MAX_TEMPLATE_FILES = 5000;

    private static final Set<String> POLICY_FILE_EXTENSIONS = Set.of(".yaml", ".yml");

    private final SecurityFrameworkService securityFrameworkService;

    private final BareGitRepositoryService bareGitRepositoryService;

    private final JsonMapper jsonMapper;

    private final List<SecurityPolicyEditorDTO.PresetDTO> presets;

    public SecurityPolicyEditorService(SecurityFrameworkService securityFrameworkService, BareGitRepositoryService bareGitRepositoryService,
            ResourceLoaderService resourceLoaderService, JsonMapper jsonMapper) {
        this.securityFrameworkService = securityFrameworkService;
        this.bareGitRepositoryService = bareGitRepositoryService;
        this.jsonMapper = jsonMapper;
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
        return readTestRepository(exercise, (repository, commit) -> editorAt(repository, commit, exercise.getPackageName()));
    }

    /**
     * Renders the changed files without committing them, and checks the policies against the Ares version the tests repository pins after the change.
     *
     * @param exercise the programming exercise
     * @param change   the changes
     * @return the files before and after the change and whether the pinned version accepts the policies
     */
    public SecurityPolicyPreviewDTO preview(ProgrammingExercise exercise, SecurityPolicyChangeDTO change) {
        securityFrameworkService.validateJavaExercise(exercise);
        requireValidChange(change);
        return readTestRepository(exercise, (repository, commit) -> {
            RevTree tree = commit.getTree();
            @Nullable
            String gradleBuild = SecurityFrameworkCommitFiles.readFile(repository, tree, SecurityFrameworkService.GRADLE_BUILD_FILE).orElse(null);
            @Nullable
            String mavenPom = SecurityFrameworkCommitFiles.readFile(repository, tree, SecurityFrameworkService.MAVEN_BUILD_FILE).orElse(null);
            @Nullable
            String pinned = SecurityPolicyRepositoryFiles.parseAresVersion(gradleBuild, mavenPom).orElse(null);
            @Nullable
            String frameworkVersion = change.frameworkVersion() != null ? change.frameworkVersion() : pinned;
            Set<String> existingPaths = SecurityFrameworkCommitFiles.evaluate(repository, tree).policyFiles().stream().map(SecurityFrameworkState.PolicyFile::policyPath)
                    .collect(Collectors.toCollection(TreeSet::new));
            List<SecurityPolicyPreviewDTO.FileDTO> files = new ArrayList<>();
            Set<String> changedPaths = new HashSet<>();
            for (SecurityPolicyChangeDTO.PolicyChangeDTO policy : change.policies()) {
                String path = requireValidPolicyPath(policy.document().policyPath());
                @Nullable
                String previousPath = policy.previousPolicyPath() == null ? null : requireValidPolicyPath(policy.previousPolicyPath());
                @Nullable
                String before = previousPath != null && existingPaths.contains(previousPath) ? SecurityFrameworkCommitFiles.readPolicyFile(repository, tree, previousPath).content()
                        : null;
                files.add(new SecurityPolicyPreviewDTO.FileDTO(path, previousPath, before, SecurityPolicyDocuments.render(policy.document(), before)));
                changedPaths.add(previousPath == null ? path : previousPath);
            }
            for (String removedPath : change.removedPolicyPaths()) {
                String path = requireValidPolicyPath(removedPath);
                @Nullable
                String before = existingPaths.contains(path) ? SecurityFrameworkCommitFiles.readPolicyFile(repository, tree, path).content() : null;
                files.add(new SecurityPolicyPreviewDTO.FileDTO(path, path, before, null));
                changedPaths.add(path);
            }
            List<String> problems = new ArrayList<>();
            AresCompatibilityManifest.Verdict verdict = AresCompatibilityManifest.Verdict.COMPATIBLE;
            for (SecurityPolicyPreviewDTO.FileDTO file : files) {
                if (file.after() != null) {
                    VersionCheck check = checkAgainstVersion(file.path(), file.after(), frameworkVersion);
                    problems.addAll(check.problems());
                    verdict = worse(verdict, check.verdict());
                }
            }
            if (frameworkVersion != null && !frameworkVersion.equals(pinned)) {
                // Another version also has to accept the policies the change keeps as they are.
                for (String path : existingPaths) {
                    @Nullable
                    String content = changedPaths.contains(path) ? null : SecurityFrameworkCommitFiles.readPolicyFile(repository, tree, path).content();
                    if (content != null) {
                        VersionCheck check = checkAgainstVersion(path, content, frameworkVersion);
                        problems.addAll(check.problems());
                        verdict = worse(verdict, check.verdict());
                    }
                }
                SecurityPolicyRepositoryFiles.pinAresVersion(gradleBuild, mavenPom, frameworkVersion)
                        .ifPresent(build -> files.add(new SecurityPolicyPreviewDTO.FileDTO(build.path(), build.path(), build.before(), build.after())));
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
     * Applies the changes to the tests repository in one commit and pushes it: writes, renames and deletes policy files, rewrites the {@code @Policy}
     * annotations so the assigned tests use their policy, and pins another Ares version if requested.
     *
     * @param exercise the programming exercise
     * @param change   the changes, with the commit the editor loaded the policies from
     * @param user     the acting user, who authors the commit
     * @return the policies and the Security Framework state after the commit
     */
    public SecurityPolicyCommitResultDTO commit(ProgrammingExercise exercise, SecurityPolicyChangeDTO change, User user) {
        securityFrameworkService.validateJavaExercise(exercise);
        requireValidChange(change);
        if (change.policies().isEmpty() && change.removedPolicyPaths().isEmpty() && change.frameworkVersion() == null) {
            throw new BadRequestAlertException("The change contains nothing to commit.", ENTITY_NAME, "securityPolicyNoChange");
        }
        String baseCommitHash = change.baseCommitHash();
        if (baseCommitHash == null || !ObjectId.isId(baseCommitHash)) {
            throw new BadRequestAlertException("The change does not name the commit the policies were loaded from.", ENTITY_NAME, "securityPolicyNoBaseCommit");
        }
        String fingerprint = fingerprint(change);
        SecurityFrameworkConfigDTO config = securityFrameworkService.changeTestRepository(exercise, user, commitMessage(change.commitMessage()), POLICY_ACTION,
                CHANGE_TRAILER + fingerprint + "\n", true, repository -> {
                    if (!isAlreadyApplied(repository, fingerprint)) {
                        applyChange(repository, change, baseCommitHash);
                    }
                });
        log.info("User {} changed the security policies {} and removed {} of programming exercise {}", user.getLogin(),
                change.policies().stream().map(policy -> policy.document().policyPath()).toList(), change.removedPolicyPaths(), exercise.getId());
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
            return AresTestCases.structure(SecurityFrameworkCommitFiles.javaSources(repository, commit.getTree()));
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

    private SecurityPolicyEditorDTO editorAt(Repository repository, RevCommit commit, @Nullable String packageName) throws IOException {
        RevTree tree = commit.getTree();
        SecurityFrameworkState state = SecurityFrameworkCommitFiles.evaluate(repository, tree);
        Map<String, List<String>> assignedTests = SecurityTestAssignments
                .entriesByPolicy(SecurityTestAssignments.read(SecurityFrameworkCommitFiles.javaSources(repository, tree)).values());
        List<SecurityPolicyEditorDTO.PolicyDTO> policies = new ArrayList<>();
        for (SecurityFrameworkState.PolicyFile policyFile : state.policyFiles()) {
            Optional<String> path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath());
            SecurityPolicyRepositoryFiles.PolicyFileContent content = path.isPresent() ? SecurityFrameworkCommitFiles.readPolicyFile(repository, tree, path.get())
                    : SecurityPolicyRepositoryFiles.PolicyFileContent.failed(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH);
            SecurityPolicyDocuments.Parsed parsed = SecurityPolicyDocuments.parse(policyFile.policyPath(), content.content(), content.problem());
            policies.add(new SecurityPolicyEditorDTO.PolicyDTO(policyFile.policyPath(), content.content(), parsed.document(),
                    parsed.reason() == null ? null : parsed.reason().name(), parsed.errorLine(), assignedTests.getOrDefault(policyFile.policyPath(), List.of())));
        }
        PersonIdent author = commit.getAuthorIdent();
        SecurityPolicyEditorDTO.CommitDTO lastCommit = new SecurityPolicyEditorDTO.CommitDTO(commit.name(), author.getName(),
                ZonedDateTime.ofInstant(author.getWhenAsInstant(), author.getZoneId()));
        SecurityPolicyRepositoryFiles.BuildFiles buildFiles = new SecurityPolicyRepositoryFiles.BuildFiles(
                SecurityFrameworkCommitFiles.readFile(repository, tree, SecurityFrameworkService.GRADLE_BUILD_FILE).orElse(null),
                SecurityFrameworkCommitFiles.readFile(repository, tree, SecurityFrameworkService.MAVEN_BUILD_FILE).orElse(null));
        SecurityPolicyDocumentDTO newPolicyTemplate = SecurityPolicyRepositoryFiles
                .detectBuildTool(buildFiles.gradleBuild(), buildFiles.mavenPom()).map(buildTool -> new SecurityPolicyDocumentDTO("", null, buildTool.aresLanguageConfiguration(),
                        packageName == null || packageName.isBlank() ? null : packageName, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null))
                .orElse(null);
        return new SecurityPolicyEditorDTO(commit.name(), lastCommit, SecurityPolicyRepositoryFiles.parseAresVersion(buildFiles.gradleBuild(), buildFiles.mavenPom()).orElse(null),
                policies, presets, BuildFileSyntax.attachesAresAgent(buildFiles), newPolicyTemplate);
    }

    /**
     * Identifies a change, so that a retry of the same request is recognized, see {@link #isAlreadyApplied(Repository, String)}.
     *
     * @return the SHA-256 hash of the change in JSON, as hexadecimal digits
     */
    private String fingerprint(SecurityPolicyChangeDTO change) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jsonMapper.writeValueAsBytes(change)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Whether the head of the checkout is the commit of this very change. That is the case when a retry follows a request whose push succeeded but whose
     * processing failed: the change must not be applied again, which would fail as a conflict with itself, and the pending processing is resumed instead.
     * The fingerprint covers the whole request, including the commit the editor loaded, so the head may follow an unrelated commit that was allowed in
     * between, while a different change, even of the same base commit, has another fingerprint and still fails as a conflict.
     */
    private static boolean isAlreadyApplied(Repository repository, String fingerprint) {
        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(Constants.HEAD);
            if (head == null) {
                return false;
            }
            return revWalk.parseCommit(head).getFullMessage().lines().anyMatch(line -> line.equals(CHANGE_TRAILER + fingerprint));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Applies a change to the checkout: checks that the policies did not change since the editor loaded them, pins the requested version, writes, renames and
     * deletes the policy files and rewrites the {@code @Policy} annotations of the test classes whose assignment changes.
     */
    private void applyChange(Repository repository, SecurityPolicyChangeDTO change, String baseCommitHash) {
        Path root = repository.getLocalPath();
        try {
            requireUnchangedSince(repository, baseCommitHash);
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
        if (change.frameworkVersion() != null && !change.frameworkVersion().equals(frameworkVersion)) {
            securityFrameworkService.validateFrameworkVersion(change.frameworkVersion());
            SecurityPolicyRepositoryFiles.writeAresVersion(root, change.frameworkVersion());
            frameworkVersion = change.frameworkVersion();
        }
        boolean javaAgent = BuildFileSyntax.attachesAresAgent(SecurityPolicyRepositoryFiles.buildFiles(root));

        // The paths the change frees: deleted policies and the old paths of renamed ones.
        Set<String> removed = new LinkedHashSet<>();
        for (String removedPath : change.removedPolicyPaths()) {
            removed.add(requireExistingPolicy(existing, requireValidPolicyPath(removedPath)));
        }
        Set<String> vacated = new HashSet<>(removed);
        Set<String> touched = new HashSet<>(removed);
        for (SecurityPolicyChangeDTO.PolicyChangeDTO policy : change.policies()) {
            if (policy.previousPolicyPath() != null) {
                String previousPath = requireExistingPolicy(existing, requireValidPolicyPath(policy.previousPolicyPath()));
                if (!touched.add(previousPath)) {
                    throw refused("The policy " + previousPath + " is listed twice.", "securityPolicyDuplicate", Map.of("path", previousPath));
                }
                if (!previousPath.equals(policy.document().policyPath())) {
                    vacated.add(previousPath);
                }
            }
        }

        Map<String, String> rendered = new LinkedHashMap<>();
        for (SecurityPolicyChangeDTO.PolicyChangeDTO policy : change.policies()) {
            SecurityPolicyDocumentDTO document = policy.document();
            String path = requireValidPolicyPath(document.policyPath());
            @Nullable
            String previous = null;
            @Nullable
            String previousLanguageConfiguration = null;
            if (policy.previousPolicyPath() != null) {
                String previousPath = requireValidPolicyPath(policy.previousPolicyPath());
                previous = SecurityPolicyRepositoryFiles.readPolicyFile(root, previousPath).content();
                SecurityPolicyDocumentDTO previousDocument = SecurityPolicyDocuments.parse(previousPath, previous, existing.get(previousPath).problem()).document();
                if (previousDocument == null) {
                    throw refused("The policy " + previousPath + " can only be changed in the repository, because the editor cannot keep all of its content.",
                            "securityPolicyReadOnly", Map.of("path", previousPath));
                }
                previousLanguageConfiguration = previousDocument.languageConfiguration();
            }
            boolean samePath = path.equals(policy.previousPolicyPath());
            if (!samePath && !vacated.contains(path) && Files.exists(root.resolve(path), LinkOption.NOFOLLOW_LINKS)) {
                throw refused("The file " + path + " already exists in the tests repository.", "securityPolicyFileExists", Map.of("path", path));
            }
            // A policy that already scans dependencies may keep doing so, so that the editor can still change it; only switching scanning on needs the agent.
            if (usesInstrumentation(document.languageConfiguration()) && !javaAgent && !usesInstrumentation(previousLanguageConfiguration)) {
                throw refused("The build of the tests repository does not attach the Ares agent, which scanning dependencies needs.", "securityPolicyAgentMissing",
                        Map.of("path", path));
            }
            String content = SecurityPolicyDocuments.render(document, previous);
            VersionCheck check = checkAgainstVersion(path, content, frameworkVersion);
            if (check.verdict() == AresCompatibilityManifest.Verdict.INCOMPATIBLE) {
                throw versionIncompatible(frameworkVersion, check.problems());
            }
            rendered.put(path, content);
        }
        if (change.frameworkVersion() != null) {
            // Another version also has to accept the policies the change keeps as they are.
            for (String path : existing.keySet()) {
                if (!touched.contains(path)) {
                    @Nullable
                    String content = SecurityPolicyRepositoryFiles.readPolicyFile(root, path).content();
                    VersionCheck check = content == null ? new VersionCheck(AresCompatibilityManifest.Verdict.UNVERIFIED, List.of())
                            : checkAgainstVersion(path, content, frameworkVersion);
                    if (check.verdict() == AresCompatibilityManifest.Verdict.INCOMPATIBLE) {
                        throw versionIncompatible(frameworkVersion, check.problems());
                    }
                }
            }
        }

        Map<String, SecurityTestAssignments.TestClass> testClasses = SecurityTestAssignments.read(SecurityPolicyRepositoryFiles.javaSources(root));
        Map<String, String> requested = requestedEntries(change, testClasses, touched);
        try {
            requireKnownTestCasesOfSplitClasses(repository, baseCommitHash, testClasses, requested);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        vacated.forEach(path -> SecurityPolicyRepositoryFiles.deletePolicyFile(root, path));
        rendered.forEach((path, content) -> SecurityPolicyRepositoryFiles.writePolicy(root, path, content));
        List<SecurityTestAssignments.TestClass> reassigned = new ArrayList<>();
        for (SecurityTestAssignments.TestClass testClass : testClasses.values()) {
            Map<String, String> entries = new LinkedHashMap<>();
            requested.forEach((entry, path) -> {
                if (SecurityTestAssignments.testClassOf(entry).equals(testClass.qualifiedName())) {
                    entries.put(entry, path);
                }
            });
            SecurityTestAssignments.TestClass assigned = testClass.with(entries);
            // A class annotation that points to a renamed or deleted file changes too, even if none of the test cases used it.
            boolean pointsToVacated = testClass.classPolicyPath() != null && vacated.contains(testClass.classPolicyPath());
            if (testClass.active() && (pointsToVacated || !assigned.entries().equals(testClass.entries()))) {
                requireKeptOverloadPolicy(testClass, assigned);
                requireKeptInheritedPolicy(testClass, assigned);
                reassigned.add(assigned);
            }
        }
        SecurityTestAssignments.assignTestCases(root, reassigned);

        SecurityFrameworkState after = SecurityPolicyRepositoryFiles.evaluate(root);
        Set<String> referencedPolicies = after.policyFiles().stream().map(SecurityFrameworkState.PolicyFile::policyPath).collect(Collectors.toSet());
        for (String path : rendered.keySet()) {
            if (!referencedPolicies.contains(path)) {
                throw refused("No test uses the policy " + path + ". Assign at least one test case to it.", "securityPolicyUnused", Map.of("path", path));
            }
        }
        if (after.status() != SecurityActivationStatus.CONFIGURED) {
            throw refused("The change would leave the Security Framework " + after.status() + ", so it was not committed.", "securityPolicyBreaksConfiguration",
                    Map.of("status", after.status().name()));
        }
    }

    /**
     * The test methods a class inherits from another test class are not among its test cases, so they cannot be assigned one by one; they follow its class
     * annotation, which therefore must not lose its policy file unnoticed. Only a class the editor shows as assigned as a whole may be released completely;
     * releasing some of its test cases, or the last one the editor shows of a class assigned case by case, would release the inherited ones as well.
     */
    private static void requireKeptInheritedPolicy(SecurityTestAssignments.TestClass testClass, SecurityTestAssignments.TestClass assigned) {
        String classPolicyPath = testClass.classPolicyPath();
        boolean releasedAsAWhole = testClass.entries().containsKey(testClass.qualifiedName()) && assigned.methodPolicyPaths().isEmpty();
        if (!testClass.inheritsTests() || classPolicyPath == null || assigned.classPolicyPath() != null || releasedAsAWhole) {
            return;
        }
        throw refused(
                "The test class " + testClass.qualifiedName() + " inherits test methods that use the policy " + classPolicyPath
                        + " through its class annotation. Assign the whole class to a policy, or release it completely.",
                "securityPolicyInheritedTestsLosePolicy", Map.of("testClass", testClass.qualifiedName(), "path", classPolicyPath));
    }

    /**
     * Test methods that share a name cannot be assigned one by one; they follow the class annotation, which therefore must not lose its policy file under
     * them unnoticed, e.g. when the file is renamed while the class is assigned case by case.
     */
    private static void requireKeptOverloadPolicy(SecurityTestAssignments.TestClass testClass, SecurityTestAssignments.TestClass assigned) {
        String classPolicyPath = testClass.classPolicyPath();
        // A class assigned as a whole shows the overloads as part of it, so releasing or deleting that assignment is what the instructor asked for.
        boolean assignedAsAWhole = testClass.entries().containsKey(testClass.qualifiedName());
        // The annotations are read by the name of the method, so whether every overload has one of its own is unknown; any of them may use the class policy.
        if (classPolicyPath == null || assigned.classPolicyPath() != null || assignedAsAWhole || testClass.overloaded().isEmpty()) {
            return;
        }
        String overload = testClass.overloaded().stream().sorted().findFirst().orElseThrow();
        throw refused(
                "The test class " + testClass.qualifiedName() + " has several test methods called " + overload + ", which can use the policy " + classPolicyPath
                        + " of the class only through the class annotation. Assign the whole class to a policy, or change these annotations in the repository.",
                "securityPolicyOverloadsLosePolicy", Map.of("testClass", testClass.qualifiedName(), "test", overload, "path", classPolicyPath));
    }

    /**
     * The policy of every assigned test after the change: the policies the change does not touch keep their tests, and each listed policy gets the tests it
     * lists. A test can only use one policy, and a whole test class cannot be assigned together with one of its test cases.
     *
     * @param touched the paths in the repository of the policies the change rewrites, renames or deletes
     * @return the policy path by entry, see {@link SecurityTestAssignments}
     */
    private static Map<String, String> requestedEntries(SecurityPolicyChangeDTO change, Map<String, SecurityTestAssignments.TestClass> testClasses, Set<String> touched) {
        Map<String, String> requested = new LinkedHashMap<>();
        testClasses.values().forEach(testClass -> testClass.entries().forEach((entry, path) -> {
            if (!touched.contains(path)) {
                requested.put(entry, path);
            }
        }));
        for (SecurityPolicyChangeDTO.PolicyChangeDTO policy : change.policies()) {
            String path = requireValidPolicyPath(policy.document().policyPath());
            Set<String> declared = new HashSet<>(policy.document().testClasses());
            for (String entry : policy.assignedTests()) {
                SecurityTestAssignments.TestClass testClass = testClasses.get(SecurityTestAssignments.testClassOf(entry));
                if (testClass == null || !SecurityTestAssignments.isValidEntry(testClass, entry)) {
                    throw refused("The tests repository has no test " + entry + ".", "securityPolicyUnknownTest", Map.of("test", entry));
                }
                if (!testClass.active()) {
                    throw refused("The test class " + testClass.qualifiedName() + " runs without the sandbox, so its tests cannot use a policy.", "securityPolicyTestSwitchedOff",
                            Map.of("testClass", testClass.qualifiedName()));
                }
                if (SecurityTestAssignments.isSwitchedOffEntry(testClass, entry)) {
                    throw refused("The test " + entry + " runs without the sandbox, so it cannot use a policy.", "securityPolicyTestCaseSwitchedOff", Map.of("test", entry));
                }
                if (SecurityTestAssignments.isOverloadedEntry(testClass, entry)) {
                    throw refused("More than one test method is called " + entry + ", so they can only use a policy with their whole class.", "securityPolicyTestCaseOverloaded",
                            Map.of("test", entry));
                }
                if (!declared.contains(testClass.qualifiedName())) {
                    throw refused("The policy " + path + " does not declare the test class " + testClass.qualifiedName() + " of its test " + entry + ".",
                            "securityPolicyTestClassNotDeclared", Map.of("path", path, "testClass", testClass.qualifiedName()));
                }
                String other = requested.put(entry, path);
                if (other != null && !other.equals(path)) {
                    throw assignedTwice(entry, other, path);
                }
            }
        }
        Map<String, String> wholeClasses = new HashMap<>();
        requested.forEach((entry, path) -> {
            if (entry.indexOf(SecurityTestAssignments.METHOD_SEPARATOR) < 0) {
                wholeClasses.put(entry, path);
            }
        });
        requested.forEach((entry, path) -> {
            String wholeClass = wholeClasses.get(SecurityTestAssignments.testClassOf(entry));
            if (wholeClass != null && !entry.equals(SecurityTestAssignments.testClassOf(entry))) {
                throw assignedTwice(entry, wholeClass, path);
            }
        });
        return requested;
    }

    private static String requireExistingPolicy(Map<String, SecurityFrameworkState.PolicyFile> existing, String path) {
        if (!existing.containsKey(path)) {
            throw refused("No test uses the policy " + path + " in the tests repository.", "securityPolicyNotFound", Map.of("path", path));
        }
        return path;
    }

    private static boolean usesInstrumentation(@Nullable String languageConfiguration) {
        return languageConfiguration != null && languageConfiguration.endsWith("_INSTRUMENTATION");
    }

    private static BadRequestAlertException assignedTwice(String test, String first, String second) {
        return refused("The test " + test + " is assigned to the policies " + first + " and " + second + ".", "securityPolicyTestAssignedTwice",
                Map.of("test", test, "first", first, "second", second));
    }

    private static BadRequestAlertException versionIncompatible(@Nullable String frameworkVersion, List<String> problems) {
        return refused("The pinned Ares version " + frameworkVersion + " does not accept the policy: " + String.join("; ", problems), "securityPolicyVersionIncompatible",
                Map.of("version", Objects.requireNonNullElse(frameworkVersion, ""), "details", String.join("; ", problems)));
    }

    /**
     * Refuses a change if a policy changed in the repository since the editor loaded the policies: a policy file, which test classes point to which file, an
     * annotation of a test class or test method, e.g. a new override without a policy file, or the entries of a policy, e.g. a test added to a class assigned
     * case by case, because the change rewrites the annotations from the state it was loaded with. Commits that change something else, e.g. the body of a
     * test, do not conflict.
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
            SecurityFrameworkState baseState = SecurityFrameworkCommitFiles.evaluate(repository, baseTree);
            SecurityFrameworkState headState = SecurityFrameworkCommitFiles.evaluate(repository, headTree);
            if (!policyReferences(baseState).equals(policyReferences(headState)) || !assignmentState(repository, baseTree).equals(assignmentState(repository, headTree))) {
                throw policiesChanged();
            }
            for (SecurityFrameworkState.PolicyFile policyFile : headState.policyFiles()) {
                Optional<String> path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath());
                if (path.isPresent() && !Objects.equals(SecurityFrameworkCommitFiles.readPolicyFile(repository, baseTree, path.get()).content(),
                        SecurityFrameworkCommitFiles.readPolicyFile(repository, headTree, path.get()).content())) {
                    throw policiesChanged();
                }
            }
        }
    }

    /**
     * Refuses a change that assigns some test cases of a class assigned as a whole if the class got test cases since the editor loaded the policies. They use
     * the policy of the class, and splitting the class would release them although the editor never showed them. A test added to a class that stays assigned
     * as a whole, or that is released as a whole, keeps following the class and does not conflict.
     */
    private static void requireKnownTestCasesOfSplitClasses(Repository repository, String baseCommitHash, Map<String, SecurityTestAssignments.TestClass> testClasses,
            Map<String, String> requested) throws IOException {
        Set<String> assignedCaseByCase = new HashSet<>();
        for (String entry : requested.keySet()) {
            if (entry.indexOf(SecurityTestAssignments.METHOD_SEPARATOR) >= 0) {
                assignedCaseByCase.add(SecurityTestAssignments.testClassOf(entry));
            }
        }
        List<SecurityTestAssignments.TestClass> split = new ArrayList<>();
        for (SecurityTestAssignments.TestClass testClass : testClasses.values()) {
            if (testClass.entries().containsKey(testClass.qualifiedName()) && assignedCaseByCase.contains(testClass.qualifiedName())) {
                split.add(testClass);
            }
        }
        ObjectId head = repository.resolve(Constants.HEAD);
        if (split.isEmpty() || (head != null && head.name().equals(baseCommitHash))) {
            return;
        }
        ObjectId base = repository.resolve(baseCommitHash);
        if (base == null) {
            throw policiesChanged();
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevTree baseTree = revWalk.parseCommit(base).getTree();
            Map<String, SecurityTestAssignments.TestClass> loaded = SecurityTestAssignments.read(SecurityFrameworkCommitFiles.javaSources(repository, baseTree));
            for (SecurityTestAssignments.TestClass testClass : split) {
                SecurityTestAssignments.TestClass loadedClass = loaded.get(testClass.qualifiedName());
                if (loadedClass == null || !new HashSet<>(loadedClass.testCases()).containsAll(testClass.testCases())) {
                    throw policiesChanged();
                }
            }
        }
    }

    /**
     * The {@code @Policy} annotations of a test class, without its test cases, which a change of the tests may change without changing a policy.
     *
     * @param active            whether its annotation enforces a policy
     * @param classPolicyPath   the policy file of its annotation
     * @param methodPolicyPaths the policy files of the annotations of its test methods
     * @param defaultMethods    the test methods whose annotation gives them the strict default policy
     * @param switchedOff       the test methods whose annotation switches the sandbox off
     */
    private record Annotations(boolean active, @Nullable String classPolicyPath, Map<String, String> methodPolicyPaths, Set<String> defaultMethods, Set<String> switchedOff) {
    }

    /**
     * The state of the {@code @Policy} annotations of a commit that a change is based on: the annotations of each test class and the entries they give each
     * policy. A test added to a class assigned case by case changes the entries, because the change would assign the class without it; a test added to a
     * class assigned as a whole does not, and neither does the body of a test.
     */
    private record AssignmentState(Map<String, Annotations> annotations, Map<String, List<String>> entries) {
    }

    private static AssignmentState assignmentState(Repository repository, RevTree tree) throws IOException {
        Map<String, SecurityTestAssignments.TestClass> testClasses = SecurityTestAssignments.read(SecurityFrameworkCommitFiles.javaSources(repository, tree));
        Map<String, Annotations> annotations = testClasses.values().stream().collect(Collectors.toMap(SecurityTestAssignments.TestClass::qualifiedName,
                testClass -> new Annotations(testClass.active(), testClass.classPolicyPath(), testClass.methodPolicyPaths(), testClass.defaultMethods(), testClass.switchedOff())));
        return new AssignmentState(annotations, SecurityTestAssignments.entriesByPolicy(testClasses.values()));
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

    private static void requireValidChange(SecurityPolicyChangeDTO change) {
        Set<String> paths = new HashSet<>();
        for (SecurityPolicyChangeDTO.PolicyChangeDTO policy : change.policies()) {
            if (policy.document() == null) {
                throw new BadRequestAlertException("A changed policy has no content.", ENTITY_NAME, "securityPolicyNoChange");
            }
            String path = requireValidPolicyPath(policy.document().policyPath());
            if (!paths.add(path)) {
                throw refused("The policy " + path + " is listed twice.", "securityPolicyDuplicate", Map.of("path", path));
            }
            List<String> problems = SecurityPolicyDocuments.validate(policy.document());
            if (!problems.isEmpty()) {
                throw refused("The policy " + path + " is not valid: " + String.join(" ", problems), "securityPolicyInvalid",
                        Map.of("path", path, "details", String.join(" ", problems)));
            }
        }
        for (String removedPath : change.removedPolicyPaths()) {
            String path = requireValidPolicyPath(removedPath);
            if (paths.contains(path)) {
                throw refused("The policy " + path + " is listed twice.", "securityPolicyDuplicate", Map.of("path", path));
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
        return path.orElseThrow(() -> refused("The policy path " + policyPath + " is not valid.", "securityPolicyInvalidPath", Map.of("path", String.valueOf(policyPath))));
    }

    /**
     * @return the error that refuses a request; the client shows its translation with the parameters, so the message names the translation
     */
    private static BadRequestAlertException refused(String defaultMessage, String errorKey, Map<String, Object> params) {
        return new BadRequestAlertException(ErrorConstants.PARAMETERIZED_TYPE, defaultMessage, ENTITY_NAME, errorKey, Map.of("message", "error." + errorKey, "params", params));
    }

    private static String commitMessage(@Nullable String commitMessage) {
        if (commitMessage == null || commitMessage.isBlank()) {
            return DEFAULT_COMMIT_MESSAGE;
        }
        String message = commitMessage.strip();
        return message.length() > MAX_COMMIT_MESSAGE_LENGTH ? message.substring(0, MAX_COMMIT_MESSAGE_LENGTH) : message;
    }
}
