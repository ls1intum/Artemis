package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseGitDiffEntry;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

/**
 * The service handling ProgrammingExerciseGitDiffReport and their ProgrammingExerciseGitDiffEntries.
 */
@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseGitDiffReportService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGitDiffReportService.class);

    private final GitService gitService;

    private final GitDiffReportParserService gitDiffReportParserService;

    public ProgrammingExerciseGitDiffReportService(GitService gitService, GitDiffReportParserService gitDiffReportParserService) {
        this.gitService = gitService;
        this.gitDiffReportParserService = gitDiffReportParserService;
    }

    /**
     * Calculates git diff between two repositories and returns the cumulative number of diff lines.
     *
     * @param urlRepoA       url of the first repo to compare
     * @param localPathRepoA local path to the checked out instance of the first repo to compare
     * @param urlRepoB       url of the second repo to compare
     * @param localPathRepoB local path to the checked out instance of the second repo to compare
     * @return cumulative number of lines in the git diff of given repositories
     */
    public int calculateNumberOfDiffLinesBetweenRepos(VcsRepositoryUri urlRepoA, Path localPathRepoA, VcsRepositoryUri urlRepoB, Path localPathRepoB) {
        var repoA = gitService.getExistingCheckedOutRepositoryByLocalPath(localPathRepoA, urlRepoA);
        var repoB = gitService.getExistingCheckedOutRepositoryByLocalPath(localPathRepoB, urlRepoB);

        var treeParserRepoA = new FileTreeIterator(repoA);
        var treeParserRepoB = new FileTreeIterator(repoB);

        try (var diffOutputStream = new ByteArrayOutputStream(); var git = Git.wrap(repoB)) {
            git.diff().setOldTree(treeParserRepoB).setNewTree(treeParserRepoA).setOutputStream(diffOutputStream).call();
            var diff = diffOutputStream.toString();
            return gitDiffReportParserService.extractDiffEntries(diff, true, false).stream().mapToInt(ProgrammingExerciseGitDiffEntry::getLineCount).sum();
        }
        catch (IOException | GitAPIException e) {
            log.error("Error calculating number of diff lines between repositories: urlRepoA={}, urlRepoB={}.", urlRepoA, urlRepoB, e);
            return Integer.MAX_VALUE;
        }
    }
}
