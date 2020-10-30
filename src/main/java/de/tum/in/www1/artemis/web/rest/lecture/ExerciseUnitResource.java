package de.tum.in.www1.artemis.web.rest.lecture_unit;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture_unit.ExerciseUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.lecture_unit.ExerciseUnitRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class ExerciseUnitResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseUnitResource.class);

    private static final String ENTITY_NAME = "exerciseUnit";

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseUnitRepository exerciseUnitRepository;

    private final LectureRepository lectureRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public ExerciseUnitResource(LectureRepository lectureRepository, ExerciseUnitRepository exerciseUnitRepository, AuthorizationCheckService authorizationCheckService) {
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * POST /lectures/:lectureId/exercise-units : creates a new exercise unit.
     *
     * @param lectureId    the id of the lecture to which the attachment unit should be added
     * @param exerciseUnit the exercise unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new exercise unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/lectures/{lectureId}/exercise-units")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ExerciseUnit> createExerciseUnit(@PathVariable Long lectureId, @RequestBody ExerciseUnit exerciseUnit) throws URISyntaxException {
        log.debug("REST request to create ExerciseUnit : {}", exerciseUnit);
        if (exerciseUnit.getId() != null) {
            return badRequest();
        }
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureId);
        if (lectureOptional.isEmpty()) {
            return badRequest();
        }
        Lecture lecture = lectureOptional.get();
        if (lecture.getCourse() == null) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(lecture.getCourse(), null)) {
            return forbidden();
        }

        lecture.addLectureUnit(exerciseUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        ExerciseUnit persistedExerciseUnit = (ExerciseUnit) updatedLecture.getLectureUnits().get(updatedLecture.getLectureUnits().size() - 1);

        return ResponseEntity.created(new URI("/api/exercise-units/" + persistedExerciseUnit.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(persistedExerciseUnit);

    }

    /**
     * GET /lectures/:lectureId/exercise-units : gets the exercise units associated with an lecture
     *
     * @param lectureId the id of the lecture to get the exercise-units for
     * @return the ResponseEntity with status 200 (OK) and with body the found exercise units
     */
    @GetMapping("/lectures/{lectureId}/exercise-units")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<List<ExerciseUnit>> getAllExerciseUnitsOfLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get all exercise units for lecture : {}", lectureId);
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureId);
        if (lectureOptional.isEmpty()) {
            return badRequest();
        }
        Lecture lecture = lectureOptional.get();
        if (lecture.getCourse() == null) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(lecture.getCourse(), null)) {
            return forbidden();
        }

        List<ExerciseUnit> exerciseUnitsOfLecture = exerciseUnitRepository.findByLectureId(lectureId);

        return ResponseEntity.ok().body(exerciseUnitsOfLecture);

    }

}
