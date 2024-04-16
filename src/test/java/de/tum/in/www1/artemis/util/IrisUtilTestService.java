package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;

@Service
public class IrisUtilTestService {

    @Autowired
    private GitService gitService;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    /**
     * Sets up a template repository for the given exercise.
     *
     * @param exercise     The exercise for which to set up the template repository.
     * @param templateRepo The template repository to use.
     * @return The exercise with the template repository set up.
     * @throws Exception If the template repository could not be set up.
     */
    public ProgrammingExercise setupTemplate(ProgrammingExercise exercise, LocalRepository templateRepo) throws Exception {
        templateRepo.configureRepos("templateLocalRepo", "templateOriginRepo");

        var templateRepoUri = new GitUtilService.MockFileRepositoryUri(templateRepo.localRepoFile);
        exercise.setTemplateRepositoryUri(templateRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUri, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(templateRepoUri, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUri),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(templateRepoUri),
                eq(false), any());

        var savedExercise = exerciseRepository.save(exercise);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(savedExercise);
        var templateParticipation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        templateParticipation.setRepositoryUri(templateRepoUri.toString());
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        var templateSubmission = new ProgrammingSubmission();
        templateSubmission.setParticipation(templateParticipation);
        programmingSubmissionRepository.save(templateSubmission);

        return savedExercise;
    }

    /**
     * Sets up a student participation for the given exercise.
     *
     * @param participation The participation to set up.
     * @param studentRepo   The student repository to use.
     * @throws Exception If the student participation could not be set up.
     */
    public void setupStudentParticipation(ProgrammingExerciseStudentParticipation participation, LocalRepository studentRepo) throws Exception {
        studentRepo.configureRepos("studentLocalRepo", "studentOriginRepo");

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUri(), true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUri(), false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participation.getVcsRepositoryUri()), eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepo.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participation.getVcsRepositoryUri()), eq(false), any());
    }

    /**
     * Sets up a solution repository for the given exercise.
     *
     * @param exercise     The exercise for which to set up the solution repository.
     * @param solutionRepo The solution repository to use.
     * @return The exercise with the solution repository set up.
     * @throws Exception If the solution repository could not be set up.
     */
    public ProgrammingExercise setupSolution(ProgrammingExercise exercise, LocalRepository solutionRepo) throws Exception {
        solutionRepo.configureRepos("templateLocalRepo", "templateOriginRepo");

        var solutionRepoUri = new GitUtilService.MockFileRepositoryUri(solutionRepo.localRepoFile);
        exercise.setSolutionRepositoryUri(solutionRepoUri.toString());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUri, true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(solutionRepoUri, false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUri),
                eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepo.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(eq(solutionRepoUri),
                eq(false), any());

        var savedExercise = exerciseRepository.save(exercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(savedExercise);
        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(savedExercise.getId()).orElseThrow();
        solutionParticipation.setRepositoryUri(solutionRepoUri.toString());
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        var solutionSubmission = new ProgrammingSubmission();
        solutionSubmission.setParticipation(solutionParticipation);
        programmingSubmissionRepository.save(solutionSubmission);

        return savedExercise;
    }

    /**
     * Sets up a test repository for the given exercise.
     *
     * @param exercise The exercise for which to set up the test repository.
     * @param testRepo The test repository to use.
     * @return The exercise with the test repository set up.
     * @throws Exception If the test repository could not be set up.
     */
    public ProgrammingExercise setupTest(ProgrammingExercise exercise, LocalRepository testRepo) throws Exception {
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");

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
