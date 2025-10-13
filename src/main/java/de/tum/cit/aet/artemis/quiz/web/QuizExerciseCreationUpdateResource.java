package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseCreateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseFromEditorDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * REST controller for creating and updating quiz exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseCreationUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseCreationUpdateResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizExerciseService quizExerciseService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    public QuizExerciseCreationUpdateResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, CourseService courseService,
            AuthorizationCheckService authCheckService, CourseRepository courseRepository) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /exercise-groups/{exerciseGroupId}/quiz-exercises : Create a new quizExercise for an exam.
     *
     * @param exerciseGroupId the id of the exercise group to which the quiz exercise should be added
     * @param quizExerciseDTO the quizExercise DTO to create
     * @param files           the files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExerciseDTO}
     * @return the ResponseEntity with status 201 (Created) and with body the new quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid
     * @throws IOException if there is an error handling the files
     */
    @PostMapping(value = "exercise-groups/{exerciseGroupId}/quiz-exercises", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditor
    public ResponseEntity<QuizExercise> createExamQuizExercise(@PathVariable Long exerciseGroupId, @Valid @RequestPart("exercise") QuizExerciseCreateDTO quizExerciseDTO,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException, URISyntaxException {
        log.info("REST request to create QuizExercise : {} in exam exercise group {}", quizExerciseDTO, exerciseGroupId);
        QuizExercise quizExercise = quizExerciseDTO.toDomainObject();

        // We create a new ExerciseGroup with the given id
        // The exercise group is replaced when retrieveCourseOverExerciseGroupOrCourseId is called below
        // This approach avoids an additional database call
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setId(exerciseGroupId);
        quizExercise.setExerciseGroup(exerciseGroup);

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(quizExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        QuizExercise result = quizExerciseService.createQuizExercise(quizExercise, files, true);
        return ResponseEntity.created(new URI("/api/quiz/quiz-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * POST /courses/{courseId}/quiz-exercises : Create a new quizExercise for a course.
     *
     * @param courseId        the id of the course to which the quiz exercise should be added
     * @param quizExerciseDTO the quizExercise DTO to create
     * @param files           the files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExerciseDTO}
     * @return the ResponseEntity with status 201 (Created) and with body the new quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid
     * @throws IOException if there is an error handling the files
     */
    @PostMapping(value = "courses/{courseId}/quiz-exercises", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<QuizExercise> createCourseQuizExercise(@PathVariable Long courseId, @Valid @RequestPart("exercise") QuizExerciseCreateDTO quizExerciseDTO,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException, URISyntaxException {
        log.info("REST request to create QuizExercise : {} in course {}", quizExerciseDTO, courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        QuizExercise quizExercise = quizExerciseDTO.toDomainObject();
        quizExercise.setCourse(course);

        QuizExercise result = quizExerciseService.createQuizExercise(quizExercise, files, false);
        return ResponseEntity.created(new URI("/api/quiz/quiz-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PATCH /quiz-exercises/:exerciseId : Update an existing quizExercise with a DTO.
     *
     * @param exerciseId                the id of the quizExercise to save
     * @param quizExerciseFromEditorDTO the quizExercise to update
     * @param files                     the new files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in
     *                                      {@code quizExercise}
     * @param notificationText          about the quiz exercise update that should be displayed to the student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid, or with status 500
     *         (Internal Server Error) if the quizExercise couldn't be updated
     */
    @PatchMapping(value = "quiz-exercises/{exerciseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<QuizExercise> updateQuizExercise(@PathVariable Long exerciseId, @RequestPart("exercise") QuizExerciseFromEditorDTO quizExerciseFromEditorDTO,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, @RequestParam(value = "notificationText", required = false) String notificationText)
            throws IOException {
        log.info("REST request to patch quiz exercise : {}", exerciseId);
        QuizExercise quizBase = quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(exerciseId);

        QuizExercise originalQuiz = quizExerciseService.copyFieldsForUpdate(quizBase);

        quizExerciseService.mergeDTOIntoDomainObject(quizBase, quizExerciseFromEditorDTO);
        QuizExercise result = quizExerciseService.performUpdate(originalQuiz, quizBase, files, notificationText);
        return ResponseEntity.ok(result);
    }
}
