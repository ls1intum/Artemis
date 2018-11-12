package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.TextExerciseService;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing TextSubmission.
 */
@RestController
@RequestMapping("/api")
public class TextSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(TextSubmissionResource.class);

    private static final String ENTITY_NAME = "textSubmission";

    private final TextSubmissionRepository textSubmissionRepository;
    private TextExerciseService textExerciseService;
    private CourseService courseService;
    private ParticipationService participationService;
    private TextSubmissionService textSubmissionService;

    public TextSubmissionResource(TextSubmissionRepository textSubmissionRepository,
                                  TextExerciseService textExerciseService,
                                  CourseService courseService,
                                  ParticipationService participationService,
                                  TextSubmissionService textSubmissionService) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.textExerciseService = textExerciseService;
        this.courseService = courseService;
        this.participationService = participationService;
        this.textSubmissionService = textSubmissionService;
    }

    /**
     * POST  /exercises/{exerciseId}/text-submissions : Create a new textSubmission.
     * This is called when a student saves his/her answer
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param textSubmission the textSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/text-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @Timed
    public ResponseEntity<TextSubmission> createTextSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody TextSubmission textSubmission) {
        log.debug("REST request to save TextSubmission : {}", textSubmission);
        if (textSubmission.getId() != null) {
            throw new BadRequestAlertException("A new textSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }

        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        if (textExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(textExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this text exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(exerciseId, principal.getName());

        // update and save submission
        textSubmission = textSubmissionService.save(textSubmission, textExercise, participation);
        return ResponseEntity.ok(textSubmission);
    }

    /**
     * PUT  /exercises/{exerciseId}/text-submissions : Updates an existing textSubmission.
     * This function is called by the text editor for saving and submitting text submissions.
     * The submit specific handling occurs in the TextSubmissionService.save() function.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param textSubmission the textSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textSubmission,
     * or with status 400 (Bad Request) if the textSubmission is not valid,
     * or with status 500 (Internal Server Error) if the textSubmission couldn't be updated
     */
    @PutMapping("/exercises/{exerciseId}/text-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    @Timed
    public ResponseEntity<TextSubmission> updateTextSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody TextSubmission textSubmission) {
        log.debug("REST request to update TextSubmission : {}", textSubmission);
        if (textSubmission.getId() == null) {
            return createTextSubmission(exerciseId, principal, textSubmission);
        }

        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        if (textExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textExercise", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(textExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this text exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(exerciseId, principal.getName());

        textSubmission = textSubmissionService.save(textSubmission, textExercise, participation);

        return ResponseEntity.ok(textSubmission);
    }

    /**
     * POST  /text-submissions : Create a new textSubmission.
     *
     * @param textSubmission the textSubmission to create
     * @return the ResponseEntity with status 201 (Created) and with body the new textSubmission, or with status 400 (Bad Request) if the textSubmission has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/text-submissions")
    @Timed
    public ResponseEntity<TextSubmission> createTextSubmission(@RequestBody TextSubmission textSubmission) throws URISyntaxException {
        log.debug("REST request to save TextSubmission : {}", textSubmission);
        if (textSubmission.getId() != null) {
            throw new BadRequestAlertException("A new textSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        TextSubmission result = textSubmissionRepository.save(textSubmission);
        return ResponseEntity.created(new URI("/api/text-submissions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /text-submissions : Updates an existing textSubmission.
     *
     * @param textSubmission the textSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textSubmission,
     * or with status 400 (Bad Request) if the textSubmission is not valid,
     * or with status 500 (Internal Server Error) if the textSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/text-submissions")
    @Timed
    public ResponseEntity<TextSubmission> updateTextSubmission(@RequestBody TextSubmission textSubmission) throws URISyntaxException {
        log.debug("REST request to update TextSubmission : {}", textSubmission);
        if (textSubmission.getId() == null) {
            return createTextSubmission(textSubmission);
        }
        TextSubmission result = textSubmissionRepository.save(textSubmission);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, textSubmission.getId().toString()))
            .body(result);
    }

    /**
     * GET  /text-submissions : get all the textSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping("/text-submissions")
    @Timed
    public List<TextSubmission> getAllTextSubmissions() {
        log.debug("REST request to get all TextSubmissions");
        return textSubmissionRepository.findAll();
    }

    /**
     * GET  /text-submissions/:id : get the "id" textSubmission.
     *
     * @param id the id of the textSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the textSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/text-submissions/{id}")
    @Timed
    public ResponseEntity<TextSubmission> getTextSubmission(@PathVariable Long id) {
        log.debug("REST request to get TextSubmission : {}", id);
        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(textSubmission);
    }

    /**
     * DELETE  /text-submissions/:id : delete the "id" textSubmission.
     *
     * @param id the id of the textSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/text-submissions/{id}")
    @Timed
    public ResponseEntity<Void> deleteTextSubmission(@PathVariable Long id) {
        log.debug("REST request to delete TextSubmission : {}", id);
        textSubmissionRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
