package de.tum.in.www1.artemis.web.rest.lecture;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AttachmentUnitService;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@RestController
@RequestMapping("api/")
public class AttachmentUnitResource {

    private final Logger log = LoggerFactory.getLogger(AttachmentUnitResource.class);

    private static final String ENTITY_NAME = "attachmentUnit";

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final GroupNotificationService groupNotificationService;

    private final AttachmentUnitService attachmentUnitService;

    public AttachmentUnitResource(AttachmentUnitRepository attachmentUnitRepository, LectureRepository lectureRepository, AuthorizationCheckService authorizationCheckService,
            GroupNotificationService groupNotificationService, AttachmentUnitService attachmentUnitService) {
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.groupNotificationService = groupNotificationService;
        this.attachmentUnitService = attachmentUnitService;
    }

    /**
     * GET lectures/:lectureId/attachment-units/:attachmentUnitId : gets the attachment unit with the specified id
     *
     * @param attachmentUnitId the id of the attachmentUnit to retrieve
     * @param lectureId        the id of the lecture to which the unit belongs
     * @return the ResponseEntity with status 200 (OK) and with body the attachment unit, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/attachment-units/{attachmentUnitId}")
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
     * PUT lectures/:lectureId/attachment-units/:attachmentUnitId : Updates an existing attachment unit
     *
     * @param lectureId        the id of the lecture to which the attachment unit belongs to update
     * @param attachmentUnitId the id of the attachment unit to update
     * @param attachmentUnit   the attachment unit with updated content
     * @param attachment       the attachment with updated content
     * @param file             the optional file to upload
     * @param keepFilename     specifies if the original filename should be kept or not
     * @param notificationText the text to be used for the notification. No notification will be sent if the parameter is not set
     * @return the ResponseEntity with status 200 (OK) and with body the updated attachmentUnit
     */
    @PutMapping(value = "lectures/{lectureId}/attachment-units/{attachmentUnitId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<AttachmentUnit> updateAttachmentUnit(@PathVariable Long lectureId, @PathVariable Long attachmentUnitId, @RequestPart AttachmentUnit attachmentUnit,
            @RequestPart Attachment attachment, @RequestPart(required = false) MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFilename,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update an attachment unit : {}", attachmentUnit);
        AttachmentUnit existingAttachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);
        if (existingAttachmentUnit.getLecture() == null || existingAttachmentUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "AttachmentUnit", "lectureOrCourseMissing");
        }
        if (!existingAttachmentUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "AttachmentUnit", "lectureIdMismatch");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, attachmentUnit.getLecture().getCourse(), null);

        AttachmentUnit savedAttachmentUnit = attachmentUnitService.updateAttachmentUnit(existingAttachmentUnit, attachmentUnit, attachment, file, keepFilename);

        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutAttachmentChange(savedAttachmentUnit.getAttachment(), notificationText);
        }

        return ResponseEntity.ok(savedAttachmentUnit);
    }

    /**
     * POST lectures/:lectureId/attachment-units : creates a new attachment unit.
     *
     * @param lectureId      the id of the lecture to which the attachment unit should be added
     * @param attachmentUnit the attachment unit that should be created
     * @param attachment     the attachment that should be created
     * @param file           the file to upload
     * @param keepFilename   specifies if the original filename should be kept or not
     * @return the ResponseEntity with status 201 (Created) and with body the new attachment unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "lectures/{lectureId}/attachment-units", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<AttachmentUnit> createAttachmentUnit(@PathVariable Long lectureId, @RequestPart AttachmentUnit attachmentUnit, @RequestPart Attachment attachment,
            @RequestPart MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFilename) throws URISyntaxException {
        log.debug("REST request to create AttachmentUnit {} with Attachment {}", attachmentUnit, attachment);
        if (attachmentUnit.getId() != null) {
            throw new BadRequestAlertException("A new attachment unit cannot already have an ID", ENTITY_NAME, "idexists");

        }
        if (attachment.getId() != null) {
            throw new BadRequestAlertException("A new attachment cannot already have an ID", ENTITY_NAME, "idexists");
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
        if (lecture.getCourse() == null) {
            throw new ConflictException("Specified lecture is not part of a course", "AttachmentUnit", "courseMissing");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        AttachmentUnit savedAttachmentUnit = attachmentUnitService.createAttachmentUnit(attachmentUnit, attachment, lecture, file, keepFilename);

        return ResponseEntity.created(new URI("/api/attachment-units/" + savedAttachmentUnit.getId())).body(savedAttachmentUnit);
    }
}
