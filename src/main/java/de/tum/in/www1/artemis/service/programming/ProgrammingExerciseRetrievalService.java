package de.tum.in.www1.artemis.service.programming;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.user.UserRetrievalService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseRetrievalService {

    private final UserRetrievalService userRetrievalService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ProgrammingExerciseRetrievalService(ProgrammingExerciseRepository programmingExerciseRepository, UserRetrievalService userRetrievalService,
            AuthorizationCheckService authCheckService) {
        this.userRetrievalService = userRetrievalService;
        this.authCheckService = authCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Returns the list of programming exercises with a buildAndTestStudentSubmissionsAfterDueDate in future.
     *
     * @return List<ProgrammingExercise>
     */
    public List<ProgrammingExercise> findAllWithBuildAndTestAfterDueDateInFuture() {
        return programmingExerciseRepository.findAllByBuildAndTestStudentSubmissionsAfterDueDateAfterDate(ZonedDateTime.now());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the template Participation
     *
     * @param participation The template participation
     * @return The ProgrammingExercise where the given Participation is the template Participation
     */
    public ProgrammingExercise getExercise(TemplateProgrammingExerciseParticipation participation) {
        return programmingExerciseRepository.findOneByTemplateParticipationId(participation.getId());
    }

    /**
     * Find the ProgrammingExercise where the given Participation is the solution Participation
     *
     * @param participation The solution participation
     * @return The ProgrammingExercise where the given Participation is the solution Participation
     */
    public ProgrammingExercise getExercise(SolutionProgrammingExerciseParticipation participation) {
        return programmingExerciseRepository.findOneBySolutionParticipationId(participation.getId());
    }

    /**
     * Find a programming exercise by its id.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     */
    public ProgrammingExercise findById(Long programmingExerciseId) {
        return programmingExerciseRepository.findById(programmingExerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming exercise not found with id " + programmingExerciseId));
    }

    /**
     * Find a programming exercise by its id, including template and solution but without results.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findWithTemplateParticipationAndSolutionParticipationById(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
        }
    }

    /**
     * Find a programming exercise by its id, including template and solution participation and their latest results.
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findWithTemplateAndSolutionParticipationWithResultsById(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationLatestResultById(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found with id " + programmingExerciseId);
        }
    }

    /**
     * Find a programming exercise by its id, with eagerly loaded studentParticipations and submissions
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    public ProgrammingExercise findByIdWithEagerStudentParticipationsAndSubmissions(long programmingExerciseId) throws EntityNotFoundException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(programmingExerciseId);
        if (programmingExercise.isPresent()) {
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found");
        }
    }

    /**
     * Find a programming exercise by its exerciseId, including all test cases, also perform security checks
     *
     * @param exerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     * @throws IllegalAccessException  the retriever does not have the permissions to fetch information related to the programming exercise.
     */
    public ProgrammingExercise findWithTestCasesById(Long exerciseId) throws EntityNotFoundException, IllegalAccessException {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTestCasesById(exerciseId);
        if (programmingExercise.isPresent()) {
            Course course = programmingExercise.get().getCourseViaExerciseGroupOrCourseMember();
            User user = userRetrievalService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                throw new IllegalAccessException();
            }
            return programmingExercise.get();
        }
        else {
            throw new EntityNotFoundException("programming exercise not found");
        }
    }

    /**
     * Check if the repository of the given participation is locked.
     * This is the case when the participation is a ProgrammingExerciseStudentParticipation, the buildAndTestAfterDueDate of the exercise is set and the due date has passed,
     * or if manual correction is involved and the due date has passed.
     *
     * Locked means that the student can't make any changes to their repository anymore. While we can control this easily in the remote VCS, we need to check this manually for the local repository on the Artemis server.
     *
     * @param participation ProgrammingExerciseParticipation
     * @return true if repository is locked, false if not.
     */
    public boolean isParticipationRepositoryLocked(ProgrammingExerciseParticipation participation) {
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
            // Editing is allowed if build and test after due date is not set and no manual correction is involved
            // (this should match CodeEditorStudentContainerComponent.repositoryIsLocked on the client-side)
            boolean isEditingAfterDueAllowed = programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() == null
                    && programmingExercise.getAssessmentType() == AssessmentType.AUTOMATIC;
            return programmingExercise.getDueDate() != null && programmingExercise.getDueDate().isBefore(ZonedDateTime.now()) && !isEditingAfterDueAllowed;
        }
        return false;
    }
}
