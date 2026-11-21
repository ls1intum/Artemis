package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.ENTITY_NAME;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.THE_POLICY;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.holdsPolicy;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.invalidPath;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.liesBelowAFile;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.listedTwice;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.reassignedTestClasses;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.refused;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.requestedEntries;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.requireExistingPolicy;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.requireValidChange;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.requireValidPolicyPath;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.usesInstrumentation;
import static de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyChangeRules.versionIncompatible;

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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
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
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;
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
    private static final String CHANGE_TRAILER_KEY = "Security-Policy-Change";

    private static final String CHANGE_TRAILER = CHANGE_TRAILER_KEY + ": ";

    private static final String DEFAULT_COMMIT_MESSAGE = "Update the Ares security policy";

    private static final int MAX_COMMIT_MESSAGE_LENGTH = 1000;

    /** The template repository of an exercise is small, but the list is capped so a repository with generated files cannot flood the client. */
    static final int MAX_TEMPLATE_FILES = 5000;

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
     * @return the files before and after the change, including the test classes whose annotations it rewrites, and whether the pinned version accepts the policies
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
                // Only a policy file is deleted; for another path, e.g. a directory or an unrelated YAML file, the change removes the reference only.
                if (before != null && SecurityPolicyYaml.isPolicyShaped(before)) {
                    files.add(new SecurityPolicyPreviewDTO.FileDTO(path, path, before, null));
                }
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
            files.addAll(rewrittenTestClasses(repository, tree, change));
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
        securityFrameworkService.changeTestRepository(exercise, user, commitMessage(change.commitMessage()), POLICY_ACTION, CHANGE_TRAILER + fingerprint + "\n", true,
                repository -> {
                    Optional<String> appliedCommit = appliedCommit(repository, fingerprint, baseCommitHash);
                    if (appliedCommit.isEmpty()) {
                        applyChange(repository, change, baseCommitHash);
                    }
                    else {
                        SecurityPolicyConflicts.requireStillApplied(repository, appliedCommit.get());
                    }
                });
        log.info("User {} changed the security policies {} and removed the references to {} of programming exercise {}", user.getLogin(),
                change.policies().stream().map(policy -> policy.document().policyPath()).toList(), change.removedPolicyPaths(), exercise.getId());
        // The policies and the state are read from one commit, so a commit pushed right after this one cannot mix two states in the answer.
        return readTestRepository(exercise, (repository, commit) -> commitResultAt(repository, commit, exercise.getPackageName()));
    }

    /**
     * Reads the policies and the Security Framework state from the head commit of the tests repository, e.g. after a change was pushed whose processing
     * failed, so the editor and the sandbox switch show the same commit.
     *
     * @param exercise the programming exercise
     * @return the policies and the state of one commit
     */
    public SecurityPolicyCommitResultDTO getEditorWithConfig(ProgrammingExercise exercise) {
        securityFrameworkService.validateJavaExercise(exercise);
        return readTestRepository(exercise, (repository, commit) -> commitResultAt(repository, commit, exercise.getPackageName()));
    }

    private SecurityPolicyCommitResultDTO commitResultAt(Repository repository, RevCommit commit, @Nullable String packageName) throws IOException {
        return new SecurityPolicyCommitResultDTO(editorAt(repository, commit, packageName), securityFrameworkService.configAt(repository, commit.getTree()));
    }

    /**
     * Lists the test classes of a commit of the tests repository with their test cases, so that the editor shows the test cases of the commit it loaded the
     * policies from, e.g. the ones a split releases, even if the tests changed since.
     *
     * @param exercise   the programming exercise
     * @param commitHash the commit to read; null for the head commit
     * @return the test structure
     */
    public SecurityTestStructureDTO getTestStructure(ProgrammingExercise exercise, @Nullable String commitHash) {
        securityFrameworkService.validateJavaExercise(exercise);
        return readTestRepository(exercise, (repository, head) -> {
            RevCommit commit = head;
            if (commitHash != null && !commitHash.equals(head.name())) {
                ObjectId id = ObjectId.isId(commitHash) ? ObjectId.fromString(commitHash) : null;
                String message = "The tests repository has no commit " + commitHash + ". Reload the policies and try again.";
                if (id == null || !repository.getObjectDatabase().has(id)) {
                    throw new ConflictException(message, ENTITY_NAME, "securityPolicyChanged");
                }
                try (RevWalk revWalk = new RevWalk(repository)) {
                    commit = revWalk.parseCommit(id);
                }
                catch (IncorrectObjectTypeException _) {
                    // The id names another object of the repository, e.g. a file, so it is no commit the editor could have loaded.
                    throw new ConflictException(message, ENTITY_NAME, "securityPolicyChanged");
                }
            }
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
                while (!truncated && treeWalk.next()) {
                    if ((treeWalk.getRawMode(0) & FileMode.TYPE_MASK) == FileMode.TYPE_FILE) {
                        truncated = paths.size() == MAX_TEMPLATE_FILES;
                        if (!truncated) {
                            paths.add(treeWalk.getPathString());
                        }
                    }
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
     * Identifies a change, so that a retry of the same request is recognized, see {@link #appliedCommit(Repository, String, String)}.
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
     * The commit since the one the editor loaded that is the commit of this very change, if there is one. That is the case when a retry follows a request
     * whose push succeeded but whose processing failed: the change must not be applied again, which would fail as a conflict with itself. The pending
     * processing is resumed at the head, which contains the change, unless the processing of a later commit has completed and built it as well. The
     * fingerprint covers the whole request, including the commit the editor loaded, so a different change, even of the same base commit, has another
     * fingerprint and still fails as a conflict.
     */
    private static Optional<String> appliedCommit(Repository repository, String fingerprint, String baseCommitHash) {
        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(Constants.HEAD);
            if (head == null) {
                return Optional.empty();
            }
            revWalk.markStart(revWalk.parseCommit(head));
            ObjectId base = repository.resolve(baseCommitHash);
            if (base != null && repository.getObjectDatabase().has(base)) {
                revWalk.markUninteresting(SecurityPolicyConflicts.parseCommit(revWalk, base));
            }
            for (RevCommit commit : revWalk) {
                if (commit.getFooterLines(CHANGE_TRAILER_KEY).contains(fingerprint)) {
                    return Optional.of(commit.name());
                }
            }
            return Optional.empty();
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
            SecurityPolicyConflicts.requireUnchangedSince(repository, baseCommitHash);
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
                    throw listedTwice(previousPath);
                }
                if (!previousPath.equals(requireValidPolicyPath(policy.document().policyPath()))) {
                    vacated.add(previousPath);
                }
            }
        }

        // Only a vacated file that holds a policy is deleted, so only such a file makes room for a new policy at or below its path.
        Set<String> deleted = vacated.stream().filter(path -> holdsPolicy(SecurityPolicyRepositoryFiles.readPolicyFile(root, path))).collect(Collectors.toSet());

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
                    throw refused(THE_POLICY + previousPath + " can only be changed in the repository, because the editor cannot keep all of its content.",
                            "securityPolicyReadOnly", Map.of("path", previousPath));
                }
                previousLanguageConfiguration = previousDocument.languageConfiguration();
            }
            boolean samePath = policy.previousPolicyPath() != null && path.equals(requireValidPolicyPath(policy.previousPolicyPath()));
            if (!samePath && !deleted.contains(path) && Files.exists(root.resolve(path), LinkOption.NOFOLLOW_LINKS)) {
                throw refused("The file " + path + " already exists in the tests repository.", "securityPolicyFileExists", Map.of("path", path));
            }
            if (liesBelowAFile(root, path, deleted)) {
                throw invalidPath(path, "lies below a file of the tests repository.");
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
            SecurityPolicyConflicts.requireKnownTestCasesOfSplitClasses(repository, baseCommitHash, testClasses, requested);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // A vacated path that holds no policy, e.g. a directory or an unrelated YAML file that an annotation named by mistake, keeps what it holds.
        deleted.forEach(path -> SecurityPolicyRepositoryFiles.deletePolicyFileIfPolicy(root, path));
        rendered.forEach((path, content) -> SecurityPolicyRepositoryFiles.writePolicy(root, path, content));
        SecurityTestAssignments.assignTestCases(root, reassignedTestClasses(testClasses, requested, vacated));

        SecurityFrameworkState after = SecurityPolicyRepositoryFiles.evaluate(root);
        Set<String> referencedPolicies = after.policyFiles().stream().map(SecurityFrameworkState.PolicyFile::policyPath).collect(Collectors.toSet());
        // A class annotation can still point to a policy whose test cases all override it, so each policy needs a test case that uses it, too.
        Map<String, List<String>> assignedAfter = SecurityTestAssignments.entriesByPolicy(SecurityTestAssignments.read(SecurityPolicyRepositoryFiles.javaSources(root)).values());
        for (String path : rendered.keySet()) {
            if (!referencedPolicies.contains(path) || assignedAfter.getOrDefault(path, List.of()).isEmpty()) {
                throw refused("No test uses the policy " + path + ". Assign at least one test case to it.", "securityPolicyUnused", Map.of("path", path));
            }
        }
        if (after.status() != SecurityActivationStatus.CONFIGURED) {
            throw refused("The change would leave the Security Framework " + after.status() + ", so it was not committed.", "securityPolicyBreaksConfiguration",
                    Map.of("status", after.status().name()));
        }
    }

    /**
     * The test classes whose {@code @Policy} annotations a commit of the change rewrites, before and after, computed like the commit does, so the review shows
     * them. None while the assignments are invalid: the commit refuses them and reports why, and a preview of a single policy may list only some of them.
     */
    private static List<SecurityPolicyPreviewDTO.FileDTO> rewrittenTestClasses(Repository repository, RevTree tree, SecurityPolicyChangeDTO change) throws IOException {
        Map<String, String> javaSources = SecurityFrameworkCommitFiles.javaSources(repository, tree);
        Map<String, SecurityTestAssignments.TestClass> testClasses = SecurityTestAssignments.read(javaSources);
        Set<String> touched = new HashSet<>();
        Set<String> vacated = new HashSet<>();
        for (String removedPath : change.removedPolicyPaths()) {
            String path = requireValidPolicyPath(removedPath);
            touched.add(path);
            vacated.add(path);
        }
        for (SecurityPolicyChangeDTO.PolicyChangeDTO policy : change.policies()) {
            if (policy.previousPolicyPath() != null) {
                String previousPath = requireValidPolicyPath(policy.previousPolicyPath());
                touched.add(previousPath);
                if (!previousPath.equals(requireValidPolicyPath(policy.document().policyPath()))) {
                    vacated.add(previousPath);
                }
            }
        }
        List<SecurityTestAssignments.TestClass> reassigned;
        try {
            reassigned = reassignedTestClasses(testClasses, requestedEntries(change, testClasses, touched), vacated);
        }
        catch (BadRequestAlertException _) {
            return List.of();
        }
        List<SecurityPolicyPreviewDTO.FileDTO> files = new ArrayList<>();
        for (SecurityTestAssignments.TestClass testClass : reassigned) {
            String path = testClass.sourcePath();
            String before = javaSources.get(path);
            String after = AresPolicyAnnotations.assign(path, before, testClass.classPolicyPath(), testClass.methodPolicyPaths(), testClass.keptMethods());
            if (!after.equals(before)) {
                files.add(new SecurityPolicyPreviewDTO.FileDTO(path, path, before, after));
            }
        }
        return files;
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

    private static String commitMessage(@Nullable String commitMessage) {
        if (commitMessage == null || commitMessage.isBlank()) {
            return DEFAULT_COMMIT_MESSAGE;
        }
        String message = commitMessage.strip();
        return message.length() > MAX_COMMIT_MESSAGE_LENGTH ? message.substring(0, MAX_COMMIT_MESSAGE_LENGTH) : message;
    }
}
