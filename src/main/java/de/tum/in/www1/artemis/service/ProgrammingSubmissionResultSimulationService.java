package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.util.VCSSimulationUtils;

@Service
public class ProgrammingSubmissionResultSimulationService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ParticipationRepository participationRepository;

    private final UserService userService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ParticipationService participationService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    public ProgrammingSubmissionResultSimulationService(ParticipationRepository participationRepository, UserService userService,
            ProgrammingExerciseService programmingExerciseService, ParticipationService participationService, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ResultRepository resultRepository) {
        this.participationRepository = participationRepository;
        this.userService = userService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.programmingExerciseService = programmingExerciseService;
        this.participationService = participationService;
    }

    /**
     * This method creates a new participation for the provided user
     * @param programmingExercise the used programmingExercise
     * @param participant the participant object of the user
     * @param user the user who wants to particpate
     * @return the newly created and stored participation
     */
    public ProgrammingExerciseStudentParticipation createParticipation(ProgrammingExercise programmingExercise, Participant participant, User user) {
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
        programmingExerciseStudentParticipation.setBuildPlanId(programmingExercise.getProjectKey() + "-" + user.getLogin());
        programmingExerciseStudentParticipation.setParticipant(participant);
        programmingExerciseStudentParticipation.setInitializationState(InitializationState.INITIALIZED);
        programmingExerciseStudentParticipation.setRepositoryUrl("http://" + user.getLogin() + "@localhost7990/scm/" + programmingExercise.getProjectKey() + "/"
                + programmingExercise.getProjectKey().toLowerCase() + "-" + user.getLogin() + ".git");
        programmingExerciseStudentParticipation.setInitializationDate(ZonedDateTime.now());
        programmingExerciseStudentParticipation.setProgrammingExercise(programmingExercise);
        participationRepository.save(programmingExerciseStudentParticipation);
        return programmingExerciseStudentParticipation;
    }

    /**
     * This method creates a new submission for the provided user
     * @param exerciseID the exerciseId of the exercise for which a submission should be created
     * @return the newly created and stored submission
     */
    public ProgrammingSubmission createSubmission(Long exerciseID) {
        User user = userService.getUserWithGroupsAndAuthorities();
        Participant participant = user;
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;
        ProgrammingExercise programmingExercise = programmingExerciseService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseID);
        Optional<StudentParticipation> optionalStudentParticipation = participationService.findOneByExerciseAndParticipantAnyState(programmingExercise, participant);
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
        programmingExerciseStudentParticipation.addSubmissions(programmingSubmission);

        programmingSubmissionRepository.save(programmingSubmission);
        return programmingSubmission;
    }

    /**
     *  This method creates a new result for the provided participation
     * @param programmingExerciseStudentParticipation the participation for which the new result should be created
     * @return the newly created and stored result
     */
    public Result createResult(ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation) {
        Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository
                .findFirstByParticipationIdOrderBySubmissionDateDesc(programmingExerciseStudentParticipation.getId());
        Result result = new Result();
        result.setSubmission(programmingSubmission.get());
        result.setParticipation(programmingExerciseStudentParticipation);
        result.setRated(true);
        result.resultString("7 of 13 passed");
        result.score(54L);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result);
        return result;
    }
}
