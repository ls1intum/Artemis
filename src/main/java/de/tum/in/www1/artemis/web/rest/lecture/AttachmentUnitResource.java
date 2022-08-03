package de.tum.in.www1.artemis.web.rest.lecture;

import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@RestController
@RequestMapping("/api")
public class AttachmentUnitResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(AttachmentUnitResource.class);

    private static final String ENTITY_NAME = "attachmentUnit";

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public AttachmentUnitResource(AttachmentUnitRepository attachmentUnitRepository, LectureRepository lectureRepository, AuthorizationCheckService authorizationCheckService) {
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /lectures/:lectureId/attachment-units/:attachmentUnitId : gets the attachment unit with the specified id
     *
     * @param attachmentUnitId the id of the attachmentUnit to retrieve
     * @param lectureId the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the attachment unit, or with status 404 (Not Found)
     */
    @GetMapping("/lectures/{lectureId}/attachment-units/{attachmentUnitId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<AttachmentUnit> getAttachmentUnit(@PathVariable Long attachmentUnitId, @PathVariable Long lectureId) {
        log.debug("REST request to get AttachmentUnit : {}", attachmentUnitId);
        AttachmentUnit attachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);

        if (attachmentUnit.getLecture() == null || attachmentUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "AttachmentUnit", "lectureOrCourseMissing");
        }
        if (!attachmentUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "AttachmentUnit", "lectureIdMismatch");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, attachmentUnit.getLecture().getCourse(), null);
        return ResponseEntity.ok().body(attachmentUnit);
    }

    /**
     * PUT /lectures/:lectureId/attachment-units : Updates an existing attachment unit .
     *
     * @param lectureId      the id of the lecture to which the attachment unit belongs to update
     * @param attachmentUnit the attachment unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated attachmentUnit
     */
    @PutMapping("/lectures/{lectureId}/attachment-units")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<AttachmentUnit> updateAttachmentUnit(@PathVariable Long lectureId, @RequestBody AttachmentUnit attachmentUnit) {
        log.debug("REST request to update an attachment unit : {}", attachmentUnit);
        if (attachmentUnit.getId() == null) {
            throw new BadRequestException();
        }
        if (attachmentUnit.getLecture() == null || attachmentUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "AttachmentUnit", "lectureOrCourseMissing");
        }
        if (!attachmentUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "AttachmentUnit", "lectureIdMismatch");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, attachmentUnit.getLecture().getCourse(), null);

        // Make sure that the original references are preserved.
        AttachmentUnit originalAttachmentUnit = attachmentUnitRepository.findById(attachmentUnit.getId()).get();
        attachmentUnit.setAttachment(originalAttachmentUnit.getAttachment());

        AttachmentUnit result = attachmentUnitRepository.save(attachmentUnit);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /lectures/:lectureId/attachment-units : creates a new attachment unit.
     *
     * @param lectureId      the id of the lecture to which the attachment unit should be added
     * @param attachmentUnit the attachment unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new attachment unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/lectures/{lectureId}/attachment-units")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<AttachmentUnit> createAttachmentUnit(@PathVariable Long lectureId, @RequestBody AttachmentUnit attachmentUnit) throws URISyntaxException {
        log.debug("REST request to create AttachmentUnit : {}", attachmentUnit);
        if (attachmentUnit.getId() != null) {
            throw new BadRequestException();
        }
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new ConflictException("Specified lecture is not part of a course", "AttachmentUnit", "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // persist lecture unit before lecture to prevent "null index column for collection" error
        attachmentUnit.setLecture(null);
        attachmentUnit = attachmentUnitRepository.saveAndFlush(attachmentUnit);
        attachmentUnit.setLecture(lecture);
        lecture.addLectureUnit(attachmentUnit);
        lectureRepository.save(lecture);

        // cleanup before sending to client
        attachmentUnit.getLecture().setLectureUnits(null);
        attachmentUnit.getLecture().setAttachments(null);
        attachmentUnit.getLecture().setPosts(null);
        return ResponseEntity.created(new URI("/api/attachment-units/" + attachmentUnit.getId())).body(attachmentUnit);
    }
}
