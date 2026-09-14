package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.exception.GitException;

/**
 * Unit tests for the git rewriting that happens when a student repository is exported.
 * <p>
 * These operations are what makes an export safe to hand to a plagiarism checker or to another instructor: the student's
 * name and mail address are rewritten out of every commit, the remotes and reflogs that would point back at them are
 * removed, and commits made after the deadline are dropped. A failure here is silent and irreversible in the wrong
 * direction - the export still looks fine, but it carries the student's identity, or it keeps work that was submitted
 * too late. The verification method is what is supposed to catch that, so it is tested from both sides: it has to accept
 * a correctly anonymized repository and reject every way one can be left half-anonymized.
 */
class RepositoryExportGitServiceTest {

    private static final String STUDENT_NAME = "Anna Studentin";

    private static final String STUDENT_EMAIL = "anna.studentin@example.com";

    @TempDir
    Path baseDir;

    private Path workingTree;

    private RepositoryExportGitService repositoryExportGitService;

    private String setupCommitHash;

    @BeforeEach
    void setUp() throws Exception {
        workingTree = Files.createDirectories(baseDir.resolve("checkout"));
        GitService gitService = new GitService();
        ReflectionTestUtils.setField(gitService, "localVCBasePath", baseDir);
        repositoryExportGitService = new RepositoryExportGitService(gitService);
        ReflectionTestUtils.setField(repositoryExportGitService, "artemisGitName", "Artemis");
        ReflectionTestUtils.setField(repositoryExportGitService, "artemisGitEmail", "artemis@example.com");

        try (Git git = Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call()) {
            setupCommitHash = commit(git, "exercise setup", "README.md", "the exercise", new PersonIdent("Instructor", "instructor@example.com"));
        }
    }

    /**
     * Writes a file and commits it, returning the new commit hash.
     */
    private String commit(Git git, String message, String file, String content, PersonIdent author) throws Exception {
        FileUtils.write(workingTree.resolve(file).toFile(), content, StandardCharsets.UTF_8);
        git.add().addFilepattern(".").call();
        return GitService.commit(git).setMessage(message).setAuthor(author).setCommitter(author).call().getId().getName();
    }

    private String commitAsStudent(Git git, String message, String file, String content) throws Exception {
        return commit(git, message, file, content, new PersonIdent(STUDENT_NAME, STUDENT_EMAIL));
    }

    /**
     * The student, committing at a given moment. Git stores commit times with second resolution, so a test that depends on
     * one commit being before a deadline and another after it has to set the times explicitly rather than rely on "now".
     */
    private static PersonIdent studentAt(ZonedDateTime when) {
        return new PersonIdent(STUDENT_NAME, STUDENT_EMAIL, when.toInstant(), when.getZone());
    }

    /**
     * A fresh wrapper for every call: the service closes the repository when it is done, so one instance cannot be
     * reused across two operations.
     */
    private Repository repository() throws Exception {
        Repository repository = new Repository(workingTree.resolve(".git").toString(), new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-student"));
        ReflectionTestUtils.setField(repository, "localPath", workingTree);
        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        User student = new User();
        student.setFirstName("Anna");
        student.setLastName("Studentin");
        student.setEmail(STUDENT_EMAIL);
        participation.setParticipant(student);
        repository.setParticipation(participation);
        return repository;
    }

    private List<RevCommit> commitsOnHead() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            return StreamSupport.stream(git.log().call().spliterator(), false).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
    }

    /**
     * The student commits that sit on top of the setup commit, newest first.
     */
    private List<RevCommit> studentCommits() throws Exception {
        return commitsOnHead().stream().takeWhile(commit -> !commit.getId().getName().equals(setupCommitHash)).toList();
    }

    // --- filterLateSubmissions -------------------------------------------------------------------------------------

    @Test
    void filterLateSubmissions_withoutADeadline_keepsEveryCommit() throws Exception {
        // An exercise without a due date has no late submissions, so nothing may be dropped.
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "work", "Main.java", "public class Main {}");
        }

        repositoryExportGitService.filterLateSubmissions(repository(), null, null);

        assertThat(studentCommits()).hasSize(1);
    }

    @Test
    void filterLateSubmissions_resetsToTheSubmissionThatWasGraded() throws Exception {
        // When the submission is known, the export has to show exactly that state, not whatever the student pushed later.
        String gradedCommit;
        try (Git git = Git.open(workingTree.toFile())) {
            gradedCommit = commitAsStudent(git, "the submission", "Main.java", "public class Main {}");
            commitAsStudent(git, "kept working afterwards", "Main.java", "public class Main { /* later */ }");
        }

        repositoryExportGitService.filterLateSubmissions(repository(), gradedCommit, ZonedDateTime.now());

        assertThat(commitsOnHead().getFirst().getId().getName()).isEqualTo(gradedCommit);
        assertThat(workingTree.resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("public class Main {}");
    }

    @Test
    void filterLateSubmissions_withoutAKnownSubmission_fallsBackToTheLastCommitBeforeTheDeadline() throws Exception {
        // Without a recorded submission the deadline itself decides, so a commit made after it must not survive the export.
        String beforeDeadline;
        try (Git git = Git.open(workingTree.toFile())) {
            beforeDeadline = commitAsStudent(git, "in time", "Main.java", "in time");
            commitAsStudent(git, "too late", "Main.java", "too late");
        }
        // The commits above are all "now"; the deadline has to sit between them, which git's second-resolution timestamps
        // cannot express, so the deadline is placed just after the whole history and the last commit is dropped by hash.
        ZonedDateTime deadline = ZonedDateTime.now().plusSeconds(1);

        repositoryExportGitService.filterLateSubmissions(repository(), beforeDeadline, deadline);

        assertThat(commitsOnHead().getFirst().getId().getName()).isEqualTo(beforeDeadline);
        assertThat(workingTree.resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("in time");
    }

    @Test
    void filterLateSubmissions_withNoRecordedSubmission_dropsTheCommitsMadeAfterTheDeadline() throws Exception {
        // Nothing recorded which commit was submitted, so the deadline alone decides what the export may contain.
        String inTime;
        try (Git git = Git.open(workingTree.toFile())) {
            inTime = commit(git, "in time", "Main.java", "in time", studentAt(ZonedDateTime.now().minusHours(2)));
            commit(git, "after the deadline", "Main.java", "too late", studentAt(ZonedDateTime.now().minusMinutes(5)));
        }

        repositoryExportGitService.filterLateSubmissions(repository(), null, ZonedDateTime.now().minusHours(1));

        assertThat(commitsOnHead().getFirst().getId().getName()).as("the export stops at the last commit made before the deadline").isEqualTo(inTime);
        assertThat(workingTree.resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("in time");
    }

    // --- combineAllStudentCommits ----------------------------------------------------------------------------------

    @Test
    void combineAllStudentCommits_squashesTheStudentsWorkIntoASingleCommit() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "first attempt", "Main.java", "one");
            commitAsStudent(git, "second attempt", "Main.java", "two");
            commitAsStudent(git, "third attempt", "Main.java", "three");
        }

        repositoryExportGitService.combineAllStudentCommits(repository(), true, setupCommitHash);

        assertThat(studentCommits()).as("the three attempts become one commit").hasSize(1);
        assertThat(studentCommits().getFirst().getFullMessage()).isEqualTo("All student changes in one commit");
        // Squashing must not lose the final state of the work.
        assertThat(workingTree.resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("three");
    }

    @Test
    void combineAllStudentCommits_withoutOverwritingTheDefaultBranch_squashesOnASeparateBranch() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "first attempt", "Main.java", "one");
            commitAsStudent(git, "second attempt", "Main.java", "two");
        }

        repositoryExportGitService.combineAllStudentCommits(repository(), false, setupCommitHash);

        try (Git git = Git.open(workingTree.toFile())) {
            assertThat(git.getRepository().getBranch()).as("the squash happens on a branch of its own").isEqualTo("diff");
            assertThat(git.branchList().call()).extracting(ref -> ref.getName().replace("refs/heads/", "")).contains("main", "diff");
        }
        assertThat(studentCommits()).hasSize(1);
    }

    @Test
    void combineAllStudentCommits_withoutATemplateCommit_leavesTheRepositoryAlone() throws Exception {
        // An empty template repository should never happen, and it must not cost the student's history if it does.
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "work", "Main.java", "one");
        }

        assertThatCode(() -> repositoryExportGitService.combineAllStudentCommits(repository(), true, null)).doesNotThrowAnyException();

        assertThat(studentCommits()).hasSize(1);
    }

    // --- anonymizeStudentCommits -----------------------------------------------------------------------------------

    @Test
    void anonymizeStudentCommits_replacesTheStudentOnEveryCommitButKeepsTheMessages() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "first attempt", "Main.java", "one");
            commitAsStudent(git, "second attempt", "Main.java", "two");
        }

        repositoryExportGitService.anonymizeStudentCommits(repository(), setupCommitHash);

        List<RevCommit> studentCommits = studentCommits();
        assertThat(studentCommits).hasSize(2);
        assertThat(studentCommits).allSatisfy(commit -> {
            assertThat(commit.getAuthorIdent().getName()).isEqualTo("student");
            assertThat(commit.getAuthorIdent().getEmailAddress()).isEmpty();
            assertThat(commit.getCommitterIdent().getName()).isEqualTo("student");
            assertThat(commit.getCommitterIdent().getEmailAddress()).isEmpty();
        });
        // The messages are what makes the export reviewable, so they have to survive the rewrite.
        assertThat(studentCommits).extracting(RevCommit::getFullMessage).containsExactly("second attempt", "first attempt");
        assertThat(workingTree.resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("two");
    }

    @Test
    void anonymizeStudentCommits_removesTheRemotesAndReflogsThatPointBackAtTheStudent() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish("https://artemis.example.com/git/ABC/abc-student.git")).call();
            commitAsStudent(git, "work", "Main.java", "one");
        }
        FileUtils.write(workingTree.resolve(".git/FETCH_HEAD").toFile(), "the url of the last fetch", StandardCharsets.UTF_8);

        repositoryExportGitService.anonymizeStudentCommits(repository(), setupCommitHash);

        try (Git git = Git.open(workingTree.toFile())) {
            assertThat(git.remoteList().call()).as("a remote would point straight back at the student's repository").isEmpty();
        }
        // The reflog records every previous position of the branch, so it can restore the commits that were just rewritten.
        assertThat(workingTree.resolve(".git/logs")).doesNotExist();
        assertThat(workingTree.resolve(".git/FETCH_HEAD")).doesNotExist();
    }

    @Test
    void anonymizeStudentCommits_withoutATemplateCommit_isReportedRatherThanSilentlySkipped() throws Exception {
        // Skipping anonymization quietly would export the student's name, so this is the one case that has to throw.
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "work", "Main.java", "one");
        }

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> repositoryExportGitService.anonymizeStudentCommits(repository(), null));
    }

    // --- verifyAnonymizationOrThrow --------------------------------------------------------------------------------

    @Test
    void verifyAnonymization_acceptsARepositoryThatWasProperlyAnonymized() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "first attempt", "Main.java", "one");
            commitAsStudent(git, "second attempt", "Main.java", "two");
        }
        repositoryExportGitService.anonymizeStudentCommits(repository(), setupCommitHash);

        assertThatCode(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), false, setupCommitHash)).doesNotThrowAnyException();
    }

    @Test
    void verifyAnonymization_rejectsACommitThatStillCarriesTheStudentsName() throws Exception {
        // This is the case the whole verification exists for: the export would otherwise ship the student's identity.
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "not anonymized", "Main.java", "one");
        }
        removeRemotesAndLogs();

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), false, setupCommitHash))
                .withMessageContaining("non-anonymized commit");
    }

    @Test
    void verifyAnonymization_rejectsARepositoryThatStillHasARemote() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            git.remoteAdd().setName("origin").setUri(new org.eclipse.jgit.transport.URIish("https://artemis.example.com/git/ABC/abc-student.git")).call();
        }

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), false, setupCommitHash))
                .withMessageContaining("remotes");
    }

    @Test
    void verifyAnonymization_rejectsARemoteTrackingRefEvenWhenNoRemoteIsConfigured() throws Exception {
        // Dropping the remote from the config leaves refs/remotes/... behind, and those still name the student's repository.
        removeRemotesAndLogs();
        try (Git git = Git.open(workingTree.toFile())) {
            var update = git.getRepository().updateRef("refs/remotes/origin/main");
            update.setNewObjectId(git.getRepository().resolve("HEAD"));
            update.setForceUpdate(true);
            update.update();
        }

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), false, setupCommitHash))
                .withMessageContaining("remote tracking refs remain");
    }

    @Test
    void verifyAnonymization_rejectsARepositoryThatStillHasItsReflog() throws Exception {
        // The reflog alone is enough to recover the original, named commits.
        assertThat(workingTree.resolve(".git/logs")).exists();

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), false, setupCommitHash))
                .withMessageContaining("logs");
    }

    @Test
    void verifyAnonymization_rejectsARepositoryThatStillHasAFetchHead() throws Exception {
        removeRemotesAndLogs();
        FileUtils.write(workingTree.resolve(".git/FETCH_HEAD").toFile(), "the url of the last fetch", StandardCharsets.UTF_8);

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), false, setupCommitHash))
                .withMessageContaining("FETCH_HEAD");
    }

    @Test
    void verifyAnonymization_whenTheCommitsShouldHaveBeenCombined_rejectsMoreThanOne() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            commitAsStudent(git, "first attempt", "Main.java", "one");
            commitAsStudent(git, "second attempt", "Main.java", "two");
        }
        repositoryExportGitService.anonymizeStudentCommits(repository(), setupCommitHash);

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), true, setupCommitHash))
                .withMessageContaining("not combined");
    }

    @Test
    void verifyAnonymization_rejectsAHistoryThatDoesNotContainTheTemplateCommit() throws Exception {
        // A history that never reaches the template commit means the walk compared against the wrong branch, so nothing was
        // actually verified. Every commit here is anonymized, so the missing template is the only thing left to complain about.
        PersonIdent anonymized = new PersonIdent("student", "");
        try (Git git = Git.open(workingTree.toFile())) {
            GitService.commit(git).setAmend(true).setAuthor(anonymized).setCommitter(anonymized).setMessage("exercise setup").call();
        }
        removeRemotesAndLogs();

        assertThatExceptionOfType(GitException.class)
                .isThrownBy(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), false, "0123456789012345678901234567890123456789"))
                .withMessageContaining("template commit not reachable");
    }

    @Test
    void verifyAnonymization_withoutATemplateCommit_cannotVerifyAnythingAndSaysSo() throws Exception {
        removeRemotesAndLogs();

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> repositoryExportGitService.verifyAnonymizationOrThrow(repository(), false, null))
                .withMessageContaining("Cannot determine template commit");
    }

    /**
     * Brings a repository into the state anonymization leaves behind, so that a test can check one deviation from it at a
     * time rather than tripping over the first check.
     */
    private void removeRemotesAndLogs() throws Exception {
        try (Git git = Git.open(workingTree.toFile())) {
            for (var remote : git.remoteList().call()) {
                git.remoteRemove().setRemoteName(remote.getName()).call();
            }
        }
        FileUtils.deleteDirectory(workingTree.resolve(".git/logs").toFile());
    }
}
