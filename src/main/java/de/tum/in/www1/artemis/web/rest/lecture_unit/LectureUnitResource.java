package de.tum.in.www1.artemis.web.rest.lecture_unit;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

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
import de.tum.in.www1.artemis.domain.lecture_unit.LectureUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.lecture_unit.LectureUnitRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.lecture_unit.LectureUnitService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class LectureUnitResource {

    private final Logger log = LoggerFactory.getLogger(LectureUnitResource.class);

    private static final String ENTITY_NAME = "lectureUnit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final AuthorizationCheckService authorizationCheckService;

    private final LectureUnitService lectureUnitService;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    public LectureUnitResource(UserService userService, AuthorizationCheckService authorizationCheckService, LectureUnitService lectureUnitService,
            LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository) {
        this.userService = userService;
        this.authorizationCheckService = authorizationCheckService;
        this.lectureUnitService = lectureUnitService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
    }

    @PutMapping("/lectures/{lectureId}/lecture-units-order")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<List<LectureUnit>> updateLectureUnitsOrder(@PathVariable Long lectureId, @RequestBody List<LectureUnit> orderedLectureUnits) {
        log.debug("REST request to update the order of lecture units of lecture: {}", lectureId);
        List<LectureUnit> persistedOrderedLectureUnits = lectureUnitService.updateLectureUnitsOrder(lectureId, orderedLectureUnits);
        return ResponseEntity.ok(persistedOrderedLectureUnits);
    }

    @DeleteMapping("/lectures/{lectureId}/lecture-units/{lectureUnitId}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<Void> deleteLectureUnit(@PathVariable Long lectureId, @PathVariable Long lectureUnitId) {
        log.info("REST request to delete lecture unit: {}", lectureUnitId);
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureId);
        if (lectureOptional.isEmpty()) {
            return notFound();
        }
        Lecture lecture = lectureOptional.get();
        Optional<LectureUnit> lectureUnitOptional = lectureUnitRepository.findById(lectureUnitId);
        if (lectureUnitOptional.isEmpty()) {
            return notFound();
        }
        LectureUnit lectureUnit = lectureUnitOptional.get();

        // Remove the lecture unit by removing it from the list of lecture units of the corresponding lecture
        List<LectureUnit> filteredLectureUnits = lecture.getLectureUnits();
        filteredLectureUnits.removeIf(lu -> lu.getId().equals(lectureUnitId));
        lecture.setLectureUnits(filteredLectureUnits);
        lectureRepository.save(lecture);

        String title;

        if (lectureUnit instanceof ExerciseUnit) {
            title = ((ExerciseUnit) lectureUnit).getExercise().getTitle();
        }
        else {
            title = lectureUnit.getName();
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, title)).build();
    }

}
