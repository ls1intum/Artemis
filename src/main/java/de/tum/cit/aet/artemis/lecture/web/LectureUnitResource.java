package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
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

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastInstructorInLectureUnit;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastStudentInLectureUnit;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitForLearningPathNodeDetailsDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/lecture/")
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

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    public LectureUnitResource(AuthorizationCheckService authorizationCheckService, UserRepository userRepository, LectureRepository lectureRepository,
            LectureUnitRepository lectureUnitRepository, LectureUnitService lectureUnitService, Optional<CompetencyProgressApi> competencyProgressApi) {
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitService = lectureUnitService;
        this.competencyProgressApi = competencyProgressApi;
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
        if (!lectureUnits.stream().map(LectureUnit::getId).collect(Collectors.toSet()).containsAll(orderedLectureUnitIds)) {
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
    @EnforceAtLeastStudentInLectureUnit
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

        lectureUnitService.setLectureUnitCompletion(lectureUnit, user, completed);
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectForParticipantAsync(lectureUnit, user));

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
    @EnforceAtLeastInstructorInLectureUnit
    public ResponseEntity<Void> deleteLectureUnit(@PathVariable long lectureUnitId, @PathVariable Long lectureId) {
        log.info("REST request to delete lecture unit: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findByIdWithCompetenciesBidirectionalElseThrow(lectureUnitId);
        if (lectureUnit.getLecture() == null || lectureUnit.getLecture().getCourse() == null) {
            throw new BadRequestAlertException("Lecture unit must be associated to a lecture of a course", ENTITY_NAME, "lectureOrCourseMissing");
        }
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new BadRequestAlertException("Requested lecture unit is not part of the specified lecture", ENTITY_NAME, "lectureIdMismatch");
        }

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
    @EnforceAtLeastStudentInLectureUnit
    public ResponseEntity<LectureUnitForLearningPathNodeDetailsDTO> getLectureUnitForLearningPathNodeDetails(@PathVariable long lectureUnitId) {
        log.info("REST request to get lecture unit for learning path node details with id: {}", lectureUnitId);
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow();
        return ResponseEntity.ok(LectureUnitForLearningPathNodeDetailsDTO.of(lectureUnit));
    }

    /**
     * GET /lecture-units/:lectureUnitId : get the lecture unit with the given id.
     *
     * @param lectureUnitId the id of the lecture unit that should be fetched
     * @return the ResponseEntity with status 200 (OK) and the lecture unit in the body, or with status 404 (Not Found) if the lecture unit could not be found
     */
    @GetMapping("lecture-units/{lectureUnitId}")
    @EnforceAtLeastStudentInLectureUnit
    public ResponseEntity<LectureUnit> getLectureUnitById(@PathVariable @Valid long lectureUnitId) {
        log.debug("REST request to get lecture unit with id: {}", lectureUnitId);
        var lectureUnit = lectureUnitRepository.findByIdWithCompletedUsersElseThrow(lectureUnitId);
        lectureUnit.setCompleted(lectureUnit.isCompletedFor(userRepository.getUser()));
        return ResponseEntity.ok(lectureUnit);
    }

    /**
     * This endpoint triggers the ingestion process for a specified lecture unit into Pyris.
     *
     * @param lectureId     the ID of the lecture to which the lecture unit belongs
     * @param lectureUnitId the ID of the lecture unit to be ingested
     * @return ResponseEntity<Void> with the status of the ingestion operation.
     *         Returns 200 OK if the ingestion is successfully started.
     *         Returns 400 BAD_REQUEST if the lecture unit cannot be ingested.
     *         Returns SERVICE_UNAVAILABLE if the Pyris service is unavailable or
     *         ingestion fails for another reason.
     */
    @PostMapping("lectures/{lectureId}/lecture-units/{lectureUnitId}/ingest")
    @EnforceAtLeastInstructorInLectureUnit
    public ResponseEntity<Void> ingestLectureUnit(@PathVariable long lectureId, @PathVariable long lectureUnitId) {
        Lecture lecture = this.lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        Optional<LectureUnit> lectureUnitOptional = lecture.getLectureUnits().stream().filter(lu -> lu.getId() == lectureUnitId).findFirst();
        if (lectureUnitOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        LectureUnit lectureUnit = lectureUnitOptional.get();
        return lectureUnitService.ingestLectureUnitInPyris(lectureUnit);
    }
}
