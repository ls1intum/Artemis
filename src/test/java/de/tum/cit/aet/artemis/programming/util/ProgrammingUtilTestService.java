package de.tum.cit.aet.artemis.programming.util;

import static de.tum.cit.aet.artemis.programming.service.AbstractGitService.linkRepositoryForExistingGit;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

/**
 * Utility service specifically used for testing programming exercises.
 * This currently includes:
 * - Setting up a template repository
 * - Setting up a solution repository
 * - Setting up a test repository
 * <p>
 * In the future this service will be extended to make testing of the code hint generation easier.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ProgrammingUtilTestService {

    @Autowired
    private GitService gitService;

    @Autowired
    private ProgrammingExerciseTestRepository exerciseRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    /**
     * Sets up the template repository of a programming exercise with specified files
     *
     * @param files        The fileNames mapped to the content of the files
     * @param exercise     The programming exercise
     * @param templateRepo The repository
     */
    public void setupTemplate(Map<String, String> files, ProgrammingExercise exercise, LocalRepository templateRepo) throws Exception {
        templateRepo.configureRepos(localVCBasePath, "templateLocalRepo", "templateOriginRepo");

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
            // add file to the repository folder
            Path filePath = Path.of(templateRepo.workingCopyGitRepoFile + "/" + fileName);
            Files.createDirectories(filePath.getParent());
            File solutionFile = Files.createFile(filePath).toFile();
            // write content to the created file
            FileUtils.write(solutionFile, content, Charset.defaultCharset());
        }

        var templateRepoUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(templateRepo.workingCopyGitRepoFile, localVCBasePath));
        exercise.setTemplateRepositoryUri(templateRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(templateRepoUri), eq(true), anyBoolean());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(templateRepoUri), eq(false), anyBoolean());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(templateRepoUri), eq(true), anyString(), anyBoolean());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(templateRepoUri), eq(false), anyString(), anyBoolean());
        doNothing().when(gitService).pullIgnoreConflicts(any(Repository.class));
        exercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig()));
        var savedExercise = exerciseRepository.save(exercise);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(savedExercise);
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUri(templateRepoUri.toString());
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        var templateSubmission = new ProgrammingSubmission();
        templateSubmission.setParticipation(templateParticipation);
        templateSubmission.setCommitHash(String.valueOf(files.hashCode()));
        programmingSubmissionRepository.save(templateSubmission);

    }

    /**
     * Sets up the solution repository of a programming exercise with specified files
     *
     * @param files        The fileNames mapped to the content of the files
     * @param exercise     The programming exercise
     * @param solutionRepo The repository
     */
    public void setupSolution(Map<String, String> files, ProgrammingExercise exercise, LocalRepository solutionRepo) throws Exception {
        solutionRepo.configureRepos(localVCBasePath, "solutionLocalRepo", "solutionOriginRepo");

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
            // add file to the repository folder
            Path filePath = Path.of(solutionRepo.workingCopyGitRepoFile + "/" + fileName);
            Files.createDirectories(filePath.getParent());
            File solutionFile = Files.createFile(filePath).toFile();
            // write content to the created file
            FileUtils.write(solutionFile, content, Charset.defaultCharset());
        }

        var solutionRepoUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(solutionRepo.workingCopyGitRepoFile, localVCBasePath));
        exercise.setSolutionRepositoryUri(solutionRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionRepoUri, true, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionRepoUri, false, true);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(solutionRepoUri), eq(true), anyString(), anyBoolean());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(solutionRepoUri), eq(false), anyString(), anyBoolean());

        var buildConfig = programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
        exercise.setBuildConfig(buildConfig);
        var savedExercise = exerciseRepository.save(exercise);
        savedExercise.setBuildConfig(buildConfig);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(savedExercise);
        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        solutionParticipation.setRepositoryUri(solutionRepoUri.toString());
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        var solutionSubmission = new ProgrammingSubmission();
        solutionSubmission.setParticipation(solutionParticipation);
        solutionSubmission.setCommitHash(String.valueOf(files.hashCode()));
        programmingSubmissionRepository.save(solutionSubmission);
    }

    public ProgrammingSubmission setupSubmission(Map<String, String> files, ProgrammingExercise exercise, LocalRepository participationRepo, String login) throws Exception {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String fileName = entry.getKey();
            String content = entry.getValue();
            // add file to the repository folder
            Path filePath = Path.of(participationRepo.workingCopyGitRepoFile + "/" + fileName);
            Files.createDirectories(filePath.getParent());
            // write content to the created file
            FileUtils.write(filePath.toFile(), content, Charset.defaultCharset());
        }
        participationRepo.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(participationRepo.workingCopyGitRepo).setMessage("commit").call();
        participationRepo.workingCopyGitRepo.push().call();
        var commits = participationRepo.workingCopyGitRepo.log().call();
        var commitsList = StreamSupport.stream(commits.spliterator(), false).toList();

        var participationRepoUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(participationRepo.workingCopyGitRepoFile, localVCBasePath));

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participationRepoUri, true, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participationRepoUri, false, true);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participationRepoUri), eq(true), anyString(), anyBoolean());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participationRepoUri), eq(false), anyString(), anyBoolean());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(participationRepo.workingCopyGitRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(any(),
                anyBoolean(), anyBoolean());

        var participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo(exercise, login, participationRepo.workingCopyGitRepoFile.toURI());
        doReturn(linkRepositoryForExistingGit(participationRepo.remoteBareGitRepoFile.toPath(), null, "main", true, true)).when(gitService).getBareRepository(any(), anyBoolean());
        var submission = ParticipationFactory.generateProgrammingSubmission(true, commitsList.getFirst().getId().getName(), SubmissionType.MANUAL);
        participation = programmingExerciseStudentParticipationRepository
                .findWithSubmissionsByExerciseIdAndParticipationIds(exercise.getId(), Collections.singletonList(participation.getId())).getFirst();
        return (ProgrammingSubmission) participationUtilService.addSubmission(participation, submission);
    }
}
