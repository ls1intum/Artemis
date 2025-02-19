package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseGitDiffEntry;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseGitDiffReport;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseGitDiffReportRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

/**
 * The service handling ProgrammingExerciseGitDiffReport and their ProgrammingExerciseGitDiffEntries.
 */
@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseGitDiffReportService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGitDiffReportService.class);

    private final GitService gitService;

    private final ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final GitDiffReportParserService gitDiffReportParserService;

    private final ProfileService profileService;

    public ProgrammingExerciseGitDiffReportService(GitService gitService, ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, GitDiffReportParserService gitDiffReportParserService,
            ProfileService profileService) {
        this.gitService = gitService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.gitDiffReportParserService = gitDiffReportParserService;
        this.profileService = profileService;
    }

    /**
     * Updates the ProgrammingExerciseGitDiffReport of a programming exercise.
     * If there were no changes since the last report was created this will not do anything.
     * If there were changes to at least one of the repositories a new report will be created.
     * This method should not be called twice for the same programming exercise at the same time, as this will result in
     * the creation of 2 reports. See <a href="https://github.com/ls1intum/Artemis/pull/4893">Artemis 4893</a> for more information about it.
     *
     * @param programmingExercise The programming exercise
     * @return The git-diff report for the given programming exercise
     */
    public ProgrammingExerciseGitDiffReport updateReport(ProgrammingExercise programmingExercise) {
        var templateParticipationOptional = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
        var solutionParticipationOptional = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId());
        if (templateParticipationOptional.isEmpty() || solutionParticipationOptional.isEmpty()) {
            return null;
        }
        var templateParticipation = templateParticipationOptional.get();
        var solutionParticipation = solutionParticipationOptional.get();

        var templateSubmissionOptional = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(templateParticipation.getId());
        var solutionSubmissionOptional = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(solutionParticipation.getId());
        if (templateSubmissionOptional.isEmpty() || solutionSubmissionOptional.isEmpty()) {
            return null;
        }
        var templateSubmission = templateSubmissionOptional.get();
        var solutionSubmission = solutionSubmissionOptional.get();

        var templateHash = templateSubmission.getCommitHash();
        var solutionHash = solutionSubmission.getCommitHash();
        var existingReport = getReportOfExercise(programmingExercise);
        if (existingReport != null && canUseExistingReport(existingReport, templateHash, solutionHash)) {
            return existingReport;
        }

        try {
            var newReport = createReportForTemplateWithSolution(templateParticipation.getVcsRepositoryUri(), solutionParticipation.getVcsRepositoryUri());
            newReport.setTemplateRepositoryCommitHash(templateHash);
            newReport.setSolutionRepositoryCommitHash(solutionHash);
            newReport.setProgrammingExercise(programmingExercise);
            // Delete any old report first
            if (existingReport != null) {
                programmingExerciseGitDiffReportRepository.delete(existingReport);
            }
            newReport = programmingExerciseGitDiffReportRepository.save(newReport);
            programmingExerciseRepository.save(programmingExercise);
            return newReport;
        }
        catch (GitAPIException | IOException e) {
            log.error("Exception while generating git diff report", e);
            throw new InternalServerErrorException("Error while generating git-diff: " + e.getMessage());
        }
    }

    /**
     * Obtain the {@link ProgrammingExerciseGitDiffReport} of a programming exercise.
     * Contains proper error handling in case more than one report exists.
     *
     * @param programmingExercise The programming exercise
     * @return The report or null if none exists
     */
    private ProgrammingExerciseGitDiffReport getReportOfExercise(ProgrammingExercise programmingExercise) {
        var reports = programmingExerciseGitDiffReportRepository.findByProgrammingExerciseId(programmingExercise.getId());
        if (reports.isEmpty()) {
            return null;
        }
        else if (reports.size() == 1) {
            return reports.getFirst();
        }
        // Error handling in case more than one reports exist for the exercise
        var latestReport = reports.stream().max(Comparator.comparing(DomainObject::getId)).get();
        var reportsToDelete = new ArrayList<>(reports);
        reportsToDelete.remove(latestReport);
        programmingExerciseGitDiffReportRepository.deleteAll(reportsToDelete);
        return latestReport;
    }

    /**
     * Obtain the {@link ProgrammingExerciseGitDiffReport} of a programming exercise.
     * If no report exists yet, it will try to generate one
     *
     * @param programmingExercise The programming exercise
     * @return The report or null if none can be generated
     */
    public ProgrammingExerciseGitDiffReport getOrCreateReportOfExercise(ProgrammingExercise programmingExercise) {
        var report = getReportOfExercise(programmingExercise);
        if (report == null) {
            return updateReport(programmingExercise);
        }
        else {
            return report;
        }
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport for a submission with the template repository.
     *
     * @param exercise   The exercise for which the report should be created
     * @param submission The submission for which the report should be created
     * @return The report with the changes between the submission and the template
     * @throws GitAPIException If an error occurs while accessing the git repository
     * @throws IOException     If an error occurs while accessing the file system
     */
    public ProgrammingExerciseGitDiffReport createReportForSubmissionWithTemplate(ProgrammingExercise exercise, ProgrammingSubmission submission)
            throws GitAPIException, IOException {
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        var vcsRepositoryUri = ((ProgrammingExerciseParticipation) submission.getParticipation()).getVcsRepositoryUri();
        Repository templateRepo;
        Repository submissionRepository;

        // TODO find a way to make this work gitService.getBareRepository(vcsRepositoryUri);

        // if (profileService.isLocalVcsActive()) {
        // templateRepo = gitService.getBareRepository(templateParticipation.getVcsRepositoryUri());
        // submissionRepository = gitService.checkoutRepositoryAtCommit(vcsRepositoryUri, submission.getCommitHash(), false);
        // } else {
        templateRepo = prepareRepository(templateParticipation.getVcsRepositoryUri());
        submissionRepository = gitService.checkoutRepositoryAtCommit(vcsRepositoryUri, submission.getCommitHash(), false);

        var oldTreeParser = new FileTreeIterator(templateRepo);
        var newTreeParser = new FileTreeIterator(submissionRepository);
        var report = createReport(templateRepo, oldTreeParser, newTreeParser);
        gitService.switchBackToDefaultBranchHead(submissionRepository);
        return report;
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

    /**
     * Creates a new ProgrammingExerciseGitDiffReport for an exercise.
     * It will take the git-diff between the template and solution repositories and return all changes.
     *
     * @param templateVcsRepositoryUri The vcsRepositoryUri of the template participation
     * @param solutionVcsRepositoryUri The vcsRepositoryUri of the solution participation
     * @return The changes between template and solution
     * @throws GitAPIException If there was an issue with JGit
     */
    private ProgrammingExerciseGitDiffReport createReportForTemplateWithSolution(VcsRepositoryUri templateVcsRepositoryUri, VcsRepositoryUri solutionVcsRepositoryUri)
            throws GitAPIException, IOException {
        Repository templateRepo;
        Repository solutionRepo;
        // TODO: in case of LocalVC, we should calculate the diff in the bare origin repository
        // TODO localVC - find a way to compare commits of different repositories
        templateRepo = prepareRepository(templateVcsRepositoryUri);
        solutionRepo = prepareRepository(solutionVcsRepositoryUri);

        var oldTreeParser = new FileTreeIterator(templateRepo);
        var newTreeParser = new FileTreeIterator(solutionRepo);

        return createReport(templateRepo, oldTreeParser, newTreeParser);
    }

    /**
     * Prepares a repository for the git diff calculation by checking it out and resetting it to the origin head.
     *
     * @param vcsRepositoryUri The vcsRepositoryUri of the participation of the repository
     * @return The checked out template repository
     * @throws GitAPIException If an error occurs while accessing the git repository
     */
    private Repository prepareRepository(VcsRepositoryUri vcsRepositoryUri) throws GitAPIException {
        Repository templateRepo = gitService.getOrCheckoutRepository(vcsRepositoryUri, true);
        gitService.resetToOriginHead(templateRepo);
        gitService.pullIgnoreConflicts(templateRepo);
        return templateRepo;
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport containing the git-diff for two submissions.
     *
     * @param submission1 The first submission (older)
     * @param submission2 The second submission (newer)
     * @return The report with the changes between the two submissions
     * @throws GitAPIException If an error occurs while accessing the git repository
     * @throws IOException     If an error occurs while accessing the file system
     */
    public ProgrammingExerciseGitDiffReport generateReportForSubmissions(ProgrammingSubmission submission1, ProgrammingSubmission submission2) throws GitAPIException, IOException {
        var repositoryUri = ((ProgrammingExerciseParticipation) submission1.getParticipation()).getVcsRepositoryUri();
        return generateReportForCommits(repositoryUri, submission1.getCommitHash(), submission2.getCommitHash());
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport containing the git-diff.
     * <p>
     * It parses all files of the repositories in their directories on the file system and creates a report containing the changes.
     * Both repositories have to be checked out at the commit that should be compared and be in different directories
     *
     * @param firstRepo            The first repository
     * @param firstRepoTreeParser  The tree parser for the first repository
     * @param secondRepoTreeParser The tree parser for the second repository
     * @return The report with the changes between the two repositories at their checked out state
     * @throws IOException     If an error occurs while accessing the file system
     * @throws GitAPIException If an error occurs while accessing the git repository
     */
    @NotNull
    private ProgrammingExerciseGitDiffReport createReport(Repository firstRepo, FileTreeIterator firstRepoTreeParser, FileTreeIterator secondRepoTreeParser)
            throws IOException, GitAPIException {
        try (ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream(); Git git = Git.wrap(firstRepo)) {
            git.diff().setOldTree(firstRepoTreeParser).setNewTree(secondRepoTreeParser).setOutputStream(diffOutputStream).call();
            var diff = diffOutputStream.toString();
            var programmingExerciseGitDiffEntries = gitDiffReportParserService.extractDiffEntries(diff, false, true);
            var report = new ProgrammingExerciseGitDiffReport();
            for (ProgrammingExerciseGitDiffEntry gitDiffEntry : programmingExerciseGitDiffEntries) {
                gitDiffEntry.setGitDiffReport(report);
            }
            report.setEntries(new HashSet<>(programmingExerciseGitDiffEntries));
            return report;
        }
    }

    private boolean canUseExistingReport(ProgrammingExerciseGitDiffReport report, String templateHash, String solutionHash) {
        return report.getTemplateRepositoryCommitHash().equals(templateHash) && report.getSolutionRepositoryCommitHash().equals(solutionHash);
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
        var programmingExerciseGitDiffEntries = gitDiffReportParserService.extractDiffEntries(diffs.toString(), false, false);
        var report = new ProgrammingExerciseGitDiffReport();
        for (ProgrammingExerciseGitDiffEntry gitDiffEntry : programmingExerciseGitDiffEntries) {
            gitDiffEntry.setGitDiffReport(report);
        }
        report.setEntries(new HashSet<>(programmingExerciseGitDiffEntries));
        return report;
    }
}
