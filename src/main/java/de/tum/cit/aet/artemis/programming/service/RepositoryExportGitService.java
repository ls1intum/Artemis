package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Service for Git operations related to repository export functionality.
 * This includes filtering late submissions, combining student commits, and anonymizing repositories.
 */
@Lazy
@Profile(PROFILE_CORE)
@Service
public class RepositoryExportGitService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryExportGitService.class);

    private static final String ANONYMIZED_STUDENT_NAME = "student";

    private static final String ANONYMIZED_STUDENT_EMAIL = "";

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    private final GitService gitService;

    public RepositoryExportGitService(GitService gitService) {
        this.gitService = gitService;
    }

    /**
     * Filters out all commits after the given date.
     *
     * @param repository                Local Repository Object.
     * @param relevantCommitHash        The relevant commit hash for the submission or null if not present
     * @param filterLateSubmissionsDate the date after which all submissions should be filtered out (may be null)
     */
    public void filterLateSubmissions(Repository repository, @Nullable String relevantCommitHash, ZonedDateTime filterLateSubmissionsDate) {
        if (filterLateSubmissionsDate == null) {
            // No date set in client and exercise has no due date
            return;
        }

        try (Git git = new Git(repository)) {
            String commitHash;

            if (relevantCommitHash != null) {
                log.debug("Last valid commit hash is {}", relevantCommitHash);
                commitHash = relevantCommitHash;
            }
            else {
                log.debug("Last valid submission is not present for participation");
                // Get last commit before due date
                Instant since = Instant.EPOCH;
                Instant until = filterLateSubmissionsDate.toInstant();
                RevFilter between = CommitTimeRevFilter.between(since, until);
                Iterable<RevCommit> commits = git.log().setRevFilter(between).call();
                RevCommit latestCommitBeforeDueDate = commits.iterator().next();
                commitHash = latestCommitBeforeDueDate.getId().getName();
            }
            log.debug("Last commit hash is {}", commitHash);

            gitService.reset(repository, commitHash);
        }
        catch (GitAPIException ex) {
            log.warn("Cannot filter the repo {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
        }
        finally {
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
    }

    /**
     * Stager Task #6: Combine all commits after last instructor commit
     *
     * @param repository             Local Repository Object.
     * @param overwriteDefaultBranch If false keeps the default branch and creates squash commit in separate branch, if true squashes the default branch
     * @param latestSetupCommitHash  The commit hash of the latest setup commit in the student repository
     */
    public void combineAllStudentCommits(Repository repository, boolean overwriteDefaultBranch, String latestSetupCommitHash) {
        try (Git studentGit = new Git(repository)) {
            gitService.setRemoteUrl(repository);

            if (latestSetupCommitHash == null) {
                // Template Repository is somehow empty. Should never happen
                log.warn("Cannot find the last template commit in the student repo {}", repository.getLocalPath());
                // Do not throw when this does not work, it is not critical
                return;
            }

            // checkout own local "diff" branch to keep main as is
            if (!overwriteDefaultBranch) {
                studentGit.checkout().setCreateBranch(true).setName("diff").call();
            }

            studentGit.reset().setMode(ResetCommand.ResetType.SOFT).setRef(latestSetupCommitHash).call();
            studentGit.add().addFilepattern(".").call();
            var optionalStudent = ((StudentParticipation) repository.getParticipation()).getStudents().stream().findFirst();
            var name = optionalStudent.map(User::getName).orElse(artemisGitName);
            var email = optionalStudent.map(User::getEmail).orElse(artemisGitEmail);
            GitService.commit(studentGit).setMessage("All student changes in one commit").setCommitter(name, email).call();
        }
        catch (EntityNotFoundException | GitAPIException | JGitInternalException ex) {
            log.warn("Cannot reset the repo {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
            // Do not throw when this does not work, it is not critical
        }
        finally {
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
    }

    /**
     * Removes all author information from the commits on the currently active branch.
     * Also removes all remotes and FETCH_HEAD since they contain data about the student.
     * Also deletes the .git/logs folder to prevent restoring commits from reflogs
     *
     * @param repository            Local Repository Object.
     * @param latestSetupCommitHash The commit hash of the latest setup commit in the student repository
     */
    public void anonymizeStudentCommits(Repository repository, String latestSetupCommitHash) {
        try (Git studentGit = new Git(repository)) {
            gitService.setRemoteUrl(repository);
            String copyBranchName = "copy";
            String headName = "HEAD";

            if (latestSetupCommitHash == null) {
                // Template Repository is somehow empty. Should never happen
                log.debug("Cannot find a commit in the template repo for: {}", repository.getLocalPath());
                throw new GitException("Template repository has no commits; cannot anonymize student commits");
            }

            // Create copy branch
            Ref copyBranch = studentGit.branchCreate().setName(copyBranchName).call();
            // Reset main branch back to template
            studentGit.reset().setMode(ResetCommand.ResetType.HARD).setRef(latestSetupCommitHash).call();

            // Get list of all student commits, that is all commits up to the last template commit
            Iterable<RevCommit> commits = studentGit.log().add(copyBranch.getObjectId()).call();
            List<RevCommit> commitList = StreamSupport.stream(commits.spliterator(), false).takeWhile(commit -> !commit.getId().getName().equals(latestSetupCommitHash))
                    .collect(Collectors.toCollection(ArrayList::new));
            // Sort them oldest to newest
            Collections.reverse(commitList);
            // Cherry-Pick all commits back into the main branch and immediately commit amend anonymized author information
            for (RevCommit commit : commitList) {
                ObjectId head = studentGit.getRepository().resolve(headName);
                studentGit.cherryPick().include(commit).call();
                // Only commit amend if head changed; cherry-picking empty commits does nothing
                if (!head.equals(studentGit.getRepository().resolve(headName))) {
                    PersonIdent authorIdent = commit.getAuthorIdent();
                    PersonIdent fakeIdent = new PersonIdent(ANONYMIZED_STUDENT_NAME, ANONYMIZED_STUDENT_EMAIL, authorIdent.getWhenAsInstant(), authorIdent.getZoneId());
                    GitService.commit(studentGit).setAmend(true).setAuthor(fakeIdent).setCommitter(fakeIdent).setMessage(commit.getFullMessage()).call();
                }
            }
            // Delete copy branch
            studentGit.branchDelete().setBranchNames(copyBranchName).setForce(true).call();

            // Delete all remotes
            gitService.removeRemotes(studentGit);

            // Delete .git/logs/ folder to delete git reflogs
            Path logsPath = Path.of(repository.getDirectory().getPath(), "logs");
            FileUtils.deleteDirectory(logsPath.toFile());

            // Delete FETCH_HEAD containing the url of the last fetch
            Path fetchHeadPath = Path.of(repository.getDirectory().getPath(), "FETCH_HEAD");
            Files.deleteIfExists(fetchHeadPath);
        }
        catch (EntityNotFoundException | GitAPIException | JGitInternalException | IOException ex) {
            log.warn("Cannot anonymize the repo {} due to the following exception: {}", repository.getLocalPath(), ex.getMessage());
            throw new GitException("Failed to anonymize repository: " + ex.getMessage(), ex);
        }
        finally {
            // if repo is not closed, it causes weird IO issues when trying to delete the repo again
            // java.io.IOException: Unable to delete file: ...\.git\objects\pack\...
            repository.close();
        }
    }

    /**
     * Verifies that the repository is anonymized according to expectations.
     * - All remotes removed and no remote tracking refs exist
     * - .git/logs directory removed and FETCH_HEAD deleted
     * - All commits after the template's last commit use anonymized author/committer
     * - Optionally check that at most one student commit exists after the template (when combinedExpected is true)
     *
     * @param repository            the local repository
     * @param combinedExpected      whether to enforce that there is at most one student commit after the template commit
     * @param latestSetupCommitHash The commit hash of the latest setup commit in the student repository
     * @throws de.tum.cit.aet.artemis.core.exception.GitException if verification fails
     */
    public void verifyAnonymizationOrThrow(Repository repository, boolean combinedExpected, String latestSetupCommitHash) {
        try (Git git = new Git(repository)) {
            // Check remotes removed
            if (!git.remoteList().call().isEmpty()) {
                throw new GitException("Anonymization verification failed: repository still has remotes configured");
            }
            // Also ensure no remote tracking refs remain
            try {
                for (Ref ref : git.getRepository().getRefDatabase().getRefs()) {
                    if (ref.getName().startsWith("refs/remotes/")) {
                        throw new GitException("Anonymization verification failed: remote tracking refs remain");
                    }
                }
            }
            catch (java.io.IOException ioEx) {
                throw new GitException("Failed to access refs during verification: " + ioEx.getMessage(), ioEx);
            }

            // Filesystem checks for logs and FETCH_HEAD
            Path logsPath = Path.of(repository.getDirectory().getPath(), "logs");
            if (Files.exists(logsPath)) {
                throw new GitException("Anonymization verification failed: .git/logs still present");
            }
            Path fetchHeadPath = Path.of(repository.getDirectory().getPath(), "FETCH_HEAD");
            if (Files.exists(fetchHeadPath)) {
                throw new GitException("Anonymization verification failed: FETCH_HEAD still present");
            }

            // Determine last template commit
            if (latestSetupCommitHash == null) {
                throw new GitException("Cannot determine template commit for verification");
            }

            verifyCommitAnonymizationFromHead(git, latestSetupCommitHash, combinedExpected);
        }
        catch (GitAPIException | JGitInternalException e) {
            throw new GitException("Failed during anonymization verification: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies commit anonymization on the history reachable from HEAD down to (excluding) the template commit.
     */
    private void verifyCommitAnonymizationFromHead(Git git, String latestSetupCommitHash, boolean combinedExpected)
            throws org.eclipse.jgit.api.errors.GitAPIException, GitException {
        Iterable<RevCommit> commits = git.log().call();
        int studentCommitCount = 0;
        boolean foundTemplate = false;
        for (RevCommit commit : commits) {
            if (commit.getId().getName().equals(latestSetupCommitHash)) {
                foundTemplate = true;
                break;
            }
            PersonIdent author = commit.getAuthorIdent();
            PersonIdent committer = commit.getCommitterIdent();
            if (!(ANONYMIZED_STUDENT_NAME.equals(author.getName()) && ANONYMIZED_STUDENT_EMAIL.equals(author.getEmailAddress())
                    && ANONYMIZED_STUDENT_NAME.equals(committer.getName()) && ANONYMIZED_STUDENT_EMAIL.equals(committer.getEmailAddress()))) {
                throw new GitException("Anonymization verification failed: found non-anonymized commit");
            }
            studentCommitCount++;
            if (combinedExpected && studentCommitCount > 1) {
                throw new GitException("Anonymization verification failed: commits not combined into a single commit");
            }
        }
        if (!foundTemplate) {
            throw new GitException("Anonymization verification failed: template commit not reachable from HEAD");
        }
    }

}
