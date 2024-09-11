package de.tum.cit.aet.artemis.web.rest.lecture;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ExerciseUnitResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseUnitResource.class);

    private static final String ENTITY_NAME = "exerciseUnit";

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseUnitRepository exerciseUnitRepository;

    private final LectureRepository lectureRepository;

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
    @PostMapping("lectures/{lectureId}/exercise-units")
    @EnforceAtLeastEditor
    public ResponseEntity<ExerciseUnit> createExerciseUnit(@PathVariable Long lectureId, @RequestBody ExerciseUnit exerciseUnit) throws URISyntaxException {
        log.debug("REST request to create ExerciseUnit : {}", exerciseUnit);
        if (exerciseUnit.getId() != null) {
            throw new BadRequestException();
        }
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.EDITOR, lecture, null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        exerciseUnit.setLecture(null);
        exerciseUnit = exerciseUnitRepository.saveAndFlush(exerciseUnit);
        exerciseUnit.setLecture(lecture);
        lecture.addLectureUnit(exerciseUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        ExerciseUnit persistedExerciseUnit = (ExerciseUnit) updatedLecture.getLectureUnits().getLast();

        return ResponseEntity.created(new URI("/api/exercise-units/" + persistedExerciseUnit.getId())).body(persistedExerciseUnit);
    }

    /**
     * GET /lectures/:lectureId/exercise-units : gets the exercise units associated with a lecture
     *
     * @param lectureId the id of the lecture to get the exercise-units for
     * @return the ResponseEntity with status 200 (OK) and with body the found exercise units
     */
    @GetMapping("lectures/{lectureId}/exercise-units")
    @EnforceAtLeastEditor
    public ResponseEntity<List<ExerciseUnit>> getAllExerciseUnitsOfLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get all exercise units for lecture : {}", lectureId);
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.EDITOR, lecture, null);
        List<ExerciseUnit> exerciseUnitsOfLecture = exerciseUnitRepository.findByLectureId(lectureId);
        return ResponseEntity.ok().body(exerciseUnitsOfLecture);
    }
}
