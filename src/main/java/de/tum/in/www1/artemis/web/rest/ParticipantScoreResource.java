package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreDTO;

@RestController
@RequestMapping("/api")
public class ParticipantScoreResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(ParticipantScoreResource.class);

    private static final String ENTITY_NAME = "participantScore";

    private final ParticipantScoreRepository participantScoreRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public ParticipantScoreResource(ParticipantScoreRepository participantScoreRepository, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.participantScoreRepository = participantScoreRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET /courses/:courseId/participant-scores  gets the participant scores of the course
     *
     * @param courseId   the id of the course to which the learning goal belongs
     * @param pageable   pageable object to filter and
     * @param getUnpaged if set all participant scores of the course will be loaded
     * @return the ResponseEntity with status 200 (OK) and with the learning goal course performance in the body
     */
    @GetMapping("/courses/{courseId}/participant-scores")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ParticipantScoreDTO>> getParticipantScoresOfCourse(@PathVariable Long courseId, Pageable pageable,
            @RequestParam(value = "getUnpaged", required = false, defaultValue = "false") Boolean getUnpaged) {
        log.debug("REST request to get participant scores for course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        if (course == null) {
            return notFound();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }
        Set<Exercise> exercisesOfCourse = course.getExercises();
        if (getUnpaged) {
            pageable = Pageable.unpaged();
        }

        List<ParticipantScoreDTO> results = participantScoreRepository.findAllByExerciseIn(exercisesOfCourse, pageable).stream()
                .map(participantScore -> ParticipantScoreDTO.generateFromParticipantScore(participantScore)).collect(Collectors.toList());
        return ResponseEntity.ok().body(results);
    }

}
