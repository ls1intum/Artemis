package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * Unit tests for the checks Artemis runs before it accepts a push.
 * <p>
 * This hook is the last thing between a student's push and the repository on disk, and it is the only place where
 * several rules are enforced at all: a push may touch one ref, may not delete a branch, may not rewrite history unless
 * the user is allowed to, and may not carry a symbolic link, a submodule or a file large enough to fill the disk. A rule
 * that stops being enforced here is not enforced anywhere, and the push would silently succeed.
 */
@ExtendWith(MockitoExtension.class)
class LocalVCPrePushHookTest {

    private static final String DEFAULT_BRANCH = "main";

    private static final String DEFAULT_REF = Constants.R_HEADS + DEFAULT_BRANCH;

    @Mock
    private LocalVCServletService localVCServletService;

    @TempDir
    Path repositoryPath;

    private Repository repository;

    private LocalVCPrePushHook hook;

    @BeforeEach
    void setUp() throws Exception {
        repository = Git.init().setDirectory(repositoryPath.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call().getRepository();
        // Most tests reject a push before the branching check is reached, so this stub stays lenient.
        lenient().when(localVCServletService.isBranchNameAllowedForRepository(any(), anyString())).thenReturn(LocalVCServletService.BranchingStatus.BRANCH_ALLOWED);
        User user = new User();
        user.setId(1L);
        user.setLogin("ge12abc");
        hook = new LocalVCPrePushHook(localVCServletService, user);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    /** Builds a commit whose tree holds exactly the given entry, so that a push can be judged on what it carries. */
    private ObjectId commitWith(String path, FileMode fileMode, byte[] content) throws IOException {
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            ObjectId blobId = fileMode == FileMode.GITLINK ? ObjectId.fromString("0123456789012345678901234567890123456789") : inserter.insert(Constants.OBJ_BLOB, content);
            TreeFormatter treeFormatter = new TreeFormatter();
            treeFormatter.append(path, fileMode, blobId);
            ObjectId treeId = inserter.insert(treeFormatter);

            CommitBuilder commitBuilder = new CommitBuilder();
            commitBuilder.setTreeId(treeId);
            PersonIdent author = new PersonIdent("Ada Lovelace", "ada@example.com");
            commitBuilder.setAuthor(author);
            commitBuilder.setCommitter(author);
            commitBuilder.setMessage("a commit");
            ObjectId commitId = inserter.insert(commitBuilder);
            inserter.flush();
            return commitId;
        }
    }

    private ObjectId commitWithRegularFile(String path, byte[] content) throws IOException {
        return commitWith(path, FileMode.REGULAR_FILE, content);
    }

    private ReceiveCommand receive(ObjectId newId, String refName) {
        return new ReceiveCommand(ObjectId.zeroId(), newId, refName);
    }

    private void run(ReceiveCommand... commands) {
        hook.onPreReceive(new ReceivePack(repository), List.of(commands));
    }

    @Test
    void onPreReceive_withoutAnyCommand_doesNothing() {
        // A push that updates no ref has nothing to judge, and there is no command to reject on either.
        hook.onPreReceive(new ReceivePack(repository), List.of());
    }

    @Test
    void onPreReceive_pushingToSeveralRefsAtOnce_isRejected() throws Exception {
        // Artemis attributes a push to exactly one repository and branch, so a push that updates two refs cannot be processed.
        ReceiveCommand first = receive(commitWithRegularFile("Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8)), DEFAULT_REF);
        ReceiveCommand second = receive(ObjectId.zeroId(), Constants.R_HEADS + "feature");

        run(first, second);

        assertThat(first.getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
        assertThat(first.getMessage()).isEqualTo("You cannot push multiple refs at once.");
    }

    @Test
    void onPreReceive_pushingToAnotherBranchWhileBranchingIsDisabled_isRejected() throws Exception {
        when(localVCServletService.isBranchNameAllowedForRepository(any(), anyString())).thenReturn(LocalVCServletService.BranchingStatus.BRANCHING_DISABLED);
        ReceiveCommand command = receive(commitWithRegularFile("Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8)), Constants.R_HEADS + "feature");

        run(command);

        assertThat(command.getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
        assertThat(command.getMessage()).isEqualTo("You cannot push to a branch other than the default branch.");
    }

    @Test
    void onPreReceive_pushingToABranchNameTheExerciseDoesNotAllow_isRejected() throws Exception {
        when(localVCServletService.isBranchNameAllowedForRepository(any(), anyString())).thenReturn(LocalVCServletService.BranchingStatus.NAME_DOES_NOT_MATCH_REGEX);
        ReceiveCommand command = receive(commitWithRegularFile("Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8)), Constants.R_HEADS + "not-allowed");

        run(command);

        assertThat(command.getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
        assertThat(command.getMessage()).contains("does not match the regex for branch names");
    }

    @Test
    void onPreReceive_deletingABranch_isRejected() throws Exception {
        // Deleting the branch would take the student's whole submission history with it.
        ObjectId commitId = commitWithRegularFile("Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8));
        pointDefaultBranchAt(commitId);
        ReceiveCommand deletion = new ReceiveCommand(commitId, ObjectId.zeroId(), DEFAULT_REF);

        run(deletion);

        assertThat(deletion.getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
        assertThat(deletion.getMessage()).isEqualTo("You cannot delete a branch.");
    }

    @Test
    void onPreReceive_forcePushingWithoutThePermission_isRejected() throws Exception {
        // A force push rewrites history, which would let a student replace what was already graded.
        ObjectId firstCommit = commitWithRegularFile("Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8));
        ObjectId rewrittenCommit = commitWithRegularFile("Main.java", "class Main { int rewritten; }".getBytes(StandardCharsets.UTF_8));
        pointDefaultBranchAt(firstCommit);
        when(localVCServletService.isUserAllowedToForcePush(any(), any())).thenReturn(false);
        ReceiveCommand forcePush = new ReceiveCommand(firstCommit, rewrittenCommit, DEFAULT_REF, ReceiveCommand.Type.UPDATE_NONFASTFORWARD);

        run(forcePush);

        assertThat(forcePush.getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
        assertThat(forcePush.getMessage()).isEqualTo("You cannot force push.");
    }

    @Test
    void onPreReceive_forcePushingWithThePermission_isAccepted() throws Exception {
        ObjectId firstCommit = commitWithRegularFile("Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8));
        ObjectId rewrittenCommit = commitWithRegularFile("Main.java", "class Main { int rewritten; }".getBytes(StandardCharsets.UTF_8));
        pointDefaultBranchAt(firstCommit);
        when(localVCServletService.isUserAllowedToForcePush(any(), any())).thenReturn(true);
        ReceiveCommand forcePush = new ReceiveCommand(firstCommit, rewrittenCommit, DEFAULT_REF, ReceiveCommand.Type.UPDATE_NONFASTFORWARD);

        run(forcePush);

        assertThat(forcePush.getResult()).as("an instructor who may force push is let through").isEqualTo(ReceiveCommand.Result.NOT_ATTEMPTED);
    }

    @Test
    void onPreReceive_aPushCarryingASymbolicLink_isRejectedAndSaysHowToFixIt() throws Exception {
        // A symbolic link points wherever its author chose, which on the server means outside the repository.
        ReceiveCommand command = receive(commitWith("link-to-secrets", FileMode.SYMLINK, "/etc/passwd".getBytes(StandardCharsets.UTF_8)), DEFAULT_REF);

        run(command);

        assertThat(command.getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
        assertThat(command.getMessage()).as("the student is told which entry is the problem and how to remove it").contains("Symbolic links are not allowed: 'link-to-secrets'")
                .contains("rm \"link-to-secrets\"");
    }

    @Test
    void onPreReceive_aPushCarryingASubmodule_isRejectedAndSaysHowToFixIt() throws Exception {
        // A submodule makes the build pull code from somewhere Artemis does not control.
        ReceiveCommand command = receive(commitWith("external-library", FileMode.GITLINK, new byte[0]), DEFAULT_REF);

        run(command);

        assertThat(command.getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
        assertThat(command.getMessage()).as("the student is told which entry is the problem and how to remove it").contains("Git submodules are not allowed: 'external-library'")
                .contains("git rm --cached \"external-library\"");
    }

    @Test
    void onPreReceive_aPushCarryingAFileLargerThanTheLimit_isRejectedWithItsSize() throws Exception {
        byte[] tooLarge = new byte[11 * 1024 * 1024];
        ReceiveCommand command = receive(commitWithRegularFile("recording.mp4", tooLarge), DEFAULT_REF);

        run(command);

        assertThat(command.getResult()).isEqualTo(ReceiveCommand.Result.REJECTED_OTHER_REASON);
        assertThat(command.getMessage()).as("the student is told which file is too large and by how much").isEqualTo("File 'recording.mp4' exceeds 10MB size limit (11.00 MB)");
    }

    @Test
    void onPreReceive_anOrdinaryPushToTheDefaultBranch_isAccepted() throws Exception {
        ReceiveCommand command = receive(commitWithRegularFile("src/Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8)), DEFAULT_REF);

        run(command);

        assertThat(command.getResult()).as("a push that breaks none of the rules is left for JGit to apply").isEqualTo(ReceiveCommand.Result.NOT_ATTEMPTED);
        assertThat(command.getMessage()).isNull();
    }

    @Test
    void onPreReceive_aFileExactlyAtTheLimit_isAccepted() throws Exception {
        // The limit is a maximum, not a threshold below which a file has to stay.
        byte[] exactlyAtTheLimit = new byte[10 * 1024 * 1024];
        ReceiveCommand command = receive(commitWithRegularFile("archive.zip", exactlyAtTheLimit), DEFAULT_REF);

        run(command);

        assertThat(command.getResult()).isEqualTo(ReceiveCommand.Result.NOT_ATTEMPTED);
    }

    private void pointDefaultBranchAt(ObjectId commitId) throws IOException {
        var refUpdate = repository.updateRef(DEFAULT_REF);
        refUpdate.setNewObjectId(commitId);
        refUpdate.setForceUpdate(true);
        refUpdate.update();
    }
}
