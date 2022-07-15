package de.tum.in.www1.artemis.service.programming;

import java.time.ZonedDateTime;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.util.VCSSimulationUtils;

/**
 * Only for local development
 * Simulates the creation of a programming exercise without a connection to the VCS and CI server
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Profile("dev")
@Service
public class ProgrammingSubmissionResultSimulationService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ParticipationRepository participationRepository;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ParticipationService participationService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseSimulationService programmingExerciseSimulationService;

    public ProgrammingSubmissionResultSimulationService(ParticipationRepository participationRepository, UserRepository userRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ParticipationService participationService, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository, ProgrammingExerciseSimulationService programmingExerciseSimulationService) {
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationService = participationService;
        this.programmingExerciseSimulationService = programmingExerciseSimulationService;
    }

    /**
     * This method creates a new participation for the provided user
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param programmingExercise the used programmingExercise
     * @param participant the participant object of the user
     * @param user the user who wants to participate
     * @return the newly created and stored participation
     */
    public ProgrammingExerciseStudentParticipation createParticipation(ProgrammingExercise programmingExercise, Participant participant, User user) {
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
        programmingExerciseStudentParticipation.setBuildPlanId(programmingExercise.getProjectKey() + "-" + user.getLogin().toUpperCase());
        programmingExerciseStudentParticipation.setParticipant(participant);
        programmingExerciseStudentParticipation.setInitializationState(InitializationState.INITIALIZED);
        programmingExerciseStudentParticipation.setRepositoryUrl("http://" + user.getLogin() + "@" + programmingExerciseSimulationService.domain
                + programmingExercise.getProjectKey() + "/" + programmingExercise.getProjectKey().toLowerCase() + "-" + user.getLogin() + ".git");
        programmingExerciseStudentParticipation.setInitializationDate(ZonedDateTime.now());
        programmingExerciseStudentParticipation.setProgrammingExercise(programmingExercise);
        participationRepository.save(programmingExerciseStudentParticipation);
        return programmingExerciseStudentParticipation;
    }

    /**
     * This method creates a new submission for the provided user
     * @param exerciseId the exerciseId of the exercise for which a submission should be created
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @return the newly created and stored submission
     */
    public ProgrammingSubmission createSubmission(Long exerciseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Participant participant = user;
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(exerciseId);
        Optional<StudentParticipation> optionalStudentParticipation = participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(programmingExercise,
                user.getLogin());
        if (optionalStudentParticipation.isEmpty()) {
            programmingExerciseStudentParticipation = createParticipation(programmingExercise, participant, user);
        }
        else {
            programmingExerciseStudentParticipation = (ProgrammingExerciseStudentParticipation) optionalStudentParticipation.get();
        }

        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setCommitHash(VCSSimulationUtils.simulateCommitHash());
        programmingSubmission.setSubmitted(true);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission.setType(SubmissionType.MANUAL);
        programmingExerciseStudentParticipation.addSubmission(programmingSubmission);

        programmingSubmissionRepository.save(programmingSubmission);
        return programmingSubmission;
    }

    /**
     *  This method creates a new result for the provided participation
     *  This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param programmingExerciseStudentParticipation the participation for which the new result should be created
     * @return the newly created and stored result
     */
    public Result createResult(ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation) {
        Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository
                .findFirstByParticipationIdOrderByLegalSubmissionDateDesc(programmingExerciseStudentParticipation.getId());
        Result result = new Result();
        result.setSubmission(programmingSubmission.get());
        result.setParticipation(programmingExerciseStudentParticipation);
        result.setRated(true);
        result.setScore(7.0 / 13.0);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        this.addFeedback(result);
        resultRepository.save(result);
        return result;
    }

    /**
     * Creates feedback for the provided result
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param result for which the feedback should be created
     * @param methodName of the testcase
     * @param positive is the testcase positive or not
     * @param errorMessageString will only be added if the test case fails otherwise use null
     */
    public void createFeedback(Result result, String methodName, boolean positive, @Nullable String errorMessageString) {
        Feedback feedback = new Feedback();
        feedback.setText(methodName);
        feedback.setDetailText(errorMessageString);
        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setPositive(positive);
        result.addFeedback(feedback);
    }

    /**
     * adds the feedback to the result
     * currently only the Java standard template is supported
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param result to which the feedback should be added
     */
    public void addFeedback(Result result) {
        this.createFeedback(result, "testClass[BubbleSort]", false,
                "The class 'BubbleSort' does not implement the interface 'SortStrategy' as expected. Implement the interface and its methods.");
        this.createFeedback(result, "testBubbleSort", false, "BubbleSort does not sort correctly");
        this.createFeedback(result, "testUseBubbleSortForSmallList", false, "The class 'Context' was not found within the submission. Make sure to implement it properly.");
        this.createFeedback(result, "testMergeSort", false, "MergeSort does not sort correctly");
        this.createFeedback(result, "testUseMergeSortForBigList", false, "The class 'Context' was not found within the submission. Make sure to implement it properly.");
        this.createFeedback(result, "testClass[MergeSort]", false,
                "The class 'MergeSort' does not implement the interface 'SortStrategy' as expected. Implement the interface and its methods.");
        this.createFeedback(result, "testClass[SortStrategy]", true, null);
        this.createFeedback(result, "testAttributes[Context]", true, null);
        this.createFeedback(result, "testMethods[Policy]", true, null);
        this.createFeedback(result, "testMethods[SortStrategy]", true, null);
        this.createFeedback(result, "testMethods[Context]", true, null);
        this.createFeedback(result, "testAttributes[Policy]", true, null);
        this.createFeedback(result, "testConstructors[Policy]", true, null);
    }

}
