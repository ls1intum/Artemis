package de.tum.in.www1.artemis.web.rest.lecture;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@RestController
@RequestMapping("/api")
public class AttachmentUnitResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(AttachmentUnitResource.class);

    private static final String ENTITY_NAME = "attachmentUnit";

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final FileService fileService;

    private final CacheManager cacheManager;

    private final GroupNotificationService groupNotificationService;

    public AttachmentUnitResource(AttachmentUnitRepository attachmentUnitRepository, AttachmentRepository attachmentRepository, LectureRepository lectureRepository,
            AuthorizationCheckService authorizationCheckService, FileService fileService, CacheManager cacheManager, GroupNotificationService groupNotificationService) {
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.lectureRepository = lectureRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.fileService = fileService;
        this.cacheManager = cacheManager;
        this.groupNotificationService = groupNotificationService;
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
     * PUT /lectures/:lectureId/attachment-units/:attachmentUnitId : Updates an existing attachment unit .
     *
     * @param lectureId      the id of the lecture to which the attachment unit belongs to update
     * @param attachmentUnit the attachment unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated attachmentUnit
     */
    @PutMapping(value = "/lectures/{lectureId}/attachment-units/{attachmentUnitId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<AttachmentUnit> updateAttachmentUnit(@PathVariable Long lectureId, @PathVariable Long attachmentUnitId, @RequestPart AttachmentUnit attachmentUnit,
            @RequestPart Attachment attachment, @RequestPart(required = false) MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFilename,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update an attachment unit : {}", attachmentUnit);
        AttachmentUnit existingAttachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);
        if (existingAttachmentUnit.getLecture() == null || existingAttachmentUnit.getLecture().getCourse() == null) {
            throw new ConflictException("Lecture unit must be associated to a lecture of a course", "AttachmentUnit", "lectureOrCourseMissing");
        }
        if (!existingAttachmentUnit.getLecture().getId().equals(lectureId)) {
            throw new ConflictException("Requested lecture unit is not part of the specified lecture", "AttachmentUnit", "lectureIdMismatch");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, attachmentUnit.getLecture().getCourse(), null);

        existingAttachmentUnit.setDescription(attachmentUnit.getDescription());
        existingAttachmentUnit.setName(attachmentUnit.getName());
        existingAttachmentUnit.setReleaseDate(attachmentUnit.getReleaseDate());

        AttachmentUnit savedAttachmentUnit = attachmentUnitRepository.saveAndFlush(existingAttachmentUnit);

        Attachment existingAttachment = existingAttachmentUnit.getAttachment();
        if (existingAttachment == null) {
            throw new ConflictException("Attachment unit must be associated to an attachment", "AttachmentUnit", "attachmentMissing");
        }

        // Make sure that the original references are preserved.
        existingAttachment.setAttachmentUnit(savedAttachmentUnit);
        existingAttachment.setReleaseDate(attachment.getReleaseDate());
        existingAttachment.setName(attachment.getName());
        existingAttachment.setReleaseDate(attachment.getReleaseDate());
        existingAttachment.setAttachmentType(attachment.getAttachmentType());

        if (file != null && !file.isEmpty()) {
            String filePath = fileService.handleSaveFile(file, keepFilename, false);
            existingAttachment.setLink(filePath);
            existingAttachment.setVersion(existingAttachment.getVersion() + 1);
            existingAttachment.setUploadDate(ZonedDateTime.now());
        }

        Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
        this.cacheManager.getCache("files").evict(fileService.actualPathForPublicPath(savedAttachment.getLink()));
        savedAttachmentUnit.setAttachment(savedAttachment);

        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutAttachmentChange(savedAttachment, notificationText);
        }

        return ResponseEntity.ok(savedAttachmentUnit);
    }

    /**
     * POST /lectures/:lectureId/attachment-units : creates a new attachment unit.
     *
     * @param lectureId      the id of the lecture to which the attachment unit should be added
     * @param attachmentUnit the attachment unit that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new attachment unit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "/lectures/{lectureId}/attachment-units", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

        // persist lecture unit before lecture to prevent "null index column for collection" error
        attachmentUnit.setLecture(null);
        attachmentUnit = attachmentUnitRepository.saveAndFlush(attachmentUnit);
        attachmentUnit.setLecture(lecture);
        lecture.addLectureUnit(attachmentUnit);
        lectureRepository.save(lecture);

        if (!file.isEmpty()) {
            String filePath = fileService.handleSaveFile(file, keepFilename, false);
            attachment.setLink(filePath);
        }

        attachment.setAttachmentUnit(attachmentUnit);
        Attachment result = attachmentRepository.saveAndFlush(attachment);
        this.cacheManager.getCache("files").evict(fileService.actualPathForPublicPath(result.getLink()));

        // cleanup before sending to client
        attachmentUnit.getLecture().setLectureUnits(null);
        attachmentUnit.getLecture().setAttachments(null);
        attachmentUnit.getLecture().setPosts(null);
        return ResponseEntity.created(new URI("/api/attachment-units/" + attachmentUnit.getId())).body(attachmentUnit);
    }
}
