package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.TutorGroup;
import de.tum.in.www1.artemis.repository.TutorGroupRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing TutorGroup.
 */
@RestController
@RequestMapping("/api")
public class TutorGroupResource {

    private final Logger log = LoggerFactory.getLogger(TutorGroupResource.class);

    private static final String ENTITY_NAME = "tutorGroup";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TutorGroupRepository tutorGroupRepository;

    public TutorGroupResource(TutorGroupRepository tutorGroupRepository) {
        this.tutorGroupRepository = tutorGroupRepository;
    }

    /**
     * POST /tutor-groups : Create a new tutorGroup.
     *
     * @param tutorGroup the tutorGroup to create
     * @return the ResponseEntity with status 201 (Created) and with body the new tutorGroup, or with status 400 (Bad Request) if the tutorGroup has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/tutor-groups")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<TutorGroup> createTutorGroup(@RequestBody TutorGroup tutorGroup) throws URISyntaxException {
        log.debug("REST request to save TutorGroup : {}", tutorGroup);
        if (tutorGroup.getId() != null) {
            throw new BadRequestAlertException("A new tutorGroup cannot already have an ID", ENTITY_NAME, "idexists");
        }
        TutorGroup result = tutorGroupRepository.save(tutorGroup);
        return ResponseEntity.created(new URI("/api/tutor-groups/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /tutor-groups : Updates an existing tutorGroup.
     *
     * @param tutorGroup the tutorGroup to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorGroup, or with status 400 (Bad Request) if the tutorGroup is not valid, or with status 500
     *         (Internal Server Error) if the tutorGroup couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/tutor-groups")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<TutorGroup> updateTutorGroup(@RequestBody TutorGroup tutorGroup) throws URISyntaxException {
        log.debug("REST request to update TutorGroup : {}", tutorGroup);
        if (tutorGroup.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        TutorGroup result = tutorGroupRepository.save(tutorGroup);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, tutorGroup.getId().toString())).body(result);
    }

    /**
     * GET /tutor-groups/:id : get the "id" tutorGroup.
     *
     * @param id the id of the tutorGroup to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the tutorGroup, or with status 404 (Not Found)
     */
    @GetMapping("/tutor-groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<TutorGroup> getTutorGroup(@PathVariable Long id) {
        log.debug("REST request to get TutorGroup : {}", id);
        Optional<TutorGroup> tutorGroup = tutorGroupRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(tutorGroup);
    }

    /**
     * DELETE /tutor-groups/:id : delete the "id" tutorGroup.
     *
     * @param id the id of the tutorGroup to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/tutor-groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteTutorGroup(@PathVariable Long id) {
        log.debug("REST request to delete TutorGroup : {}", id);
        tutorGroupRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
