package de.tum.in.www1.artemis.web.rest;

import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.FileUploadExerciseService;
import de.tum.in.www1.artemis.service.FileUploadSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing FileUploadSubmission.
 */
@RestController
@RequestMapping("/api")
public class FileUploadSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionResource.class);

    private static final String ENTITY_NAME = "fileUploadSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final CourseService courseService;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final FileUploadExerciseService fileUploadExerciseService;

    private final AuthorizationCheckService authCheckService;

    public FileUploadSubmissionResource(FileUploadSubmissionRepository fileUploadSubmissionRepository, CourseService courseService,
            FileUploadSubmissionService fileUploadSubmissionService, FileUploadExerciseService fileUploadExerciseService, AuthorizationCheckService authCheckService) {
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.courseService = courseService;
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.fileUploadExerciseService = fileUploadExerciseService;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /file-upload-submissions : Create a new fileUploadSubmission.
     *
     * @param fileUploadSubmission the fileUploadSubmission to create
     * @return the ResponseEntity with status 201 (Created) and with body the new fileUploadSubmission, or with status 400 (Bad Request) if the fileUploadSubmission has already an
     *         ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("exercise/{exerciseId}/file-upload-submissions")
    public ResponseEntity<FileUploadSubmission> createFileUploadSubmission(@PathVariable Long exerciseId, Principal principal,
            @RequestBody FileUploadSubmission fileUploadSubmission) throws URISyntaxException {
        log.debug("REST request to save FileUploadSubmission : {}", fileUploadSubmission);
        if (fileUploadSubmission.getId() != null) {
            throw new BadRequestAlertException("A new fileUploadSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        FileUploadExercise fileUploadExercise = fileUploadExerciseService.findOne(exerciseId);
        checkAuthorization(fileUploadExercise);
        return handleFileUploadSubmission(exerciseId, principal, fileUploadSubmission);
    }

    /**
     * PUT /file-upload-submissions : Updates an existing fileUploadSubmission.
     *
     * @param fileUploadSubmission the fileUploadSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadSubmission, or with status 400 (Bad Request) if the fileUploadSubmission is not valid, or
     *         with status 500 (Internal Server Error) if the fileUploadSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("exercise/{exerciseId}/file-upload-submissions")
    public ResponseEntity<FileUploadSubmission> updateFileUploadSubmission(@PathVariable Long exerciseId, Principal principal,
            @RequestBody FileUploadSubmission fileUploadSubmission) throws URISyntaxException {
        log.debug("REST request to update FileUploadSubmission : {}", fileUploadSubmission);
        if (fileUploadSubmission.getId() == null) {
            return createFileUploadSubmission(exerciseId, principal, fileUploadSubmission);
        }
        return handleFileUploadSubmission(exerciseId, principal, fileUploadSubmission);
    }

    /**
     * GET /file-upload-submissions : get all the fileUploadSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of fileUploadSubmissions in body
     */
    @GetMapping("/file-upload-submissions")
    public List<FileUploadSubmission> getAllFileUploadSubmissions() {
        log.debug("REST request to get all FileUploadSubmissions");
        return fileUploadSubmissionRepository.findAll();
    }

    /**
     * GET /file-upload-submissions/:id : get the "id" fileUploadSubmission.
     *
     * @param id the id of the fileUploadSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/file-upload-submissions/{id}")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmission(@PathVariable Long id) {
        log.debug("REST request to get FileUploadSubmission : {}", id);
        Optional<FileUploadSubmission> fileUploadSubmission = fileUploadSubmissionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(fileUploadSubmission);
    }

    /**
     * DELETE /file-upload-submissions/:id : delete the "id" fileUploadSubmission.
     *
     * @param id the id of the fileUploadSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/file-upload-submissions/{id}")
    public ResponseEntity<Void> deleteFileUploadSubmission(@PathVariable Long id) {
        log.debug("REST request to delete FileUploadSubmission : {}", id);
        fileUploadSubmissionRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }

    @NotNull
    private ResponseEntity<FileUploadSubmission> handleFileUploadSubmission(@PathVariable Long exerciseId, Principal principal,
            @RequestBody FileUploadSubmission fileUploadSubmission) {
        FileUploadExercise fileUploadExercise = fileUploadExerciseService.findOne(exerciseId);
        ResponseEntity<FileUploadSubmission> responseFailure = this.checkExerciseValidity(fileUploadExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        fileUploadSubmission = fileUploadSubmissionService.handleFileUploadSubmission(fileUploadSubmission, fileUploadExercise, principal);

        hideDetails(fileUploadSubmission);
        return ResponseEntity.ok(fileUploadSubmission);
    }

    private ResponseEntity<FileUploadSubmission> checkExerciseValidity(FileUploadExercise fileUploadExercise) {
        if (fileUploadExercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(fileUploadExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "courseNotFound", "The course belonging to this text exercise does not exist"))
                    .body(null);
        }
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return null;
    }

    /**
     * Removes sensitive information (e.g. example solution of the exercise) from the submission based on the role of the current user. This should be called before sending a
     * submission to the client. IMPORTANT: Do not call this method from a transactional context as this would remove the sensitive information also from the entities in the
     * database without explicitly saving them.
     */
    private void hideDetails(FileUploadSubmission fileUploadSubmission) {
        // do not send old submissions or old results to the client
        if (fileUploadSubmission.getParticipation() != null) {
            fileUploadSubmission.getParticipation().setSubmissions(null);
            fileUploadSubmission.getParticipation().setResults(null);

            Exercise exercise = fileUploadSubmission.getParticipation().getExercise();
            if (exercise != null) {
                // make sure that sensitive information is not sent to the client for students
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                    exercise.filterSensitiveInformation();
                    fileUploadSubmission.setResult(null);
                }
                // remove information about the student from the submission for tutors to ensure a double-blind assessment
                if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
                    StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
                    studentParticipation.setStudent(null);
                }
            }
        }
    }

    private void checkAuthorization(FileUploadExercise exercise) throws AccessForbiddenException {
        Course course = courseService.findOne(exercise.getCourse().getId());
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourse().getTitle());
        }
    }
}
