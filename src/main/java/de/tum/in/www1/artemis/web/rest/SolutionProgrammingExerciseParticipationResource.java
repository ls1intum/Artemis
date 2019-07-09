package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;

@RestController
@RequestMapping("/api")
public class SolutionProgrammingExerciseParticipationResource {

    private final Logger log = LoggerFactory.getLogger(SolutionProgrammingExerciseParticipationResource.class);

    private SolutionProgrammingExerciseParticipationRepository participationRepository;

    private ResultRepository resultRepository;

    private AuthorizationCheckService authCheckService;

    private UserService userService;

    public SolutionProgrammingExerciseParticipationResource(SolutionProgrammingExerciseParticipationRepository participationRepository, ResultRepository resultRepository,
            UserService userService, AuthorizationCheckService authCheckService) {
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
        this.authCheckService = authCheckService;
        this.userService = userService;
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(value = "/programming-exercises-solution-participation/{participationId}/latest-result-with-feedbacks")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getLatestResultWithFeedbacks(@PathVariable Long participationId) {
        Optional<SolutionProgrammingExerciseParticipation> participation = participationRepository.findById(participationId);
        if (!participation.isPresent()) {
            return notFound();
        }
        if (!canAccessParticipation(participation.get())) {
            return forbidden();
        }
        Optional<Result> result = resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.get().getId());
        if (!result.isPresent()) {
            return notFound();
        }
        // avoid circular serialization issue.
        result.get().setParticipation(null);
        return ResponseEntity.ok(result.get());
    }

    private boolean canAccessParticipation(SolutionProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

}
