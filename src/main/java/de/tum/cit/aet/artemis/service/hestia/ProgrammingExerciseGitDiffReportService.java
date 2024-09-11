package de.tum.cit.aet.artemis.service.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.Repository;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.web.rest.GitDiffReportParserService;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorException;

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

    private final FileService fileService;

    private final GitDiffReportParserService gitDiffReportParserService;

    public ProgrammingExerciseGitDiffReportService(GitService gitService, ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, FileService fileService,
            GitDiffReportParserService gitDiffReportParserService) {
        this.gitService = gitService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.fileService = fileService;
        this.gitDiffReportParserService = gitDiffReportParserService;
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
        var existingReport = this.getReportOfExercise(programmingExercise);
        if (existingReport != null && canUseExistingReport(existingReport, templateHash, solutionHash)) {
            return existingReport;
        }

        try {
            var newReport = generateReport(templateParticipation, solutionParticipation);
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
        var report = this.getReportOfExercise(programmingExercise);
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
        Repository templateRepo = prepareTemplateRepository(templateParticipation);

        var repo1 = gitService.checkoutRepositoryAtCommit(((ProgrammingExerciseParticipation) submission.getParticipation()).getVcsRepositoryUri(), submission.getCommitHash(),
                false);
        var oldTreeParser = new FileTreeIterator(templateRepo);
        var newTreeParser = new FileTreeIterator(repo1);
        var report = createReport(templateRepo, oldTreeParser, newTreeParser);
        gitService.switchBackToDefaultBranchHead(repo1);
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
            return gitDiffReportParserService.extractDiffEntries(diff, true).stream().mapToInt(ProgrammingExerciseGitDiffEntry::getLineCount).sum();
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
     * @param templateParticipation The participation for the template
     * @param solutionParticipation The participation for the solution
     * @return The changes between template and solution
     * @throws GitAPIException If there was an issue with JGit
     */
    private ProgrammingExerciseGitDiffReport generateReport(TemplateProgrammingExerciseParticipation templateParticipation,
            SolutionProgrammingExerciseParticipation solutionParticipation) throws GitAPIException, IOException {
        Repository templateRepo = prepareTemplateRepository(templateParticipation);
        var solutionRepo = gitService.getOrCheckoutRepository(solutionParticipation.getVcsRepositoryUri(), true);
        gitService.resetToOriginHead(solutionRepo);
        gitService.pullIgnoreConflicts(solutionRepo);

        var oldTreeParser = new FileTreeIterator(templateRepo);
        var newTreeParser = new FileTreeIterator(solutionRepo);

        return createReport(templateRepo, oldTreeParser, newTreeParser);
    }

    /**
     * Prepares the template repository for the git diff calculation by checking it out and resetting it to the origin head.
     *
     * @param templateParticipation The participation for the template
     * @return The checked out template repository
     * @throws GitAPIException If an error occurs while accessing the git repository
     */
    private Repository prepareTemplateRepository(TemplateProgrammingExerciseParticipation templateParticipation) throws GitAPIException {
        var templateRepo = gitService.getOrCheckoutRepository(templateParticipation.getVcsRepositoryUri(), true);
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
        var repo1 = gitService.getOrCheckoutRepository(repositoryUri, true);
        var repo1Path = repo1.getLocalPath();
        var repo2Path = fileService.getTemporaryUniqueSubfolderPath(repo1Path.getParent(), 5);
        FileSystemUtils.copyRecursively(repo1Path, repo2Path);
        repo1 = gitService.checkoutRepositoryAtCommit(repo1, submission1.getCommitHash());
        var repo2 = gitService.getExistingCheckedOutRepositoryByLocalPath(repo2Path, repositoryUri);
        repo2 = gitService.checkoutRepositoryAtCommit(repo2, submission2.getCommitHash());
        return parseFilesAndCreateReport(repo1, repo2);
    }

    /**
     * Parses the files of the given repositories and creates a new ProgrammingExerciseGitDiffReport containing the git-diff.
     *
     * @param repo1 The first repository
     * @param repo2 The second repository
     * @return The report with the changes between the two repositories at their checked out state
     * @throws IOException     If an error occurs while accessing the file system
     * @throws GitAPIException If an error occurs while accessing the git repository
     */
    @NotNull
    private ProgrammingExerciseGitDiffReport parseFilesAndCreateReport(Repository repo1, Repository repo2) throws IOException, GitAPIException {
        var oldTreeParser = new FileTreeIterator(repo1);
        var newTreeParser = new FileTreeIterator(repo2);

        var report = createReport(repo1, oldTreeParser, newTreeParser);
        gitService.switchBackToDefaultBranchHead(repo1);
        gitService.switchBackToDefaultBranchHead(repo2);
        return report;
    }

    /**
     * Creates a new ProgrammingExerciseGitDiffReport containing the git-diff.
     * <p>
     * It parses all files of the repositories in their directories on the file system and creates a report containing the changes.
     * Both repositories have to be checked out at the commit that should be compared and be in different directories
     *
     * @param repo1         The first repository
     * @param oldTreeParser The tree parser for the first repository
     * @param newTreeParser The tree parser for the second repository
     * @return The report with the changes between the two repositories at their checked out state
     * @throws IOException     If an error occurs while accessing the file system
     * @throws GitAPIException If an error occurs while accessing the git repository
     */
    @NotNull
    private ProgrammingExerciseGitDiffReport createReport(Repository repo1, FileTreeIterator oldTreeParser, FileTreeIterator newTreeParser) throws IOException, GitAPIException {
        try (ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream(); Git git = Git.wrap(repo1)) {
            git.diff().setOldTree(oldTreeParser).setNewTree(newTreeParser).setOutputStream(diffOutputStream).call();
            var diff = diffOutputStream.toString();
            var programmingExerciseGitDiffEntries = gitDiffReportParserService.extractDiffEntries(diff, false);
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

}
