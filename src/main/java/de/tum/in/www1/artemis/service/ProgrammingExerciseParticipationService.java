package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;

@Service
public class ProgrammingExerciseParticipationService {

    private ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private AuthorizationCheckService authCheckService;

    private UserService userService;

    public ProgrammingExerciseParticipationService(SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, TemplateProgrammingExerciseParticipationRepository templateParticipationRepository,
            UserService userService, AuthorizationCheckService authCheckService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.authCheckService = authCheckService;
        this.userService = userService;
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
        User user = userService.getUser();
        return participation.getStudent().getLogin().equals(user.getLogin());
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
