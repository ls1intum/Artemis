package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.BranchConfig.BranchRebaseMode;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit tests for git hooks, which are not run in checked out repositories as Artemis does not use them.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "git hooks are shell scripts")
class RepositoryGitHooksTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    private static final String DEFAULT_BRANCH = "main";

    private static final String REMOTE_NAME = "origin";

    private static final List<String> HOOKS = List.of("pre-commit", "commit-msg", "post-commit", "pre-push");

    private static final String MARKER_PREFIX = "ran-";

    @TempDir
    Path baseDir;

    private Path workingTree;

    private Path remote;

    @BeforeEach
    void setUp() throws Exception {
        workingTree = Files.createDirectories(baseDir.resolve("checkout"));
        Git.init().setDirectory(workingTree.toFile()).setInitialBranch(DEFAULT_BRANCH).call().close();
        remote = Files.createDirectories(baseDir.resolve("remote.git"));
        Git.init().setDirectory(remote.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call().close();
        installHooks(workingTree.resolve(".git").resolve("hooks"));
    }

    /**
     * Installs an executable script for every hook that leaves a marker file next to the repository, so that a hook that ran can be told
     * from one that did not.
     */
    private void installHooks(Path hooksDirectory) throws IOException {
        for (String hook : HOOKS) {
            Path script = hooksDirectory.resolve(hook);
            FileUtils.write(script.toFile(), "#!/bin/sh\ntouch '" + baseDir.resolve(MARKER_PREFIX + hook) + "'\n", StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    private Set<String> hooksThatRan() throws IOException {
        try (Stream<Path> files = Files.list(baseDir)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.startsWith(MARKER_PREFIX)).map(name -> name.substring(MARKER_PREFIX.length()))
                    .collect(Collectors.toSet());
        }
    }

    private void commitAndPush(Git git, CommitCommand commit) throws Exception {
        commit.setMessage("A commit").setAllowEmpty(true).setCommitter("Artemis", "artemis@example.com").call();
        git.push().setRemote(remote.toUri().toString()).setRefSpecs(new RefSpec("refs/heads/" + DEFAULT_BRANCH + ":refs/heads/" + DEFAULT_BRANCH)).call();
    }

    private void forgetHooksThatRan() throws IOException {
        try (Stream<Path> files = Files.list(baseDir)) {
            files.filter(file -> file.getFileName().toString().startsWith(MARKER_PREFIX)).forEach(file -> FileUtils.deleteQuietly(file.toFile()));
        }
    }

    private Repository openedByArtemis(boolean writeAccess) throws Exception {
        return AbstractGitService.linkRepositoryForExistingGit(workingTree, new LocalVCRepositoryUri(BASE_URI, "ABC", "abc-exercise"), DEFAULT_BRANCH, false, writeAccess);
    }

    private void commitFile(Git git, String name) throws Exception {
        FileUtils.write(git.getRepository().getWorkTree().toPath().resolve(name).toFile(), name, StandardCharsets.UTF_8);
        git.add().addFilepattern(name).call();
        GitService.commit(git).setMessage("Add " + name).setCommitter("Artemis", "artemis@example.com").call();
    }

    /**
     * Leaves the branch of the given repository diverged from the remote, so that a pull has to merge it. The hooks that ran while it was
     * prepared are forgotten, as only the ones the pull runs are of interest.
     */
    private void divergeFromRemote(Git git) throws Exception {
        git.remoteAdd().setName(REMOTE_NAME).setUri(new URIish(remote.toUri().toString())).call();
        commitFile(git, "base.txt");
        git.push().setRemote(REMOTE_NAME).setRefSpecs(new RefSpec("refs/heads/" + DEFAULT_BRANCH + ":refs/heads/" + DEFAULT_BRANCH)).call();
        try (Git other = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(baseDir.resolve("other").toFile()).call()) {
            commitFile(other, "remote.txt");
            other.push().call();
        }
        commitFile(git, "local.txt");
        forgetHooksThatRan();
    }

    /** Merges rather than rebases whatever the configuration says, so that the pull creates a merge commit itself. */
    private void pullMergingTheRemote(Git git) throws Exception {
        git.pull().setRemote(REMOTE_NAME).setRemoteBranchName(DEFAULT_BRANCH).setRebase(BranchRebaseMode.NONE).call();
    }

    /** Leaves a commit on another branch that is not on the current one, and forgets the hooks that ran while preparing it. */
    private ObjectId prepareCherryPick(Git git) throws Exception {
        commitFile(git, "base.txt");
        git.checkout().setCreateBranch(true).setName("side").call();
        commitFile(git, "side.txt");
        ObjectId sideCommit = git.getRepository().resolve("HEAD");
        git.checkout().setName(DEFAULT_BRANCH).call();
        forgetHooksThatRan();
        return sideCommit;
    }

    @Test
    void aPlainJGitRepository_runsTheHooksOfItsGitDirectory() throws Exception {
        // Control: without it, an environment that cannot run the scripts would let the tests below pass for the wrong reason.
        // The verification is switched back on, as the factory of Artemis leaves it off.
        try (Git git = Git.open(workingTree.toFile())) {
            commitAndPush(git, GitService.commit(git).setNoVerify(false));
        }

        assertThat(hooksThatRan()).containsExactlyInAnyOrderElementsOf(HOOKS);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void commitAndPush_inARepositoryOpenedByArtemis_runNoHooks(boolean writeAccess) throws Exception {
        try (Repository repository = openedByArtemis(writeAccess); Git git = new Git(repository)) {
            commitAndPush(git, GitService.commit(git));
        }

        assertThat(hooksThatRan()).isEmpty();
    }

    @Test
    void aPlainJGitRepository_runsTheCommitHooksWhenAPullMergesBranches() throws Exception {
        // Control: a plain repository runs the commit hooks when a pull merges branches.
        try (Git git = Git.open(workingTree.toFile())) {
            divergeFromRemote(git);
            pullMergingTheRemote(git);
        }

        assertThat(hooksThatRan()).containsExactlyInAnyOrder("pre-commit", "commit-msg", "post-commit");
    }

    @Test
    void pull_thatMergesBranches_inARepositoryOpenedByArtemis_runsNoHooks() throws Exception {
        try (Repository repository = openedByArtemis(true); Git git = new Git(repository)) {
            divergeFromRemote(git);
            pullMergingTheRemote(git);
        }

        assertThat(hooksThatRan()).isEmpty();
    }

    @Test
    void aPlainJGitRepository_runsThePostCommitHookAfterACherryPick() throws Exception {
        // Control: a plain repository runs the post-commit hook after a cherry-pick.
        try (Git git = Git.open(workingTree.toFile())) {
            ObjectId sideCommit = prepareCherryPick(git);
            git.cherryPick().include(sideCommit).call();
        }

        assertThat(hooksThatRan()).containsExactly("post-commit");
    }

    @Test
    void cherryPick_inARepositoryOpenedByArtemis_runsNoHooks() throws Exception {
        try (Repository repository = openedByArtemis(true); Git git = new Git(repository)) {
            ObjectId sideCommit = prepareCherryPick(git);
            git.cherryPick().include(sideCommit).call();
        }

        assertThat(hooksThatRan()).isEmpty();
    }

    @Test
    void commitAndPush_inARepositoryWhoseConfigurationNamesAHooksDirectory_runNoHooks() throws Exception {
        Path configuredHooks = workingTree.resolve("configured-hooks");
        installHooks(configuredHooks);
        try (Git git = Git.open(workingTree.toFile())) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_HOOKS_PATH, configuredHooks.toString());
            config.save();
        }

        try (Repository repository = openedByArtemis(true); Git git = new Git(repository)) {
            commitAndPush(git, GitService.commit(git));
        }

        assertThat(hooksThatRan()).isEmpty();
    }
}
