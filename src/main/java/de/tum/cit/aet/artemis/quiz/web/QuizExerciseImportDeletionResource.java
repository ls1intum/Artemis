package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * REST controller for deleting and importing quiz exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseImportDeletionResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseImportDeletionResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizExerciseService quizExerciseService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final QuizExerciseImportService quizExerciseImportService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    public QuizExerciseImportDeletionResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, UserRepository userRepository,
            ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService, QuizExerciseImportService quizExerciseImportService, CourseService courseService,
            AuthorizationCheckService authCheckService) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.quizExerciseImportService = quizExerciseImportService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
    }

    /**
     * DELETE /quiz-exercises/:quizExerciseId : delete the "id" quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("quiz-exercises/{quizExerciseId}")
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long quizExerciseId) {
        log.info("REST request to delete quiz exercise : {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndCompetenciesElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        List<DragAndDropQuestion> dragAndDropQuestions = quizExercise.getQuizQuestions().stream().filter(question -> question instanceof DragAndDropQuestion)
                .map(question -> ((DragAndDropQuestion) question)).toList();
        List<String> backgroundImagePaths = dragAndDropQuestions.stream().map(DragAndDropQuestion::getBackgroundFilePath).toList();
        List<String> dragItemImagePaths = dragAndDropQuestions.stream().flatMap(question -> question.getDragItems().stream().map(DragItem::getPictureFilePath)).toList();
        List<Path> imagesToDelete = Stream
                .concat(backgroundImagePaths.stream().filter(Objects::nonNull).map(path -> convertToActualPath(path, FilePathType.DRAG_AND_DROP_BACKGROUND)),
                        dragItemImagePaths.stream().filter(Objects::nonNull).map(path -> convertToActualPath(path, FilePathType.DRAG_ITEM)))
                .filter(Objects::nonNull).toList();

        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(quizExercise, quizExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(quizExerciseId, false);
        quizExerciseService.cancelScheduledQuiz(quizExerciseId);

        FileUtil.deleteFiles(imagesToDelete);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, quizExercise.getTitle())).build();
    }

    private Path convertToActualPath(String pathString, FilePathType filePathType) {
        try {
            return FilePathConverter.fileSystemPathForExternalUri(URI.create(pathString), filePathType);
        }
        catch (FilePathParsingException e) {
            log.warn("Could not find file {} for deletion", pathString);
            return null;
        }
    }

    /**
     * POST /quiz-exercises/import: Imports an existing quiz exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and dates. Referenced
     * entities will get cloned and assigned a new id.
     *
     * @param sourceExerciseId The ID of the original exercise which should get imported
     * @param importedExercise The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @param files            the files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExercise}
     * @return The imported exercise (200), a not found error (404) if the template does not exist,
     *         or a forbidden error (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping(value = "quiz-exercises/import/{sourceExerciseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditor
    public ResponseEntity<QuizExercise> importExercise(@PathVariable long sourceExerciseId, @RequestPart("exercise") QuizExercise importedExercise,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws URISyntaxException, IOException {
        log.info("REST request to import from quiz exercise : {}", sourceExerciseId);
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }

        // Valid exercises have set either a course or an exerciseGroup
        importedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(importedExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        if (!importedExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see above in create Quiz)
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        List<MultipartFile> nullsafeFiles = files != null ? files : new ArrayList<>();

        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();
        quizExerciseService.validateQuizExerciseFiles(importedExercise, nullsafeFiles, false);

        final var originalQuizExercise = quizExerciseRepository.findByIdElseThrow(sourceExerciseId);
        QuizExercise newQuizExercise = quizExerciseImportService.importQuizExercise(originalQuizExercise, importedExercise, files);

        return ResponseEntity.created(new URI("/api/quiz/quiz-exercises/" + newQuizExercise.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, newQuizExercise.getId().toString())).body(newQuizExercise);
    }

}
