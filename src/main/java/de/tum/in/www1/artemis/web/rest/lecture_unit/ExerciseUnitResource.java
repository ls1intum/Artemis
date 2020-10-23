package de.tum.in.www1.artemis.web.rest.lecture_unit;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

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

import de.tum.in.www1.artemis.domain.lecture_unit.ExerciseUnit;
import de.tum.in.www1.artemis.repository.lecture_unit.ExerciseUnitRepository;
import de.tum.in.www1.artemis.service.lecture_unit.ExerciseUnitService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class ExerciseUnitResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseUnitResource.class);

    private static final String ENTITY_NAME = "exerciseUnit";

    private final ExerciseUnitService exerciseUnitService;

    private final ExerciseUnitRepository exerciseUnitRepository;

    public ExerciseUnitResource(ExerciseUnitService exerciseUnitService, ExerciseUnitRepository exerciseUnitRepository) {
        this.exerciseUnitService = exerciseUnitService;
        this.exerciseUnitRepository = exerciseUnitRepository;
    }

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    @GetMapping("/exercise-units/{exerciseUnitId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ExerciseUnit> getExerciseUnit(@PathVariable Long exerciseUnitId) {
        log.debug("REST request to get ExerciseUnit : {}", exerciseUnitId);
        Optional<ExerciseUnit> optionalExerciseUnit = exerciseUnitService.findById(exerciseUnitId);
        if (optionalExerciseUnit.isEmpty()) {
            return notFound();
        }
        ExerciseUnit exerciseUnit = optionalExerciseUnit.get();
        return ResponseEntity.ok().body(exerciseUnit);
    }

    @PostMapping("/lectures/{lectureId}/exercise-units")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<ExerciseUnit> createExerciseUnit(@PathVariable Long lectureId, @RequestBody ExerciseUnit exerciseUnit) throws URISyntaxException {

        log.debug("REST request to create ExerciseUnit : {}", exerciseUnit);
        if (exerciseUnit.getId() != null) {
            throw new BadRequestAlertException("A new ExerciseUnit cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ExerciseUnit persistedExerciseUnit = exerciseUnitService.createExerciseUnit(lectureId, exerciseUnit);
        return ResponseEntity.created(new URI("/api/exercise-units/" + persistedExerciseUnit.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, persistedExerciseUnit.getId().toString())).body(persistedExerciseUnit);

    }

    @GetMapping("/lectures/{lectureId}/exercise-units")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<List<ExerciseUnit>> getExerciseUnitsByLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get all exercise units for lecture : {}", lectureId);

        List<ExerciseUnit> exerciseUnitsOfLecture = exerciseUnitRepository.findByLectureId(lectureId);

        return ResponseEntity.ok().body(exerciseUnitsOfLecture);

    }

}
