package de.tum.in.www1.artemis.web.rest.lecture_unit;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.conflict;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.lecture_unit.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.lecture_unit.AttachmentUnitRepository;
import de.tum.in.www1.artemis.service.lecture_unit.AttachmentUnitService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class AttachmentUnitResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(ExerciseUnitResource.class);

    private static final String ENTITY_NAME = "attachmentUnit";

    private final AttachmentUnitService attachmentUnitService;

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final AttachmentRepository attachmentRepository;

    public AttachmentUnitResource(AttachmentUnitService attachmentUnitService, AttachmentUnitRepository attachmentUnitRepository, AttachmentRepository attachmentRepository) {
        this.attachmentUnitService = attachmentUnitService;
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @GetMapping("/attachment-units/{attachmentUnitId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<AttachmentUnit> getAttachmentUnit(@PathVariable Long attachmentUnitId) {
        log.debug("REST request to get AttachmentUnit : {}", attachmentUnitId);
        Optional<AttachmentUnit> optionalAttachmentUnit = attachmentUnitRepository.findById(attachmentUnitId);
        if (optionalAttachmentUnit.isEmpty()) {
            return notFound();
        }
        AttachmentUnit attachmentUnit = optionalAttachmentUnit.get();
        return ResponseEntity.ok().body(attachmentUnit);
    }

    @PutMapping("/lectures/{lectureId}/attachment-units")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<AttachmentUnit> updateAttachmentUnit(@PathVariable Long lectureId, @RequestBody AttachmentUnit attachmentUnit) throws URISyntaxException {
        log.debug("REST request to update an attachment unit : {}", attachmentUnit);
        if (attachmentUnit.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }

        if (attachmentUnit.getLecture() == null) {
            return conflict();
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

    @PostMapping("/lectures/{lectureId}/attachment-units")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<AttachmentUnit> createAttachmentUnit(@PathVariable Long lectureId, @RequestBody AttachmentUnit attachmentUnit) throws URISyntaxException {
        log.debug("REST request to create AttachmentUnit : {}", attachmentUnit);
        if (attachmentUnit.getId() != null) {
            throw new BadRequestAlertException("A new Attachment cannot already have an ID", ENTITY_NAME, "idexists");
        }

        // refactor to id way also on attachment sid

        AttachmentUnit persistedAttachmentUnit = this.attachmentUnitService.createAttachmentUnit(lectureId, attachmentUnit);
        return ResponseEntity.created(new URI("/api/exercise-units/" + persistedAttachmentUnit.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, persistedAttachmentUnit.getId().toString())).body(persistedAttachmentUnit);

    }

}
