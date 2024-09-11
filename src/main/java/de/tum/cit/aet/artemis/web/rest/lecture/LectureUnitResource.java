package de.tum.cit.aet.artemis.web.rest.lecture;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.user.UserService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.LectureUnitService;
import de.tum.cit.aet.artemis.web.rest.dto.lectureunit.LectureUnitForLearningPathNodeDetailsDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class LectureUnitResource {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitResource.class);

    private static final String ENTITY_NAME = "lectureUnit";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    private final LectureUnitService lectureUnitService;

    private final CompetencyProgressService competencyProgressService;

    public LectureUnitResource(AuthorizationCheckService authorizationCheckService, UserRepository userRepository, LectureRepository lectureRepository,
            LectureUnitRepository lectureUnitRepository, LectureUnitService lectureUnitService, CompetencyProgressService competencyProgressService, UserService userService) {
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitService = lectureUnitService;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * PUT /lectures/:lectureId/lecture-units-order
     *
     * @param lectureId             the id of the lecture for which to update the lecture unit order
     * @param orderedLectureUnitIds ordered list of ids of lecture units
     * @return the ResponseEntity with status 200 (OK) and with body the ordered lecture units
     */
    @PutMapping("lectures/{lectureId}/lecture-units-order")
    @EnforceAtLeastEditor
    public ResponseEntity<List<LectureUnit>> updateLectureUnitsOrder(@PathVariable Long lectureId, @RequestBody List<Long> orderedLectureUnitIds) {
        log.debug("REST request to update the order of lecture units of lecture: {}", lectureId);
        final Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);

        if (lecture.getCourse() == null) {
            throw new BadRequestAlertException("Specified lecture is not part of a course", ENTITY_NAME, "courseMissing");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        List<LectureUnit> lectureUnits = lecture.getLectureUnits();

        // Ensure that exactly as many lecture unit ids have been received as are currently related to the lecture
        if (orderedLectureUnitIds.size() != lectureUnits.size()) {
            throw new BadRequestAlertException("Received wrong size of lecture unit ids", ENTITY_NAME, "lectureUnitsSizeMismatch");
        }

        // Ensure that all received lecture unit ids are already part of the lecture
        if (!lectureUnits.stream().map(LectureUnit::getId).toList().containsAll(orderedLectureUnitIds)) {
            throw new BadRequestAlertException("Received lecture unit is not part of the lecture", ENTITY_NAME, "lectureMismatch");
        }

        lectureUnits.sort(Comparator.comparing(unit -> orderedLectureUnitIds.indexOf(unit.getId())));

        Lecture persistedLecture = lectureRepository.save(lecture);
        return ResponseEntity.ok(persistedLecture.getLectureUnits());
    }

    /**
     * POST lectures/:lectureId/lecture-units/:lectureUnitId/complete
     *
     * @param lectureId     the id of the lecture to which the unit belongs
     * @param lectureUnitId the id of the lecture unit to mark as completed for the logged-in user
     * @param completed     true if the lecture unit should be marked as completed, false for uncompleted
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("lectures/{lectureId}/lecture-units/{lectureUnitId}/completion")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> completeLectureUnit(@PathVariable Long lectureUnitId, @PathVariable Long lectureId, @RequestParam("completed") boolean completed) {
        log.info("REST request to mark lecture unit as completed: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow(() -> new EntityNotFoundException(ENTITY_NAME));

        if (lectureUnit.getLecture() == null || lectureUnit.getLecture().getCourse() == null) {
            throw new BadRequestAlertException("Lecture unit must be associated to a lecture of a course", ENTITY_NAME, "lectureOrCourseMissing");
        }

        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }

        if (!lectureUnit.isVisibleToStudents()) {
            throw new BadRequestAlertException("Requested lecture unit is not yet visible for students", ENTITY_NAME, "lectureUnitNotReleased");
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, lectureUnit.getLecture().getCourse(), user);

        lectureUnitService.setLectureUnitCompletion(lectureUnit, user, completed);
        competencyProgressService.updateProgressByLearningObjectForParticipantAsync(lectureUnit, user);

        return ResponseEntity.ok().build();
    }

    /**
     * DELETE lectures/:lectureId/lecture-units/:lectureUnitId
     *
     * @param lectureId     the id of the lecture to which the unit belongs
     * @param lectureUnitId the id of the lecture unit to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("lectures/{lectureId}/lecture-units/{lectureUnitId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteLectureUnit(@PathVariable Long lectureUnitId, @PathVariable Long lectureId) {
        log.info("REST request to delete lecture unit: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findByIdWithCompetenciesBidirectionalElseThrow(lectureUnitId);
        if (lectureUnit.getLecture() == null || lectureUnit.getLecture().getCourse() == null) {
            throw new BadRequestAlertException("Lecture unit must be associated to a lecture of a course", ENTITY_NAME, "lectureOrCourseMissing");
        }
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, lectureUnit.getLecture().getCourse(), null);

        String lectureUnitName = lectureUnit.getName();
        if (lectureUnitName == null) {
            lectureUnitName = "lectureUnitWithoutName";
        }
        lectureUnitService.removeLectureUnit(lectureUnit);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureUnitName)).build();
    }

    /**
     * GET /lecture-units/:lectureUnitId/for-learning-path-node-details : Gets lecture unit for the details view of a learning path node.
     *
     * @param lectureUnitId the id of the lecture unit that should be fetched
     * @return the ResponseEntity with status 200 (OK)
     */
    @GetMapping("lecture-units/{lectureUnitId}/for-learning-path-node-details")
    @EnforceAtLeastStudent
    public ResponseEntity<LectureUnitForLearningPathNodeDetailsDTO> getLectureUnitForLearningPathNodeDetails(@PathVariable long lectureUnitId) {
        log.info("REST request to get lecture unit for learning path node details with id: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, lectureUnit.getLecture().getCourse(), null);
        return ResponseEntity.ok(LectureUnitForLearningPathNodeDetailsDTO.of(lectureUnit));
    }

    /**
     * GET /lecture-units/:lectureUnitId : get the lecture unit with the given id.
     *
     * @param lectureUnitId the id of the lecture unit that should be fetched
     * @return the ResponseEntity with status 200 (OK) and the lecture unit in the body, or with status 404 (Not Found) if the lecture unit could not be found
     */
    @GetMapping("lecture-units/{lectureUnitId}")
    @EnforceAtLeastStudent
    public ResponseEntity<LectureUnit> getLectureUnitById(@PathVariable @Valid Long lectureUnitId) {
        log.debug("REST request to get lecture unit with id: {}", lectureUnitId);
        var lectureUnit = lectureUnitRepository.findByIdWithCompletedUsersElseThrow(lectureUnitId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, lectureUnit.getLecture().getCourse(), null);
        lectureUnit.setCompleted(lectureUnit.isCompletedFor(userRepository.getUser()));
        return ResponseEntity.ok(lectureUnit);
    }
}
