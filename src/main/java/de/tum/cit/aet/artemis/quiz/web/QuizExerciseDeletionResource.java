package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
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
public class QuizExerciseDeletionResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseDeletionResource.class);

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

    private final ExerciseVersionService exerciseVersionService;

    private final Optional<AtlasMLApi> atlasMLApi;

    public QuizExerciseDeletionResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, UserRepository userRepository,
            ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService, QuizExerciseImportService quizExerciseImportService, CourseService courseService,
            AuthorizationCheckService authCheckService, ExerciseVersionService exerciseVersionService, Optional<AtlasMLApi> atlasMLApi) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.quizExerciseImportService = quizExerciseImportService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.exerciseVersionService = exerciseVersionService;
        this.atlasMLApi = atlasMLApi;
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

        // Notify AtlasML about the quiz exercise deletion before actual deletion
        notifyAtlasML(quizExercise, OperationTypeDTO.DELETE, "quiz exercise deletion");

        // note: we use the exercise service here, because this one makes sure to clean
        // up all lazy references correctly.
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
     * Helper method to notify AtlasML about quiz exercise changes with consistent
     * error handling.
     *
     * @param exercise             the exercise to save
     * @param operationType        the operation type (UPDATE or DELETE)
     * @param operationDescription the description of the operation for logging
     *                                 purposes
     */
    private void notifyAtlasML(QuizExercise exercise, OperationTypeDTO operationType, String operationDescription) {
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(exercise, operationType);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about {}: {}", operationDescription, e.getMessage());
            }
        });
    }

}
