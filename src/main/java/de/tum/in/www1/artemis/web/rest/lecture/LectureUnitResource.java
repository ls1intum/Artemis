package de.tum.in.www1.artemis.web.rest.lecture;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
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

    private final LectureUnitService lectureUnitService;

    public LectureUnitResource(AuthorizationCheckService authorizationCheckService, LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository,
            LectureUnitService lectureUnitService) {
        this.authorizationCheckService = authorizationCheckService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitService = lectureUnitService;
    }

    /**
     * PUT /lectures/:lectureId/lecture-units-order
     *
     * @param lectureId             the id of the lecture for which to update the lecture unit order
     * @param orderedLectureUnitIds ordered list of ids of lecture units
     * @return the ResponseEntity with status 200 (OK) and with body the ordered lecture units
     */
    @PutMapping("/lectures/{lectureId}/lecture-units-order")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<LectureUnit>> updateLectureUnitsOrder(@PathVariable Long lectureId, @RequestBody List<Long> orderedLectureUnitIds) {
        log.debug("REST request to update the order of lecture units of lecture: {}", lectureId);
        final Lecture lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lectureId).orElseThrow();

        if (lecture.getCourse() == null) {
            throw new ConflictException("Specified lecture is not part of a course", "LectureUnit", "courseMissing");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // Ensure that exactly as many lecture unit ids have been received as are currently related to the lecture
        if (orderedLectureUnitIds.size() != lecture.getLectureUnits().size()) {
            throw new ConflictException("Received wrong size of lecture unit ids", "LectureUnit", "lectureUnitsSizeMismatch");
        }

        // We can not use findAllById because this does not preserve the ordering of orderedLectureUnitIds
        List<LectureUnit> orderedLectureUnits = orderedLectureUnitIds.stream().map(id -> lectureUnitRepository.findById(id).orElseThrow()).toList();

        // Ensure that all received lecture units are already related to the lecture
        if (!orderedLectureUnits.stream().allMatch(lectureUnit -> lectureUnit.getLecture().getId().equals(lecture.getId()))) {
            throw new ConflictException("Received lecture unit is not part of the lecture", "LectureUnit", "lectureMismatch");
        }

        lecture.setLectureUnits(orderedLectureUnits);
        Lecture persistedLecture = lectureRepository.save(lecture);
        return ResponseEntity.ok(persistedLecture.getLectureUnits());
    }

    /**
     * DELETE lectures/:lectureId/lecture-units/:lectureUnitId
     *
     * @param lectureId     the id of the lecture to which the unit belongs
     * @param lectureUnitId the id of the lecture unit to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/lectures/{lectureId}/lecture-units/{lectureUnitId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteLectureUnit(@PathVariable Long lectureUnitId, @PathVariable Long lectureId) {
        log.info("REST request to delete lecture unit: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findByIdWithLearningGoalsBidirectionalElseThrow(lectureUnitId);
        if (lectureUnit.getLecture() == null || lectureUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "LectureUnit", "lectureOrCourseMissing");
        }
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "LectureUnit", "lectureIdMismatch");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, lectureUnit.getLecture().getCourse(), null);

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
        if (Objects.isNull(lectureUnitName)) {
            lectureUnitName = "lectureUnitWithoutName";
        }
        lectureUnitService.removeLectureUnit(lectureUnit);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureUnitName)).build();
    }

}
