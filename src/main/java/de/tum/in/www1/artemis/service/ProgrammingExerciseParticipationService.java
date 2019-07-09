package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;

@Service
public class ProgrammingExerciseParticipationService {

    private SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private AuthorizationCheckService authCheckService;

    private UserService userService;

    public ProgrammingExerciseParticipationService(SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository, UserService userService, AuthorizationCheckService authCheckService) {
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.authCheckService = authCheckService;
        this.userService = userService;
    }

    public boolean canAccessParticipation(ProgrammingExerciseParticipation participation) {
        if (participation instanceof SolutionProgrammingExerciseParticipation) {
            return canAccessParticipation((SolutionProgrammingExerciseParticipation) participation);
        }
        else if (participation instanceof TemplateProgrammingExerciseParticipation) {
            return canAccessParticipation((TemplateProgrammingExerciseParticipation) participation);
        }
        return false;
    }

    public boolean canAccessParticipation(SolutionProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

    public boolean canAccessParticipation(TemplateProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }
}
