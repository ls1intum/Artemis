package de.tum.cit.aet.artemis.lecture.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastEditorInLecture;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.dto.ExerciseUnitDTO;
import de.tum.cit.aet.artemis.lecture.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

@Conditional(LectureEnabled.class)
@Lazy
@RestController
@RequestMapping("api/lecture/")
public class ExerciseUnitResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseUnitResource.class);

    private static final String ENTITY_NAME = "exerciseUnit";

    private final ExerciseUnitRepository exerciseUnitRepository;

    private final LectureRepository lectureRepository;

    private final ExerciseRepository exerciseRepository;

    public ExerciseUnitResource(LectureRepository lectureRepository, ExerciseUnitRepository exerciseUnitRepository, ExerciseRepository exerciseRepository) {
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.lectureRepository = lectureRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * POST /lectures/:lectureId/exercise-units : creates a new exercise unit.
     *
     * @param lectureId       the id of the lecture to which the attachment video unit should be added
     * @param exerciseUnitDto the exercise unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new exercise unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("lectures/{lectureId}/exercise-units")
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<ExerciseUnitDTO> createExerciseUnit(@PathVariable Long lectureId, @RequestBody ExerciseUnitDTO exerciseUnitDto) throws URISyntaxException {
        log.debug("REST request to create ExerciseUnit : {}", exerciseUnitDto);
        if (exerciseUnitDto.id() != null) {
            throw new BadRequestAlertException("A new exercise unit cannot have an id", ENTITY_NAME, "idExists");
        }
        if (exerciseUnitDto.exercise() == null || exerciseUnitDto.exercise().id() == null) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseUnitDto.exercise().id());
        if (lecture.getCourse() == null || exercise.getCourseViaExerciseGroupOrCourseMember() == null) {
            throw new BadRequestAlertException("Input data not valid", ENTITY_NAME, "inputInvalid");
        }
        if (!Objects.equals(lecture.getCourse().getId(), exercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new BadRequestAlertException("The exercise must belong to the same course as the lecture", ENTITY_NAME, "courseMismatch");
        }

        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        // addLectureUnit sets the lecture back-reference and the lecture unit order on the new unit.
        lecture.addLectureUnit(exerciseUnit);
        // Persist the new unit directly. Saving via the lecture would trigger a JPA merge that returns a managed copy while
        // leaving this transient reference id-less, so the subsequent save below would insert a second, duplicate unit.
        ExerciseUnit persistedUnit = exerciseUnitRepository.saveAndFlush(exerciseUnit);

        return ResponseEntity.created(new URI("/api/exercise-units/" + persistedUnit.getId())).body(ExerciseUnitDTO.of(persistedUnit));
    }

    /**
     * GET /lectures/:lectureId/exercise-units : gets the exercise units associated with a lecture
     *
     * @param lectureId the id of the lecture to get the exercise-units for
     * @return the ResponseEntity with status 200 (OK) and with body the found exercise units
     */
    @GetMapping("lectures/{lectureId}/exercise-units")
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<List<ExerciseUnitDTO>> getAllExerciseUnitsOfLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get all exercise units for lecture : {}", lectureId);
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }
        List<ExerciseUnitDTO> exerciseUnitsOfLecture = exerciseUnitRepository.findByLectureId(lectureId).stream().map(ExerciseUnitDTO::of).toList();
        return ResponseEntity.ok().body(exerciseUnitsOfLecture);
    }
}
