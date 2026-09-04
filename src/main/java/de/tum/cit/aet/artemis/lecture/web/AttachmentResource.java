package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.util.FilePathConverter.fileSystemPathForExternalUri;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.web.util.ResponseUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.service.AttachmentService;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationService;

/**
 * REST controller for managing Attachment.
 */
@Conditional(LectureEnabled.class)
@Lazy
@FeatureUsage("authoring/attachments")
@RestController
@RequestMapping("api/lecture/")
public class AttachmentResource {

    private static final Logger log = LoggerFactory.getLogger(AttachmentResource.class);

    private static final String ENTITY_NAME = "attachment";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AttachmentRepository attachmentRepository;

    private final GroupNotificationService groupNotificationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final FileService fileService;

    private final AttachmentService attachmentService;

    public AttachmentResource(AttachmentRepository attachmentRepository, GroupNotificationService groupNotificationService, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, FileService fileService, AttachmentService attachmentService) {
        this.attachmentRepository = attachmentRepository;
        this.groupNotificationService = groupNotificationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.attachmentService = attachmentService;
    }

    /**
     * PUT /attachments/:id : Updates an existing attachment.
     *
     * @param attachmentId     the id of the attachment to save
     * @param attachment       the attachment to update
     * @param file             the file to save if the file got changed (optional)
     * @param notificationText text that will be sent to student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated attachment, or with status 400 (Bad Request) if the attachment is not valid, or with status 500
     *         (Internal Server Error) if the attachment couldn't be updated
     */
    @PutMapping(value = "attachments/{attachmentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditor
    public ResponseEntity<AttachmentDTO> updateAttachment(@PathVariable Long attachmentId, @RequestPart AttachmentDTO attachment, @RequestPart(required = false) MultipartFile file,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update Attachment : {}", attachment);

        // Build a transient attachment carrying only the client-provided fields; the service copies them onto the managed attachment
        // and derives server-controlled state (cache-busting version, student version) itself instead of trusting the client payload.
        Attachment attachmentUpdate = toTransientAttachment(attachment);
        Attachment result = attachmentService.updateLectureAttachment(attachmentId, attachmentUpdate, file);
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutAttachmentChange(result);
        }
        return ResponseEntity.ok(AttachmentDTO.of(result));
    }

    /**
     * Builds a transient {@link Attachment} carrying only the fields the client may set via the {@link AttachmentDTO} part.
     * {@link AttachmentService#updateLectureAttachment} copies these onto the managed attachment; the client never deserializes
     * an entity directly, and server-controlled fields (id, link, version, studentVersion, lecture) are never taken from the client.
     *
     * @param attachmentDTO the attachment part from the request
     * @return a new transient attachment
     */
    private static Attachment toTransientAttachment(AttachmentDTO attachmentDTO) {
        Attachment attachment = new Attachment();
        attachment.setName(attachmentDTO.name());
        attachment.setReleaseDate(attachmentDTO.releaseDate());
        attachment.setUploadDate(attachmentDTO.uploadDate());
        attachment.setAttachmentType(attachmentDTO.attachmentType());
        return attachment;
    }

    /**
     * GET /attachments/:id : get the "id" attachment.
     *
     * @param id the id of the attachment to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the attachment, or with status 404 (Not Found)
     */
    @GetMapping("attachments/{id}")
    @EnforceAtLeastEditor
    public ResponseEntity<AttachmentDTO> getAttachment(@PathVariable Long id) {
        log.debug("REST request to get Attachment : {}", id);
        Optional<AttachmentDTO> attachment = attachmentRepository.findById(id).map(AttachmentDTO::of);
        return ResponseUtil.wrapOrNotFound(attachment);
    }

    /**
     * GET /lectures/:lectureId/attachments : get all the attachments of a lecture.
     *
     * @param lectureId the id of the lecture
     * @return the ResponseEntity with status 200 (OK) and the list of attachments in body
     */
    @GetMapping("lectures/{lectureId}/attachments")
    @EnforceAtLeastTutor
    public ResponseEntity<List<AttachmentDTO>> getAttachmentsForLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get all attachments for the lecture with id : {}", lectureId);
        return ResponseEntity.ok(attachmentRepository.findAllByLectureId(lectureId).stream().map(AttachmentDTO::of).toList());
    }

    /**
     * DELETE /attachments/:attachmentId : delete the "id" attachment.
     *
     * @param attachmentId the id of the attachment to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("attachments/{attachmentId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long attachmentId) {
        User user = userRepository.getUserWithAuthorities();
        Optional<Attachment> optionalAttachment = attachmentRepository.findById(attachmentId);
        if (optionalAttachment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Attachment attachment = optionalAttachment.get();
        Course course = null;
        String relatedEntity = null;
        if (attachment.getLecture() != null) {
            course = attachment.getLecture().getCourse();
            relatedEntity = "lecture " + attachment.getLecture().getTitle();
        }
        else if (attachment.getExercise() != null) {
            course = attachment.getExercise().getCourseViaExerciseGroupOrCourseMember();
            relatedEntity = "exercise " + attachment.getExercise().getTitle();
        }
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        log.info("{} deleted attachment with id {} for {}", user.getLogin(), attachmentId, relatedEntity);
        attachmentRepository.deleteById(attachmentId);

        try {
            if (AttachmentType.FILE.equals(attachment.getAttachmentType())) {
                URI oldPath = URI.create(attachment.getLink());
                fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(oldPath, FilePathType.LECTURE_ATTACHMENT), 0);
                this.fileService.evictCacheForPath(fileSystemPathForExternalUri(oldPath, FilePathType.LECTURE_ATTACHMENT));
            }
        }
        catch (RuntimeException exception) {
            // this catch is required for deleting wrongly formatted attachment database entries
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, attachmentId.toString())).build();
    }
}
