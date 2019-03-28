package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** REST controller for managing FileUploadExercise. */
@RestController
@RequestMapping("/api")
public class FileUploadExerciseResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadExerciseResource.class);

    private static final String ENTITY_NAME = "fileUploadExercise";

    private final FileUploadExerciseRepository fileUploadExerciseRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final AuthorizationCheckService authCheckService;
    private final GroupNotificationService groupNotificationService;

    public FileUploadExerciseResource(
            FileUploadExerciseRepository fileUploadExerciseRepository,
            UserService userService,
            AuthorizationCheckService authCheckService,
            CourseService courseService,
            GroupNotificationService groupNotificationService) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
    }

    /**
    * POST /file-upload-exercises : Create a new fileUploadExercise.
    *
    * @param fileUploadExercise the fileUploadExercise to create
    * @return the ResponseEntity with status 201 (Created) and with body the new fileUploadExercise,
    *     or with status 400 (Bad Request) if the fileUploadExercise has already an ID
    * @throws URISyntaxException if the Location URI syntax is incorrect
    */
    @PostMapping("/file-upload-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadExercise> createFileUploadExercise(
            @RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to save FileUploadExercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() != null) {
            throw new BadRequestAlertException(
                    "A new fileUploadExercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(fileUploadExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createFailureAlert(
                                    ENTITY_NAME,
                                    "courseNotFound",
                                    "The course belonging to this file upload exercise does not exist"))
                    .body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user)
                && !authCheckService.isInstructorInCourse(course, user)
                && !authCheckService.isAdmin()) {
            return forbidden();
        }
        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);
        groupNotificationService.notifyGroupAboutExerciseChange(fileUploadExercise);
        return ResponseEntity.created(new URI("/api/file-upload-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
                .body(result);
    }

    /**
    * PUT /file-upload-exercises : Updates an existing fileUploadExercise.
    *
    * @param fileUploadExercise the fileUploadExercise to update
    * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadExercise,
    *     or with status 400 (Bad Request) if the fileUploadExercise is not valid, or with status 500
    *     (Internal Server Error) if the fileUploadExercise couldn't be updated
    * @throws URISyntaxException if the Location URI syntax is incorrect
    */
    @PutMapping("/file-upload-exercises")
    public ResponseEntity<FileUploadExercise> updateFileUploadExercise(
            @RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to update FileUploadExercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() == null) {
            return createFileUploadExercise(fileUploadExercise);
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(fileUploadExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createFailureAlert(
                                    ENTITY_NAME,
                                    "courseNotFound",
                                    "The course belonging to this file upload exercise does not exist"))
                    .body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user)
                && !authCheckService.isInstructorInCourse(course, user)
                && !authCheckService.isAdmin()) {
            return forbidden();
        }
        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);
        groupNotificationService.notifyGroupAboutExerciseChange(fileUploadExercise);
        return ResponseEntity.ok()
                .headers(
                        HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, fileUploadExercise.getId().toString()))
                .body(result);
    }

    /**
    * GET /courses/:courseId/exercises : get all the exercises.
    *
    * @return the ResponseEntity with status 200 (OK) and the list of fileUploadExercises in body
    */
    @GetMapping(value = "/courses/{courseId}/file-upload-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<FileUploadExercise>> getFileUploadExercisesForCourse(
            @PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user)
                && !authCheckService.isInstructorInCourse(course, user)
                && !authCheckService.isAdmin()) {
            return forbidden();
        }
        List<FileUploadExercise> exercises = fileUploadExerciseRepository.findByCourseId(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
    * GET /file-upload-exercises/:id : get the "id" fileUploadExercise.
    *
    * @param id the id of the fileUploadExercise to retrieve
    * @return the ResponseEntity with status 200 (OK) and with body the fileUploadExercise, or with
    *     status 404 (Not Found)
    */
    @GetMapping("/file-upload-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<FileUploadExercise> getFileUploadExercise(@PathVariable Long id) {
        log.debug("REST request to get FileUploadExercise : {}", id);
        Optional<FileUploadExercise> fileUploadExercise = fileUploadExerciseRepository.findById(id);
        Course course = fileUploadExercise.get().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user)
                && !authCheckService.isInstructorInCourse(course, user)
                && !authCheckService.isAdmin()) {
            return forbidden();
        }
        return ResponseUtil.wrapOrNotFound(fileUploadExercise);
    }

    /**
    * DELETE /file-upload-exercises/:id : delete the "id" fileUploadExercise.
    *
    * @param id the id of the fileUploadExercise to delete
    * @return the ResponseEntity with status 200 (OK)
    */
    @DeleteMapping("/file-upload-exercises/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteFileUploadExercise(@PathVariable Long id) {
        log.debug("REST request to delete FileUploadExercise : {}", id);
        Optional<FileUploadExercise> fileUploadExercise = fileUploadExerciseRepository.findById(id);
        Course course = fileUploadExercise.get().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return forbidden();
        }
        fileUploadExerciseRepository.deleteById(id);
        return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString()))
                .build();
    }
}
