package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseParticipationService {

    private ParticipationService participationService;

    private ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private AuthorizationCheckService authCheckService;

    private UserService userService;

    public ProgrammingExerciseParticipationService(ParticipationService participationService, SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, TemplateProgrammingExerciseParticipationRepository templateParticipationRepository,
            UserService userService, AuthorizationCheckService authCheckService) {
        this.participationService = participationService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.authCheckService = authCheckService;
        this.userService = userService;
    }

    public Participation findParticipation(Long participationId) throws EntityNotFoundException {
        return participationService.findOne(participationId);
    }

    public Optional<ProgrammingExerciseStudentParticipation> findStudentParticipation(Long participationId) {
        return studentParticipationRepository.findById(participationId);
    }

    public Optional<TemplateProgrammingExerciseParticipation> findTemplateParticipation(Long participationId) {
        return templateParticipationRepository.findById(participationId);
    }

    public Optional<SolutionProgrammingExerciseParticipation> findSolutionParticipation(Long participationId) {
        return solutionParticipationRepository.findById(participationId);
    }

    /**
     * Tries to retrieve a student participation for the given exercise id and username.
     *
     * @param exerciseId id of the exercise.
     * @param username of the user to which the participation belongs.
     * @return the participation for the given exercise and user.
     * @throws EntityNotFoundException if there is no participation for the given exercise and user.
     */
    public ProgrammingExerciseStudentParticipation findStudentParticipationByExerciseIdAndStudentId(Long exerciseId, String username) throws EntityNotFoundException {
        Optional<ProgrammingExerciseStudentParticipation> participation;
        participation = studentParticipationRepository.findByExerciseIdAndStudentLogin(exerciseId, username);
        if (!participation.isPresent())
            throw new EntityNotFoundException("participation could not be found by exerciseId " + exerciseId + " and user " + username);
        return participation.get();
    }

    public List<ProgrammingExerciseStudentParticipation> findByExerciseId(Long exerciseId) {
        return studentParticipationRepository.findByExerciseId(exerciseId);
    }

    public List<ProgrammingExerciseStudentParticipation> findByExerciseAndParticipationIds(Long exerciseId, List<Long> participationIds) {
        return studentParticipationRepository.findByExerciseIdAndParticipationIds(exerciseId, participationIds);
    }

    public Optional<ProgrammingExerciseStudentParticipation> findStudentParticipationWithLatestResultAndFeedbacks(Long participationId) {
        return studentParticipationRepository.findByIdWithLatestResultAndFeedbacks(participationId);
    }

    /**
     * Check if the user can access a given participation.
     * The method will treat the participation types differently:
     * - ProgrammingExerciseStudentParticipations should only be accessible by its owner (student) and the courses instructor/tas.
     * - Template/SolutionParticipations should only be accessible by the courses instructor/tas.
     *
     * @param participation to check permissions for.
     * @return true if the user can access the participation, false if not. Also returns false if the participation is not from a programming exercise.
     */
    public boolean canAccessParticipation(ProgrammingExerciseParticipation participation) {
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            return canAccessParticipation((ProgrammingExerciseStudentParticipation) participation);
        }
        else if (participation instanceof SolutionProgrammingExerciseParticipation) {
            return canAccessParticipation((SolutionProgrammingExerciseParticipation) participation);
        }
        else if (participation instanceof TemplateProgrammingExerciseParticipation) {
            return canAccessParticipation((TemplateProgrammingExerciseParticipation) participation);
        }
        return false;
    }

    public boolean canAccessParticipation(ProgrammingExerciseStudentParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return participation.getStudent().getLogin().equals(user.getLogin()) || authCheckService.isAtLeastTeachingAssistantInCourse(participation.getExercise().getCourse(), user);
    }

    public boolean canAccessParticipation(SolutionProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getProgrammingExercise(), user);
    }

    public boolean canAccessParticipation(TemplateProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getProgrammingExercise(), user);
    }

    /**
     * Check if the user can access a given participation.
     * The method will treat the participation types differently:
     * - ProgrammingExerciseStudentParticipations should only be accessible by its owner (student) and the courses instructor/tas.
     * - Template/SolutionParticipations should only be accessible by the courses instructor/tas.
     *
     * @param participation to check permissions for.
     * @param principal object to check permissions of the user with.
     * @return true if the user can access the participation, false if not. Also returns false if the participation is not from a programming exercise.
     */
    public boolean canAccessParticipation(ProgrammingExerciseParticipation participation, Principal principal) {
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            return canAccessParticipation((ProgrammingExerciseStudentParticipation) participation, principal);
        }
        else if (participation instanceof SolutionProgrammingExerciseParticipation) {
            return canAccessParticipation((SolutionProgrammingExerciseParticipation) participation, principal);
        }
        else if (participation instanceof TemplateProgrammingExerciseParticipation) {
            return canAccessParticipation((TemplateProgrammingExerciseParticipation) participation, principal);
        }
        return false;
    }

    public boolean canAccessParticipation(ProgrammingExerciseStudentParticipation participation, Principal principal) {
        return participation.getStudent().getLogin().equals(principal.getName());
    }

    public boolean canAccessParticipation(SolutionProgrammingExerciseParticipation participation, Principal principal) {
        User user = userService.getUserWithGroupsAndAuthorities(principal);
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

    public boolean canAccessParticipation(TemplateProgrammingExerciseParticipation participation, Principal principal) {
        User user = userService.getUserWithGroupsAndAuthorities(principal);
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }
}
