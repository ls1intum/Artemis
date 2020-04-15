package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;
import de.tum.in.www1.artemis.service.util.VCSSimulationUtils;

/**
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Profile("dev")
@Service
public class ProgrammingExerciseSimulationService {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    private final GroupNotificationService groupNotificationService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    public final String domain = "nolocalsetup:7990/scm/";

    public ProgrammingExerciseSimulationService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseScheduleService programmingExerciseScheduleService,
            GroupNotificationService groupNotificationService, ProgrammingExerciseService programmingExerciseService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseScheduleService = programmingExerciseScheduleService;
        this.groupNotificationService = groupNotificationService;
        this.programmingExerciseService = programmingExerciseService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Setups the context of a new programming exercise.
     * @param programmingExercise the exercise which should be stored in the database
     * @return returns the modified and stored programming exercise
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     */
    @Transactional
    public ProgrammingExercise setupProgrammingExerciseWithoutVersionControlAndContinuousIntegrationAvailable(ProgrammingExercise programmingExercise) {
        programmingExercise.generateAndSetProjectKey();
        final var projectKey = programmingExercise.getProjectKey();
        // TODO: the following code is used quite often and should be done in only one place
        final var exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        final var testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        final var solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();

        programmingExerciseService.initParticipations(programmingExercise);
        setURLsAndBuildPlanIDsForNewExerciseWithoutVersionControlAndContinuousIntegrationAvailable(programmingExercise, exerciseRepoName, testRepoName, solutionRepoName);
        // Save participations to get the ids required for the webhooks
        programmingExerciseService.connectBaseParticipationsToExerciseAndSave(programmingExercise);

        // save to get the id required for the webhook
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        // The creation of the webhooks must occur after the initial push, because the participation is
        // not yet saved in the database, so we cannot save the submission accordingly (see ProgrammingSubmissionService.notifyPush)
        programmingExerciseScheduleService.scheduleExerciseIfRequired(programmingExercise);
        groupNotificationService.notifyTutorGroupAboutExerciseCreated(programmingExercise);

        return programmingExercise;
    }

    /**
     * Sets the url and buildplan ids for the new exercise
     * @param programmingExercise the new exercise
     * @param exerciseRepoName the repo name of the new exercise
     * @param testRepoName the test repo name of the new exercise
     * @param solutionRepoName the solution repo name of the new exercise
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     */
    private void setURLsAndBuildPlanIDsForNewExerciseWithoutVersionControlAndContinuousIntegrationAvailable(ProgrammingExercise programmingExercise, String exerciseRepoName,
            String testRepoName, String solutionRepoName) {
        final var projectKey = programmingExercise.getProjectKey();
        final var templateParticipation = programmingExercise.getTemplateParticipation();
        final var solutionParticipation = programmingExercise.getSolutionParticipation();
        final var templatePlanName = TEMPLATE.getName();
        final var solutionPlanName = SOLUTION.getName();
        final var exerciseRepoUrl = "http://" + domain + projectKey + "/" + exerciseRepoName + ".git";
        final var testsRepoUrl = "http://" + domain + projectKey + "/" + testRepoName + ".git";
        final var solutionRepoUrl = "http://" + domain + projectKey + "/" + solutionRepoName + ".git";
        templateParticipation.setBuildPlanId(projectKey + "-" + templatePlanName);
        templateParticipation.setRepositoryUrl(exerciseRepoUrl);
        solutionParticipation.setBuildPlanId(projectKey + "-" + solutionPlanName);
        solutionParticipation.setRepositoryUrl(solutionRepoUrl);
        programmingExercise.setTestRepositoryUrl(testsRepoUrl);
    }

    /**
     * This method creates the template and solution submissions and results for the new exercise
     * These submissions and results are SIMULATIONS for the testing of programming exercises without a connection to
     * the VCS and Continuous Integration server
     * @param programmingExercise the new exercise
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     */
    public void setupInitialSubmissionsAndResults(ProgrammingExercise programmingExercise) {
        Optional<TemplateProgrammingExerciseParticipation> templateProgrammingExerciseParticipation = this.templateProgrammingExerciseParticipationRepository
                .findByProgrammingExerciseId(programmingExercise.getId());
        Optional<SolutionProgrammingExerciseParticipation> solutionProgrammingExerciseParticipation = this.solutionProgrammingExerciseParticipationRepository
                .findByProgrammingExerciseId(programmingExercise.getId());
        String commitHashBase = VCSSimulationUtils.simulateCommitHash();
        ProgrammingSubmission programmingSubmissionBase = new ProgrammingSubmission();
        programmingSubmissionBase.setParticipation(templateProgrammingExerciseParticipation.get());
        programmingSubmissionBase.setSubmitted(true);
        programmingSubmissionBase.setType(SubmissionType.OTHER);
        programmingSubmissionBase.setCommitHash(commitHashBase);
        programmingSubmissionBase.setSubmissionDate(templateProgrammingExerciseParticipation.get().getInitializationDate());
        programmingSubmissionRepository.save(programmingSubmissionBase);
        Result resultBase = new Result();
        resultBase.setParticipation(templateProgrammingExerciseParticipation.get());
        resultBase.setSubmission(programmingSubmissionBase);
        resultBase.setRated(true);
        resultBase.resultString("0 of 13 passed");
        resultBase.setAssessmentType(AssessmentType.AUTOMATIC);
        resultBase.score(0L);
        resultBase.setCompletionDate(templateProgrammingExerciseParticipation.get().getInitializationDate());
        resultRepository.save(resultBase);

        ProgrammingSubmission programmingSubmissionSolution = new ProgrammingSubmission();
        String commitHashSolution = VCSSimulationUtils.simulateCommitHash();
        programmingSubmissionSolution.setParticipation(solutionProgrammingExerciseParticipation.get());
        programmingSubmissionSolution.setSubmitted(true);
        programmingSubmissionSolution.setType(SubmissionType.OTHER);
        programmingSubmissionSolution.setCommitHash(commitHashSolution);
        programmingSubmissionSolution.setSubmissionDate(solutionProgrammingExerciseParticipation.get().getInitializationDate());
        programmingSubmissionRepository.save(programmingSubmissionSolution);
        Result resultSolution = new Result();
        resultSolution.setParticipation(solutionProgrammingExerciseParticipation.get());
        resultSolution.setSubmission(programmingSubmissionSolution);
        resultSolution.setRated(true);
        resultSolution.resultString("13 of 13 passed");
        resultSolution.score(100L);
        resultSolution.setAssessmentType(AssessmentType.AUTOMATIC);
        resultSolution.setCompletionDate(solutionProgrammingExerciseParticipation.get().getInitializationDate());
        resultRepository.save(resultSolution);
    }

}
