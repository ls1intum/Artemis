package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * REST controller for managing complaints.
 */
@RestController
@RequestMapping("/api")
public class ComplaintResource {
    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String ENTITY_NAME = "complaint";

    private ComplaintRepository complaintRepository;
    private ResultRepository resultRepository;
    private UserRepository userRepository;

    /**
     * POST /complaint: create a new complaint
     *
     * @param complaint the complaint to create
     * @return the ResponseEntity with status 201 (Created) and with body the new complaints
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/complaint")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Complaint> createComplaint(@RequestBody Complaint complaint, Principal principal) throws URISyntaxException {
        log.debug("REST request to save Complaint: {}", complaint);
        if (complaint.getId() != null) {
            throw new BadRequestAlertException("A new complaint cannot already have an id", ENTITY_NAME, "idexists");
        }

        if (complaint.getResult() == null || complaint.getResult().getId() == null) {
            throw new BadRequestAlertException("A complaint can be only associated to a result", ENTITY_NAME, "noresult");
        }

        String submissorName = principal.getName();
        User originalSubmissor = userRepository.findUserIdByResultId(complaint.getResult().getId());

        if (!originalSubmissor.getLogin().equals(submissorName)) {
            throw new BadRequestAlertException("You can create a complaint only about a result you submitted", ENTITY_NAME, "differentuser");
        }

        // Do not trust user input
        Result originalResult = resultRepository.getOne(complaint.getResult().getId());

        complaint.setSubmittedTime(ZonedDateTime.now());
        complaint.setStudent(originalSubmissor);
        complaint.setResult(originalResult);
        complaint.setResultBeforeComplaint(originalResult.getResultString());

        Complaint result = complaintRepository.save(complaint);
        return ResponseEntity.created(new URI("/api/complaints/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * GET  /complaints/:id : get the "id" complaint.
     *
     * @param id the id of the complaint to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the complaint, or with status 404 (Not Found)
     */
    @GetMapping("/complaints/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Complaint> getComplaint(@PathVariable Long id) {
        log.debug("REST request to get Complaint : {}", id);
        Optional<Complaint> complaint = complaintRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(complaint);
    }

    /**
     * Get /complaints/result/:id get a complaint associated with the result "id"
     *
     * @param resultId the id of the result for which we want to find a linked complaint
     * @return the ResponseEntity with status 200 (OK) and with body the complaint, or with status 404 (Not Found)
     */
    @GetMapping("/complaints/result/{resultId")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Complaint> getComplaintByResultId(@PathVariable Long resultId) {
        log.debug("REST request to get Complaint associated to result : {}", resultId);
        Optional<Complaint> complaint = complaintRepository.findByResult_Id(resultId);
        return ResponseUtil.wrapOrNotFound(complaint);
    }
}
