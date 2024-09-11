package de.tum.cit.aet.artemis.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.participation.ParticipationFactory;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.repository.ProgrammingSubmissionTestRepository;

/**
 * Utility service specifically used for testing Hestia related functionality.
 * This currently includes:
 * - Setting up a template repository
 * - Setting up a solution repository
 * - Setting up a test repository
 * <p>
 * In the future this service will be extended to make testing of the code hint generation easier.
 */
@Service
public class HestiaUtilTestService {

    @Autowired
    private GitService gitService;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    /**
     * Sets up the template repository of a programming exercise with a single file
     *
     * @param fileName     The name of the file
     * @param content      The content of the file
     * @param exercise     The programming exercise
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

        var templateRepoUri = new GitUtilService.MockFileRepositoryUri(templateRepo.localRepoFile);
        exercise.setTemplateRepositoryUri(templateRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUri, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUri, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUri),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUri),
                eq(false), any());
        doNothing().when(gitService).pullIgnoreConflicts(any(Repository.class));
        exercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig()));
        var savedExercise = exerciseRepository.save(exercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(savedExercise);
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUri(templateRepoUri.toString());
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
     * @param fileName     The name of the file
     * @param content      The content of the file
     * @param exercise     The programming exercise
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

        var solutionRepoUri = new GitUtilService.MockFileRepositoryUri(solutionRepo.localRepoFile);
        exercise.setSolutionRepositoryUri(solutionRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUri, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUri, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUri),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUri),
                eq(false), any());

        var buildConfig = programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
        exercise.setBuildConfig(buildConfig);
        var savedExercise = exerciseRepository.save(exercise);
        savedExercise.setBuildConfig(buildConfig);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(savedExercise);
        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        solutionParticipation.setRepositoryUri(solutionRepoUri.toString());
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        var solutionSubmission = new ProgrammingSubmission();
        solutionSubmission.setParticipation(solutionParticipation);
        solutionSubmission.setCommitHash(String.valueOf(files.hashCode()));
        programmingSubmissionRepository.save(solutionSubmission);
        return savedExercise;
    }

    public ProgrammingSubmission setupSubmission(String fileName, String content, ProgrammingExercise exercise, LocalRepository participationRepo, String login) throws Exception {
        return setupSubmission(Collections.singletonMap(fileName, content), exercise, participationRepo, login);
    }

    public ProgrammingSubmission deleteFileAndSetupSubmission(String oldFileName, String newFileName, String content, ProgrammingExercise exercise,
            LocalRepository participationRepo, String login) throws Exception {
        Path oldFilePath = Path.of(participationRepo.localRepoFile + "/" + oldFileName);
        Files.delete(oldFilePath);
        // Ensure JGit realizes the file has been removed
        participationRepo.localGit.rm().addFilepattern(oldFileName).call();
        return setupSubmission(newFileName, content, exercise, participationRepo, login);
    }

    public ProgrammingSubmission setupSubmission(Map<String, String> files, ProgrammingExercise exercise, LocalRepository participationRepo, String login) throws Exception {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
            // add file to the repository folder
            Path filePath = Path.of(participationRepo.localRepoFile + "/" + fileName);
            Files.createDirectories(filePath.getParent());
            // write content to the created file
            FileUtils.write(filePath.toFile(), content, Charset.defaultCharset());
        }
        participationRepo.localGit.add().addFilepattern(".").call();
        GitService.commit(participationRepo.localGit).setMessage("commit").call();
        participationRepo.localGit.push().call();
        var commits = participationRepo.localGit.log().call();
        var commitsList = StreamSupport.stream(commits.spliterator(), false).toList();

        var participationRepoUri = new GitUtilService.MockFileRepositoryUri(participationRepo.localRepoFile);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participationRepoUri, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participationRepoUri, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participationRepoUri), eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participationRepoUri), eq(false), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(any(),
                anyBoolean());

        var participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo(exercise, login, participationRepo.localRepoFile.toURI());
        doReturn(gitService.linkRepositoryForExistingGit(participationRepo.originRepoFile.toPath(), null, "main", true)).when(gitService).getBareRepository(any());
        var submission = ParticipationFactory.generateProgrammingSubmission(true, commitsList.getFirst().getId().getName(), SubmissionType.MANUAL);
        participation = programmingExerciseStudentParticipationRepository
                .findWithSubmissionsByExerciseIdAndParticipationIds(exercise.getId(), Collections.singletonList(participation.getId())).getFirst();
        return (ProgrammingSubmission) participationUtilService.addSubmission(participation, submission);
    }

    /**
     * Sets up the test repository of a programming exercise with a single file
     *
     * @param fileName The name of the file
     * @param content  The content of the file
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

        var testRepoUri = new GitUtilService.MockFileRepositoryUri(testRepo.localRepoFile);
        exercise.setTestRepositoryUri(testRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(testRepoUri, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(testRepoUri, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUri), eq(true),
                any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(testRepoUri), eq(false),
                any());

        return exerciseRepository.save(exercise);
    }
}
