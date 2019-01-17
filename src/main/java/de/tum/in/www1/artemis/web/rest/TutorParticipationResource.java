package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.rest.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing TutorParticipation.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasRole('ADMIN')")
public class TutorParticipationResource {

    private final Logger log = LoggerFactory.getLogger(TutorParticipationResource.class);
    private final TutorParticipationService tutorParticipationService;
    private final ExerciseService exerciseService;
    private final CourseService courseService;
    private final UserService userService;

    public TutorParticipationResource(TutorParticipationService tutorParticipationService,
                                      CourseService courseService,
                                      ExerciseService exerciseService,
                                      UserService userService) {
        this.tutorParticipationService = tutorParticipationService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.userService = userService;
    }

    /**
     * POST /exercises/:exerciseId/tutorParticipations : start the "id" exercise for the current user.
     *
     * @param exerciseId the id of the exercise for which to init a tutorParticipations
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/tutorParticipations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<TutorParticipation> initTutorParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        Course course = exercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return forbidden();
        }

        TutorParticipation existingTutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
        if (existingTutorParticipation != null && existingTutorParticipation.getStatus() != TutorParticipationStatus.NOT_PARTICIPATED) {
            // tutorParticipations already exists
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("tutorParticipations", "tutorParticipationAlreadyExists", "There is already a tutorParticipations for the given exercise and user.")).body(null);
        }
        TutorParticipation tutorParticipation = tutorParticipationService.createNewParticipation(exercise, user);
        return ResponseEntity.created(new URI("/api/exercises/"  + exerciseId + "tutorParticipations/" + tutorParticipation.getId()))
            .body(tutorParticipation);
    }
}
