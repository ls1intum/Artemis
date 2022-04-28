package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.connector.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.GitService;

/**
 * Utility service specifically used for testing Hestia related functionality.
 * This currently includes:
 * - Setting up a template repository
 * - Setting up a solution repository
 * - Setting up a test repository
 *
 * In the future this service will be extended to make testing of the code hint generation easier.
 */
@Service
public class HestiaUtilTestService {

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Autowired
    private GitService gitService;

    @Autowired
    private UrlService urlService;

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    // required=false is necessary, as this will otherwise fail when not part of a AbstractSpringIntegrationBambooBitbucketJiraTest
    @Autowired(required = false)
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    /**
     * Sets up the template repository of a programming exercise with a single file
     *
     * @param fileName The name of the file
     * @param content The content of the file
     * @param exercise The programming exercise
     * @param templateRepo The repository
     * @return The programming exercise
     */
    public ProgrammingExercise setupTemplate(String fileName, String content, ProgrammingExercise exercise, LocalRepository templateRepo) throws Exception {
        return setupTemplate(Collections.singletonMap(fileName, content), exercise, templateRepo);
    }

    /**
     * Sets up the template repository of a programming exercise with specified files
     *
     * @param files        The fileNames mapped to the content of the files
     * @param exercise     The programming exercise
     * @param templateRepo The repository
     * @return The programming exercise
     */
    public ProgrammingExercise setupTemplate(Map<String, String> files, ProgrammingExercise exercise, LocalRepository templateRepo) throws Exception {
        templateRepo.configureRepos("templateLocalRepo", "templateOriginRepo");

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
            // add file to the repository folder
            Path filePath = Path.of(templateRepo.localRepoFile + "/" + fileName);
            Files.createDirectories(filePath.getParent());
            File solutionFile = Files.createFile(filePath).toFile();
            // write content to the created file
            FileUtils.write(solutionFile, content, Charset.defaultCharset());
        }

        var templateRepoUrl = new GitUtilService.MockFileRepositoryUrl(templateRepo.localRepoFile);
        exercise.setTemplateRepositoryUrl(templateRepoUrl.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUrl, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUrl),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUrl),
                eq(false), any());

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, urlService.getProjectKeyFromRepositoryUrl(templateRepoUrl));

        var savedExercise = exerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(savedExercise);
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUrl(templateRepoUrl.toString());
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        var templateSubmission = new ProgrammingSubmission();
        templateSubmission.setParticipation(templateParticipation);
        templateSubmission.setCommitHash(String.valueOf(files.hashCode()));
        programmingSubmissionRepository.save(templateSubmission);

        return savedExercise;
    }

    /**
     * Sets up the solution repository of a programming exercise with a single file
     *
     * @param fileName The name of the file
     * @param content The content of the file
     * @param exercise The programming exercise
     * @param solutionRepo The repository
     * @return The programming exercise
     */
    public ProgrammingExercise setupSolution(String fileName, String content, ProgrammingExercise exercise, LocalRepository solutionRepo) throws Exception {
        return setupSolution(Collections.singletonMap(fileName, content), exercise, solutionRepo);
    }

    /**
     * Sets up the solution repository of a programming exercise with specified files
     *
     * @param files        The fileNames mapped to the content of the files
     * @param exercise     The programming exercise
     * @param solutionRepo The repository
     * @return The programming exercise
     */
    public ProgrammingExercise setupSolution(Map<String, String> files, ProgrammingExercise exercise, LocalRepository solutionRepo) throws Exception {
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
            // add file to the repository folder
            Path filePath = Path.of(solutionRepo.localRepoFile + "/" + fileName);
            Files.createDirectories(filePath.getParent());
            File solutionFile = Files.createFile(filePath).toFile();
            // write content to the created file
            FileUtils.write(solutionFile, content, Charset.defaultCharset());
        }

        var solutionRepoUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.localRepoFile);
        exercise.setSolutionRepositoryUrl(solutionRepoUrl.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUrl, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUrl),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUrl),
                eq(false), any());

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, urlService.getProjectKeyFromRepositoryUrl(solutionRepoUrl));

        var savedExercise = exerciseRepository.save(exercise);
        database.addSolutionParticipationForProgrammingExercise(savedExercise);
        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        solutionParticipation.setRepositoryUrl(solutionRepoUrl.toString());
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        var solutionSubmission = new ProgrammingSubmission();
        solutionSubmission.setParticipation(solutionParticipation);
        solutionSubmission.setCommitHash(String.valueOf(files.hashCode()));
        programmingSubmissionRepository.save(solutionSubmission);

        return savedExercise;
    }

    /**
     * Sets up the test repository of a programming exercise with a single file
     *
     * @param fileName The name of the file
     * @param content The content of the file
     * @param exercise The programming exercise
     * @param testRepo The repository
     * @return The programming exercise
     */
    public ProgrammingExercise setupTests(String fileName, String content, ProgrammingExercise exercise, LocalRepository testRepo) throws Exception {
        return setupTests(Collections.singletonMap(fileName, content), exercise, testRepo);
    }

    /**
     * Sets up the test repository of a programming exercise with specified files
     *
     * @param files    The fileNames mapped to the content of the files
     * @param exercise The programming exercise
     * @param testRepo The repository
     * @return The programming exercise
     */
    public ProgrammingExercise setupTests(Map<String, String> files, ProgrammingExercise exercise, LocalRepository testRepo) throws Exception {
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
            // add file to the repository folder
            Path filePath = Path.of(testRepo.localRepoFile + "/" + fileName);
            Files.createDirectories(filePath.getParent());
            File solutionFile = Files.createFile(filePath).toFile();
            // write content to the created file
            FileUtils.write(solutionFile, content, Charset.defaultCharset());
        }

        var testRepoUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.localRepoFile);
        exercise.setTestRepositoryUrl(testRepoUrl.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(testRepoUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(testRepoUrl, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUrl), eq(true),
                any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUrl), eq(false),
                any());

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, urlService.getProjectKeyFromRepositoryUrl(testRepoUrl));

        return exerciseRepository.save(exercise);
    }
}
