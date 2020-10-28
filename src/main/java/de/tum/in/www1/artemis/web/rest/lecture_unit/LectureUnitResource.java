package de.tum.in.www1.artemis.web.rest.lecture_unit;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture_unit.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture_unit.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture_unit.LectureUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.lecture_unit.LectureUnitRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class LectureUnitResource {

    private final Logger log = LoggerFactory.getLogger(LectureUnitResource.class);

    private static final String ENTITY_NAME = "lectureUnit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuthorizationCheckService authorizationCheckService;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    public LectureUnitResource(AuthorizationCheckService authorizationCheckService, LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository) {
        this.authorizationCheckService = authorizationCheckService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
    }

    /**
     * PUT /lectures/:lectureId/lecture-units-order
     *
     * @param lectureId           the id of the lecture for which to update the lecture unit order
     * @param orderedLectureUnits ordered lecture units
     * @return the ResponseEntity with status 200 (OK) and with body the ordered lecture units
     */
    @PutMapping("/lectures/{lectureId}/lecture-units-order")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<List<LectureUnit>> updateLectureUnitsOrder(@PathVariable Long lectureId, @RequestBody List<LectureUnit> orderedLectureUnits) {
        log.debug("REST request to update the order of lecture units of lecture: {}", lectureId);
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureId);
        if (lectureOptional.isEmpty()) {
            return notFound();
        }
        Lecture lecture = lectureOptional.get();
        if (lecture.getCourse() == null) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(lecture.getCourse(), null)) {
            return forbidden();
        }

        // Ensure that exactly as many lecture units have been received as are currently related to the lecture
        if (orderedLectureUnits.size() != lecture.getLectureUnits().size()) {
            return conflict();
        }

        // Ensure that all received lecture units are already related to the lecture
        for (LectureUnit lectureUnit : orderedLectureUnits) {
            if (!lecture.getLectureUnits().contains(lectureUnit)) {
                return conflict();
            }
            // Set the lecture manually as it won't be included in orderedLectureUnits
            lectureUnit.setLecture(lecture);

            // keep bidirectional mapping between attachment unit and attachment
            if (lectureUnit instanceof AttachmentUnit) {
                ((AttachmentUnit) lectureUnit).getAttachment().setAttachmentUnit((AttachmentUnit) lectureUnit);
            }

        }

        lecture.setLectureUnits(orderedLectureUnits);
        Lecture persistedLecture = lectureRepository.save(lecture);
        return ResponseEntity.ok(persistedLecture.getLectureUnits());
    }

    /**
     * DELETE /lecture-units/:lectureUnitId
     *
     * @param lectureUnitId the id of the lecture unit to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/lecture-units/{lectureUnitId}")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<Void> deleteLectureUnit(@PathVariable Long lectureUnitId) {
        log.info("REST request to delete lecture unit: {}", lectureUnitId);
        Optional<LectureUnit> lectureUnitOptional = lectureUnitRepository.findById(lectureUnitId);
        if (lectureUnitOptional.isEmpty()) {
            return notFound();
        }
        LectureUnit lectureUnit = lectureUnitOptional.get();
        if (lectureUnit.getLecture() == null || lectureUnit.getLecture().getCourse() == null) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(lectureUnit.getLecture().getCourse(), null)) {
            return forbidden();
        }

        // we have to get the lecture from the db so that that the lecture units are included
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureUnit.getLecture().getId());
        if (lectureOptional.isEmpty()) {
            return notFound();
        }
        Lecture lecture = lectureOptional.get();

        List<LectureUnit> filteredLectureUnits = lecture.getLectureUnits();
        filteredLectureUnits.removeIf(lu -> lu.getId().equals(lectureUnitId));
        lecture.setLectureUnits(filteredLectureUnits);
        lectureRepository.save(lecture);

        String lectureUnitName;

        if (lectureUnit instanceof ExerciseUnit && ((ExerciseUnit) lectureUnit).getExercise() != null) {
            lectureUnitName = ((ExerciseUnit) lectureUnit).getExercise().getTitle();
        }
        else if (lectureUnit instanceof AttachmentUnit && ((AttachmentUnit) lectureUnit).getAttachment() != null) {
            lectureUnitName = ((AttachmentUnit) lectureUnit).getAttachment().getName();
        }
        else {
            lectureUnitName = lectureUnit.getName();
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureUnitName)).build();
    }

}
