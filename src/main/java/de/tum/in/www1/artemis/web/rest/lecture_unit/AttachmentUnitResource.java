package de.tum.in.www1.artemis.web.rest.lecture_unit;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.lecture_unit.AttachmentUnit;
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

    public AttachmentUnitResource(AttachmentUnitService attachmentUnitService) {
        this.attachmentUnitService = attachmentUnitService;
    }

    @PostMapping("/lectures/{lectureId}/attachment-units")
    @PreAuthorize("hasAnyRole('ADMIN','INSTRUCTOR')")
    public ResponseEntity<AttachmentUnit> createAttachmentUnit(@PathVariable Long lectureId, @RequestBody AttachmentUnit attachmentUnit) throws URISyntaxException {
        log.debug("REST request to create AttachmentUnit : {}", attachmentUnit);
        if (attachmentUnit.getId() != null) {
            throw new BadRequestAlertException("A new Attachment cannot already have an ID", ENTITY_NAME, "idexists");
        }
        AttachmentUnit persistedAttachmentUnit = this.attachmentUnitService.createAttachmentUnit(lectureId, attachmentUnit);
        return ResponseEntity.created(new URI("/api/exercise-units/" + persistedAttachmentUnit.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, persistedAttachmentUnit.getId().toString())).body(persistedAttachmentUnit);

    }

}
