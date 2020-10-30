package de.tum.in.www1.artemis.web.rest.lecture_unit;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture_unit.AttachmentUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.lecture_unit.AttachmentUnitRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class AttachmentUnitResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(ExerciseUnitResource.class);

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
     * GET /attachment-units/:attachmentUnitId : gets the attachment unit with the specified id
     *
     * @param attachmentUnitId the id of the attachmentUnit to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the attachment unit, or with status 404 (Not Found)
     */
    @GetMapping("/attachment-units/{attachmentUnitId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<AttachmentUnit> getAttachmentUnit(@PathVariable Long attachmentUnitId) {
        log.debug("REST request to get AttachmentUnit : {}", attachmentUnitId);
        Optional<AttachmentUnit> optionalAttachmentUnit = attachmentUnitRepository.findById(attachmentUnitId);
        if (optionalAttachmentUnit.isEmpty()) {
            return notFound();
        }
        AttachmentUnit attachmentUnit = optionalAttachmentUnit.get();
        if (attachmentUnit.getLecture() == null || attachmentUnit.getLecture().getCourse() == null) {
            return conflict();
        }

        if (!authorizationCheckService.isAtLeastInstructorInCourse(attachmentUnit.getLecture().getCourse(), null)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(attachmentUnit);
    }

    /**
     * PUT /lectures/:lectureId/attachment-units : Updates an existing attachment unit .
     *
     * @param lectureId      the id of the lecture to which the attachment unit belongs to update
     * @param attachmentUnit the attachment unit to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated attachmentUnit
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/lectures/{lectureId}/attachment-units")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<AttachmentUnit> updateAttachmentUnit(@PathVariable Long lectureId, @RequestBody AttachmentUnit attachmentUnit) throws URISyntaxException {
        log.debug("REST request to update an attachment unit : {}", attachmentUnit);
        if (attachmentUnit.getId() == null) {
            return badRequest();
        }

        if (attachmentUnit.getLecture() == null || attachmentUnit.getLecture().getCourse() == null) {
            return conflict();
        }

        if (!authorizationCheckService.isAtLeastInstructorInCourse(attachmentUnit.getLecture().getCourse(), null)) {
            return forbidden();
        }

        if (!attachmentUnit.getLecture().getId().equals(lectureId)) {
            return conflict();
        }

        // Make sure that the original references are preserved.
        AttachmentUnit originalAttachmentUnit = attachmentUnitRepository.findById(attachmentUnit.getId()).get();
        attachmentUnit.setAttachment(originalAttachmentUnit.getAttachment());

        AttachmentUnit result = attachmentUnitRepository.save(attachmentUnit);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, attachmentUnit.getId().toString())).body(result);
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
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<AttachmentUnit> createAttachmentUnit(@PathVariable Long lectureId, @RequestBody AttachmentUnit attachmentUnit) throws URISyntaxException {
        log.debug("REST request to create AttachmentUnit : {}", attachmentUnit);
        if (attachmentUnit.getId() != null) {
            return badRequest();
        }
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureId);
        if (lectureOptional.isEmpty()) {
            return badRequest();
        }
        Lecture lecture = lectureOptional.get();
        if (lecture.getCourse() == null) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(lecture.getCourse(), null)) {
            return forbidden();
        }

        lecture.addLectureUnit(attachmentUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        AttachmentUnit persistedAttachmentUnit = (AttachmentUnit) updatedLecture.getLectureUnits().get(updatedLecture.getLectureUnits().size() - 1);

        return ResponseEntity.created(new URI("/api/exercise-units/" + persistedAttachmentUnit.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(persistedAttachmentUnit);

    }

}
