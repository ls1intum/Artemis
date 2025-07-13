package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.FilePathConverter.fileSystemPathForExternalUri;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
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

import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Attachment.
 */
@Profile(PROFILE_CORE)
@Lazy
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

    public AttachmentResource(AttachmentRepository attachmentRepository, GroupNotificationService groupNotificationService, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, FileService fileService) {
        this.attachmentRepository = attachmentRepository;
        this.groupNotificationService = groupNotificationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.fileService = fileService;
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
    public ResponseEntity<Attachment> updateAttachment(@PathVariable Long attachmentId, @RequestPart Attachment attachment, @RequestPart(required = false) MultipartFile file,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update Attachment : {}", attachment);
        attachment.setId(attachmentId);

        // Make sure that the original references are preserved.
        Attachment originalAttachment = attachmentRepository.findByIdOrElseThrow(attachment.getId());
        attachment.setAttachmentVideoUnit(originalAttachment.getAttachmentVideoUnit());

        if (file != null) {
            Path basePath = FilePathConverter.getLectureAttachmentFileSystemPath().resolve(originalAttachment.getLecture().getId().toString());
            Path savePath = FileUtil.saveFile(file, basePath, FilePathType.LECTURE_ATTACHMENT, true);
            attachment.setLink(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.LECTURE_ATTACHMENT, originalAttachment.getLecture().getId()).toString());
            // Delete the old file
            URI oldPath = URI.create(originalAttachment.getLink());
            fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(oldPath, FilePathType.LECTURE_ATTACHMENT), 0);
            this.fileService.evictCacheForPath(FilePathConverter.fileSystemPathForExternalUri(oldPath, FilePathType.LECTURE_ATTACHMENT));
        }

        Attachment result = attachmentRepository.save(attachment);
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutAttachmentChange(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /attachments/:id : get the "id" attachment.
     *
     * @param id the id of the attachment to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the attachment, or with status 404 (Not Found)
     */
    @GetMapping("attachments/{id}")
    @EnforceAtLeastEditor
    public ResponseEntity<Attachment> getAttachment(@PathVariable Long id) {
        log.debug("REST request to get Attachment : {}", id);
        Optional<Attachment> attachment = attachmentRepository.findById(id);
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
    public ResponseEntity<List<Attachment>> getAttachmentsForLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get all attachments for the lecture with id : {}", lectureId);
        return ResponseEntity.ok(attachmentRepository.findAllByLectureId(lectureId));
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
        User user = userRepository.getUserWithGroupsAndAuthorities();
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
