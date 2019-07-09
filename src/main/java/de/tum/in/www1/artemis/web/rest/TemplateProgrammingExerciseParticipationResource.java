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

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;

@RestController
@RequestMapping("/api")
public class TemplateProgrammingExerciseParticipationResource {

    private final Logger log = LoggerFactory.getLogger(TemplateProgrammingExerciseParticipationResource.class);

    private TemplateProgrammingExerciseParticipationRepository participationRepository;

    private ResultRepository resultRepository;

    private AuthorizationCheckService authCheckService;

    private UserService userService;

    public TemplateProgrammingExerciseParticipationResource(TemplateProgrammingExerciseParticipationRepository participationRepository, ResultRepository resultRepository,
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
    @GetMapping(value = "/programming-exercises-template-participation/{participationId}/latest-result-with-feedbacks")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getLatestResultWithFeedbacks(@PathVariable Long participationId) {
        Optional<TemplateProgrammingExerciseParticipation> participation = participationRepository.findById(participationId);
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
        result.get().setParticipation(null);
        return ResponseEntity.ok(result.get());
    }

    private boolean canAccessParticipation(TemplateProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

}
