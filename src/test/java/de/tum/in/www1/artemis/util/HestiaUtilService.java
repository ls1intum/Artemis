package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class HestiaUtilService {

    @Autowired
    private GitService gitService;

    @Autowired
    private UrlService urlService;

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    public ProgrammingExercise setupTemplate(String content, String fileName, ProgrammingExercise exercise, LocalRepository templateRepo) throws Exception {
        templateRepo.configureRepos("templateLocalRepo", "templateOriginRepo");

        // add file to the repository folder
        Path filePath = Paths.get(templateRepo.localRepoFile + "/" + fileName);
        File templateFile = Files.createFile(filePath).toFile();
        // write content to the created file
        org.apache.commons.io.FileUtils.write(templateFile, content, Charset.defaultCharset());

        var templateRepoUrl = new GitUtilService.MockFileRepositoryUrl(templateRepo.localRepoFile);
        exercise.setTemplateRepositoryUrl(templateRepoUrl.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUrl, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUrl),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUrl),
                eq(false), any());

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch("master", urlService.getProjectKeyFromRepositoryUrl(templateRepoUrl));

        exercise = exerciseRepository.save(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUrl(templateRepoUrl.toString());
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        var templateSubmission = new ProgrammingSubmission();
        templateSubmission.setParticipation(templateParticipation);
        templateSubmission.setCommitHash(String.valueOf(content.hashCode()));
        programmingSubmissionRepository.save(templateSubmission);

        return exercise;
    }

    public ProgrammingExercise setupSolution(String content, String fileName, ProgrammingExercise exercise, LocalRepository solutionRepo) throws Exception {
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        // add file to the repository folder
        Path filePath = Paths.get(solutionRepo.localRepoFile + "/" + fileName);
        File solutionFile = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(solutionFile, content, Charset.defaultCharset());

        var solutionRepoUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.localRepoFile);
        exercise.setSolutionRepositoryUrl(solutionRepoUrl.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUrl, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUrl, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUrl),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUrl),
                eq(false), any());

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch("master", urlService.getProjectKeyFromRepositoryUrl(solutionRepoUrl));

        exercise = exerciseRepository.save(exercise);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId()).orElseThrow();
        solutionParticipation.setRepositoryUrl(solutionRepoUrl.toString());
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        var solutionSubmission = new ProgrammingSubmission();
        solutionSubmission.setParticipation(solutionParticipation);
        solutionSubmission.setCommitHash(String.valueOf(content.hashCode()));
        programmingSubmissionRepository.save(solutionSubmission);

        return exercise;
    }
}
