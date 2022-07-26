package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Attachment.
 */
@RestController
@RequestMapping("/api")
public class AttachmentResource {

    private final Logger log = LoggerFactory.getLogger(AttachmentResource.class);

    private static final String ENTITY_NAME = "attachment";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AttachmentRepository attachmentRepository;

    private final GroupNotificationService groupNotificationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final FileService fileService;

    private final CacheManager cacheManager;

    public AttachmentResource(AttachmentRepository attachmentRepository, GroupNotificationService groupNotificationService, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, FileService fileService, CacheManager cacheManager) {
        this.attachmentRepository = attachmentRepository;
        this.groupNotificationService = groupNotificationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.cacheManager = cacheManager;
    }

    /**
     * POST /attachments : Create a new attachment.
     *
     * @param attachment the attachment to create
     * @return the ResponseEntity with status 201 (Created) and with body the new attachment, or with status 400 (Bad Request) if the attachment has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/attachments")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Attachment> createAttachment(@RequestBody Attachment attachment) throws URISyntaxException {
        log.debug("REST request to save Attachment : {}", attachment);
        if (attachment.getId() != null) {
            throw new BadRequestAlertException("A new attachment cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Attachment result = attachmentRepository.save(attachment);
        this.cacheManager.getCache("files").evict(fileService.actualPathForPublicPath(result.getLink()));
        return ResponseEntity.created(new URI("/api/attachments/" + result.getId())).body(result);
    }

    /**
     * PUT /attachments : Updates an existing attachment.
     *
     * @param attachment       the attachment to update
     * @param notificationText text that will be send to student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated attachment, or with status 400 (Bad Request) if the attachment is not valid, or with status 500
     *         (Internal Server Error) if the attachment couldn't be updated
     */
    @PutMapping("/attachments")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Attachment> updateAttachment(@RequestBody Attachment attachment, @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update Attachment : {}", attachment);
        if (attachment.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }

        // Make sure that the original references are preserved.
        Optional<Attachment> originalAttachment = attachmentRepository.findById(attachment.getId());
        originalAttachment.ifPresent(value -> attachment.setAttachmentUnit(value.getAttachmentUnit()));

        Attachment result = attachmentRepository.save(attachment);
        this.cacheManager.getCache("files").evict(fileService.actualPathForPublicPath(result.getLink()));
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutAttachmentChange(result, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, attachment.getId().toString())).body(result);
    }

    /**
     * GET /attachments/:id : get the "id" attachment.
     *
     * @param id the id of the attachment to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the attachment, or with status 404 (Not Found)
     */
    @GetMapping("/attachments/{id}")
    @PreAuthorize("hasRole('EDITOR')")
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
    @GetMapping(value = "/lectures/{lectureId}/attachments")
    @PreAuthorize("hasRole('TA')")
    public List<Attachment> getAttachmentsForLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get all attachments for the lecture with id : {}", lectureId);
        return attachmentRepository.findAllByLectureId(lectureId);
    }

    /**
     * DELETE /attachments/:id : delete the "id" attachment.
     *
     * @param id the id of the attachment to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/attachments/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<Attachment> optionalAttachment = attachmentRepository.findById(id);
        if (optionalAttachment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Attachment attachment = optionalAttachment.get();
        Course course = null;
        String relatedEntity = null;
        if (attachment.getLecture() != null) {
            course = attachment.getLecture().getCourse();
            relatedEntity = "lecture " + attachment.getLecture().getTitle();
            try {
                this.cacheManager.getCache("files").evict(fileService.actualPathForPublicPath(attachment.getLink()));
            }
            catch (RuntimeException exception) {
                // this catch is required for deleting wrongly formatted attachment database entries
            }
        }
        else if (attachment.getExercise() != null) {
            course = attachment.getExercise().getCourseViaExerciseGroupOrCourseMember();
            relatedEntity = "exercise " + attachment.getExercise().getTitle();
        }
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        log.info("{} deleted attachment with id {} for {}", user.getLogin(), id, relatedEntity);
        attachmentRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
