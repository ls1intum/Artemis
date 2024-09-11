package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.web.rest.GitDiffReportParserService;

@Profile(PROFILE_CORE)
@Service
public class CommitHistoryService {

    private static final Logger log = LoggerFactory.getLogger(CommitHistoryService.class);

    private final GitService gitService;

    private final ProfileService profileService;

    private final GitDiffReportParserService gitDiffReportParserService;

    public CommitHistoryService(GitService gitService, ProfileService profileService, GitDiffReportParserService gitDiffReportParserService) {
        this.gitService = gitService;
        this.profileService = profileService;
        this.gitDiffReportParserService = gitDiffReportParserService;
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport containing the git-diff for a repository and two commit hashes.
     *
     * @param repositoryUri The repository for which the report should be created
     * @param commitHash1   The first commit hash
     * @param commitHash2   The second commit hash
     * @return The report with the changes between the two commits
     * @throws GitAPIException If an error occurs while accessing the git repository
     * @throws IOException     If an error occurs while accessing the file system
     */
    public ProgrammingExerciseGitDiffReport generateReportForCommits(VcsRepositoryUri repositoryUri, String commitHash1, String commitHash2) throws GitAPIException, IOException {

        Repository repository;
        if (profileService.isLocalVcsActive()) {
            log.debug("Using local VCS generateReportForCommits on repo {}", repositoryUri);
            repository = gitService.getBareRepository(repositoryUri);
        }
        else {
            log.debug("Checking out repo {} for generateReportForCommits", repositoryUri);
            repository = gitService.getOrCheckoutRepository(repositoryUri, true);
        }

        RevCommit commitOld = repository.parseCommit(repository.resolve(commitHash1));
        RevCommit commitNew = repository.parseCommit(repository.resolve(commitHash2));

        if (commitOld == null || commitNew == null) {
            log.error("Could not find the commits with the provided commit hashes in the repository: {} and {}", commitHash1, commitHash2);
            return null;
        }

        return createReport(repository, commitOld, commitNew);
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport containing the git-diff for a participation and a commit hash.
     * If both commit hashes are the same, the report will be the diff of the commit with an empty file.
     *
     * @param repository The repository for which the report should be created
     * @param commitOld  The old commit
     * @param commitNew  The new commit
     * @return The report with the changes between the two commits
     * @throws IOException If an error occurs while accessing the file system
     */
    @NotNull
    private ProgrammingExerciseGitDiffReport createReport(Repository repository, RevCommit commitOld, RevCommit commitNew) throws IOException {
        StringBuilder diffs = new StringBuilder();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            formatter.setContext(10); // Set the context lines
            formatter.setDiffComparator(RawTextComparator.DEFAULT);
            formatter.setDetectRenames(true);

            // If the commit hashes are the same, we want to compare the commit with an empty file
            // This is useful for the initial commit of a repository
            if (commitOld.getName().equals(commitNew.getName())) {
                formatter.format(null, commitNew);
            }
            else {
                formatter.format(commitOld, commitNew);
            }

            diffs.append(out.toString(StandardCharsets.UTF_8));
        }
        var programmingExerciseGitDiffEntries = gitDiffReportParserService.extractDiffEntries(diffs.toString(), false);
        var report = new ProgrammingExerciseGitDiffReport();
        for (ProgrammingExerciseGitDiffEntry gitDiffEntry : programmingExerciseGitDiffEntries) {
            gitDiffEntry.setGitDiffReport(report);
        }
        report.setEntries(new HashSet<>(programmingExerciseGitDiffEntries));
        return report;
    }
}
