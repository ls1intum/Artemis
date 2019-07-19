package de.tum.in.www1.artemis.service;

import java.security.Principal;
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

    public ProgrammingExerciseStudentParticipation findStudentParticipationByExerciseIdAndStudentId(Long exerciseId, String username) {
        Optional<ProgrammingExerciseStudentParticipation> participation;
        participation = studentParticipationRepository.findByExerciseIdAndStudentLogin(exerciseId, username);
        if (!participation.isPresent())
            throw new EntityNotFoundException("participation could not be found by exerciseId " + exerciseId + " and user " + username);
        return participation.get();
    }

    public Optional<ProgrammingExerciseStudentParticipation> findStudentParticipationWithLatestResultAndFeedbacks(Long participationId) {
        return studentParticipationRepository.findByIdWithLatestResultAndFeedbacks(participationId);
    }

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
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

    public boolean canAccessParticipation(TemplateProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

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
