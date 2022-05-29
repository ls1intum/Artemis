package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.SubmissionPolicyService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.util.VCSSimulationUtils;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Only for local development
 * This class simulates a programming exercises without a connection to a vcs and ci server
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Profile("dev")
@Service
public class ProgrammingExerciseSimulationService {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GroupNotificationService groupNotificationService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    public final String domain = "artemislocalhost:7990/scm/";

    private final InstanceMessageSendService instanceMessageSendService;

    private final SubmissionPolicyService submissionPolicyService;

    public ProgrammingExerciseSimulationService(ProgrammingExerciseRepository programmingExerciseRepository, GroupNotificationService groupNotificationService,
            ProgrammingExerciseService programmingExerciseService, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository, InstanceMessageSendService instanceMessageSendService, SubmissionPolicyService submissionPolicyService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.groupNotificationService = groupNotificationService;
        this.programmingExerciseService = programmingExerciseService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.submissionPolicyService = submissionPolicyService;
    }

    /**
     * Setups the context of a new programming exercise.
     * @param programmingExercise the exercise which should be stored in the database
     * @return returns the modified and stored programming exercise
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     */
    @Transactional // ok because we create many objects in a rather complex way and need a rollback in case of exceptions
    public ProgrammingExercise createProgrammingExerciseWithoutVersionControlAndContinuousIntegrationAvailable(ProgrammingExercise programmingExercise) {
        programmingExercise.generateAndSetProjectKey();

        programmingExerciseService.initParticipations(programmingExercise);
        setURLsAndBuildPlanIDsForNewExerciseWithoutVersionControlAndContinuousIntegrationAvailable(programmingExercise);

        programmingExerciseService.connectBaseParticipationsToExerciseAndSave(programmingExercise);
        submissionPolicyService.validateSubmissionPolicyCreation(programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        // The creation of the webhooks must occur after the initial push, because the participation is
        // not yet saved in the database, so we cannot save the submission accordingly (see ProgrammingSubmissionService.notifyPush)
        instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExercise.getId());
        if (programmingExercise.getReleaseDate() == null || !programmingExercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            groupNotificationService.notifyAllGroupsAboutReleasedExercise(programmingExercise);
        }
        else {
            instanceMessageSendService.sendExerciseReleaseNotificationSchedule(programmingExercise.getId());
        }

        return programmingExercise;
    }

    /**
     * Sets the url and build plan ids for the new exercise
     * @param programmingExercise the new exercise
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     */
    private void setURLsAndBuildPlanIDsForNewExerciseWithoutVersionControlAndContinuousIntegrationAvailable(ProgrammingExercise programmingExercise) {
        final var exerciseRepoName = programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = programmingExercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = programmingExercise.generateRepositoryName(RepositoryType.TESTS);

        final var projectKey = programmingExercise.getProjectKey();
        final var templateParticipation = programmingExercise.getTemplateParticipation();
        final var solutionParticipation = programmingExercise.getSolutionParticipation();
        final var exerciseRepoUrl = "https://" + domain + projectKey + "/" + exerciseRepoName + ".git";
        final var testsRepoUrl = "https://" + domain + projectKey + "/" + testRepoName + ".git";
        final var solutionRepoUrl = "https://" + domain + projectKey + "/" + solutionRepoName + ".git";
        templateParticipation.setBuildPlanId(programmingExercise.generateBuildPlanId(TEMPLATE));
        templateParticipation.setRepositoryUrl(exerciseRepoUrl);
        solutionParticipation.setBuildPlanId(programmingExercise.generateBuildPlanId(SOLUTION));
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
        var templateProgrammingExerciseParticipation = this.templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow(
                () -> new EntityNotFoundException("TemplateProgrammingExerciseParticipation with programming exercise with " + programmingExercise.getId() + " does not exist"));
        var solutionProgrammingExerciseParticipation = this.solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow(
                () -> new EntityNotFoundException("SolutionProgrammingExerciseParticipation with programming exercise with " + programmingExercise.getId() + " does not exist"));

        String commitHashBase = VCSSimulationUtils.simulateCommitHash();
        Result templateResult = createSubmissionAndResult(templateProgrammingExerciseParticipation, commitHashBase);
        templateResult.setScore(0D);
        resultRepository.save(templateResult);

        String commitHashSolution = VCSSimulationUtils.simulateCommitHash();
        Result solutionResult = createSubmissionAndResult(solutionProgrammingExerciseParticipation, commitHashSolution);
        solutionResult.setScore(100D);
        resultRepository.save(solutionResult);
    }

    private Result createSubmissionAndResult(AbstractBaseProgrammingExerciseParticipation templateProgrammingExerciseParticipation, String commitHashBase) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setParticipation(templateProgrammingExerciseParticipation);
        programmingSubmission.setSubmitted(true);
        programmingSubmission.setType(SubmissionType.MANUAL);
        programmingSubmission.setCommitHash(commitHashBase);
        programmingSubmission.setSubmissionDate(templateProgrammingExerciseParticipation.getInitializationDate());
        programmingSubmissionRepository.save(programmingSubmission);
        Result result = new Result();
        result.setParticipation(templateProgrammingExerciseParticipation);
        result.setSubmission(programmingSubmission);
        result.setRated(true);
        result.setCompletionDate(templateProgrammingExerciseParticipation.getInitializationDate());
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        return result;
    }

}
