package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/** REST controller for managing FileUploadExercise. */
@RestController
@RequestMapping("/api")
public class FileUploadExerciseResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadExerciseResource.class);

    private static final String ENTITY_NAME = "fileUploadExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ExerciseService exerciseService;

    private final UserService userService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    private final GradingCriterionService gradingCriterionService;

    public FileUploadExerciseResource(FileUploadExerciseRepository fileUploadExerciseRepository, UserService userService, AuthorizationCheckService authCheckService,
            CourseService courseService, GroupNotificationService groupNotificationService, ExerciseService exerciseService, GradingCriterionService gradingCriterionService) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseService = exerciseService;
        this.gradingCriterionService = gradingCriterionService;
    }

    /**
     * POST /file-upload-exercises : Create a new fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new fileUploadExercise, or with status 400 (Bad Request) if the fileUploadExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/file-upload-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadExercise> createFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to save FileUploadExercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "A new fileUploadExercise cannot already have an ID", "idexists")).body(null);
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(fileUploadExercise.getCourse().getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        if (!isFilePatternValid(fileUploadExercise)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName,
                            "The file pattern is invalid. Please use a comma separated list with actual file endings without dots (e.g. 'png, pdf').", "filepattern.invalid"))
                    .body(null);
        }

        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);
        groupNotificationService.notifyTutorGroupAboutExerciseCreated(fileUploadExercise);
        return ResponseEntity.created(new URI("/api/file-upload-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    private boolean isFilePatternValid(FileUploadExercise exercise) {
        // a file ending should consist of a comma separated list of 1-5 characters / digits
        var filePattern = exercise.getFilePattern().toLowerCase().replaceAll("\\s+", "");
        var allowedFileEndings = filePattern.split(",");
        if (allowedFileEndings.length == 0) {
            return false;
        }
        var isValid = true;
        for (var allowedFileEnding : allowedFileEndings) {
            isValid = isValid && FILE_ENDING_PATTERN.matcher(allowedFileEnding).matches();
        }

        if (isValid) {
            // use the lowercase version without whitespaces
            exercise.setFilePattern(filePattern);
            return true;
        }
        return false;
    }

    /**
     * PUT /file-upload-exercises : Updates an existing fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to update
     * @param notificationText the text shown to students
     * @param exerciseId the id of exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadExercise, or with status 400 (Bad Request) if the fileUploadExercise is not valid, or
     *         with status 500 (Internal Server Error) if the fileUploadExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadExercise> updateFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText, @PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to update FileUploadExercise : {}", fileUploadExercise);
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(fileUploadExercise.getCourse().getId());
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        if (!isFilePatternValid(fileUploadExercise)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName,
                            "The file pattern is invalid. Please use a comma separated list with actual file endings without dots (e.g. 'png, pdf').", "filepattern.invalid"))
                    .body(null);
        }

        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(fileUploadExercise, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).body(result);
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of fileUploadExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/file-upload-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<FileUploadExercise>> getFileUploadExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        List<FileUploadExercise> exercises = fileUploadExerciseRepository.findByCourseId(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET /file-upload-exercises/:exerciseId : get the "id" fileUploadExercise.
     *
     * @param exerciseId the id of the fileUploadExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadExercise, or with status 404 (Not Found)
     */
    @GetMapping("/file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadExercise> getFileUploadExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get FileUploadExercise : {}", exerciseId);
        Optional<FileUploadExercise> fileUploadExercise = fileUploadExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesById(exerciseId);
        List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        fileUploadExercise.ifPresent(exercise -> exercise.setGradingCriteria(gradingCriteria));
        Course course = fileUploadExercise.get().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        return ResponseUtil.wrapOrNotFound(fileUploadExercise);
    }

    /**
     * DELETE /file-upload-exercises/:exerciseId : delete the "id" fileUploadExercise.
     *
     * @param exerciseId the id of the fileUploadExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteFileUploadExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete FileUploadExercise : {}", exerciseId);
        Optional<FileUploadExercise> fileUploadExercise = fileUploadExerciseRepository.findById(exerciseId);
        if (fileUploadExercise.isEmpty()) {
            return notFound();
        }
        Course course = fileUploadExercise.get().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(fileUploadExercise.get(), course, user);
        exerciseService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, fileUploadExercise.get().getTitle())).build();
    }
}
