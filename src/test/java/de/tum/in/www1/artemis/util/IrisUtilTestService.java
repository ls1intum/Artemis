package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.connector.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.GitService;

@Service
public class IrisUtilTestService {

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Autowired
    private GitService gitService;

    @Autowired
    private UrlService urlService;

    @Autowired(required = false)
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    public ProgrammingExercise setupTemplate(ProgrammingExercise exercise, LocalRepository templateRepo) throws Exception {
        templateRepo.configureRepos("templateLocalRepo", "templateOriginRepo");

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
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(savedExercise);
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUrl(templateRepoUrl.toString());
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        var templateSubmission = new ProgrammingSubmission();
        templateSubmission.setParticipation(templateParticipation);
        programmingSubmissionRepository.save(templateSubmission);

        return savedExercise;
    }

    public void setupStudentParticipation(ProgrammingExerciseStudentParticipation participation, LocalRepository studentRepo) throws Exception {
        studentRepo.configureRepos("studentLocalRepo", "studentOriginRepo");

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUrl(), true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUrl(), false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), eq(false), any());

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, urlService.getProjectKeyFromRepositoryUrl(participation.getVcsRepositoryUrl()));
    }

}
