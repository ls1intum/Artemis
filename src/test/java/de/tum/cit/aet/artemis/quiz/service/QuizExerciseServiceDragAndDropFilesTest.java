package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;

/**
 * Unit tests for the file handling of drag and drop questions in the {@link QuizExerciseService}, in particular for questions or drag items without a stored file.
 */
class QuizExerciseServiceDragAndDropFilesTest {

    @TempDir
    Path uploadRoot;

    private Path originalUploadPath;

    private QuizExerciseService quizExerciseService;

    @BeforeEach
    void setUp() {
        originalUploadPath = FilePathConverter.getFileUploadPath();
        FilePathConverter.setFileUploadPath(uploadRoot);
        quizExerciseService = new QuizExerciseService(mock(QuizExerciseRepository.class), mock(ResultRepository.class), mock(QuizSubmissionRepository.class),
                mock(InstanceMessageSendService.class), Optional.empty(), mock(QuizStatisticsService.class), mock(QuizBatchService.class), mock(ExerciseSpecificationService.class),
                mock(ExerciseService.class), mock(UserRepository.class), mock(QuizBatchRepository.class), mock(ChannelService.class), mock(GroupNotificationScheduleService.class),
                Optional.empty(), Optional.empty(), mock(CompetencyExerciseLinkService.class), Optional.empty(), Optional.empty(), mock(ExerciseConfigurationService.class));
    }

    @AfterEach
    void tearDown() {
        if (originalUploadPath != null) {
            FilePathConverter.setFileUploadPath(originalUploadPath);
        }
    }

    private static MultipartFile image(String filename) {
        return new MockMultipartFile("file", filename, "image/png", "image-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private static DragItem pictureItem(String picture) {
        var item = new DragItem();
        item.setPictureFilePath(picture);
        return item;
    }

    private static QuizExercise quizOf(DragAndDropQuestion question) {
        var quiz = new QuizExercise();
        quiz.setQuizQuestions(List.of(question));
        return quiz;
    }

    @Test
    void handleDndQuizFileCreation_withoutBackground_leavesTheBackgroundUnset() throws Exception {
        var question = new DragAndDropQuestion();

        quizExerciseService.handleDndQuizFileCreation(quizOf(question), null);

        assertThat(question.getBackgroundFilePath()).isNull();
    }

    @Test
    void handleDndQuizFileCreation_withUnknownBackgroundAndProvidedFile_storesTheUpload() throws Exception {
        var question = new DragAndDropQuestion();
        question.setBackgroundFilePath("background.png");

        quizExerciseService.handleDndQuizFileCreation(quizOf(question), List.of(image("background.png")));

        assertThat(question.getBackgroundFilePath()).isNotEqualTo("background.png");
        assertThat(FilePathConverter.getDragAndDropBackgroundFilePath().resolve(question.getBackgroundFilePath())).isRegularFile();
    }

    @Test
    void handleDndQuizFileCreation_withExistingBackground_copiesItToANewFile() throws Exception {
        Files.createDirectories(FilePathConverter.getDragAndDropBackgroundFilePath());
        FileUtils.writeStringToFile(FilePathConverter.getDragAndDropBackgroundFilePath().resolve("existing-background.png").toFile(), "bytes", StandardCharsets.UTF_8);
        var question = new DragAndDropQuestion();
        question.setBackgroundFilePath("existing-background.png");

        quizExerciseService.handleDndQuizFileCreation(quizOf(question), List.of());

        assertThat(question.getBackgroundFilePath()).isNotEqualTo("existing-background.png");
        assertThat(FilePathConverter.getDragAndDropBackgroundFilePath().resolve(question.getBackgroundFilePath())).hasContent("bytes");
        assertThat(FilePathConverter.getDragAndDropBackgroundFilePath().resolve("existing-background.png")).exists();
    }

    @Test
    void uploadNewFilesToNewImportedQuiz_storesMissingFilesAndSkipsQuestionsAndItemsWithoutOne() throws Exception {
        var withFiles = new DragAndDropQuestion();
        withFiles.setBackgroundFilePath("background.png");
        withFiles.addDragItem(pictureItem("picture.png"));

        var withoutFiles = new DragAndDropQuestion();
        var textItem = new DragItem();
        textItem.setText("text only");
        withoutFiles.addDragItem(textItem);

        var quiz = new QuizExercise();
        quiz.setQuizQuestions(List.of(withFiles, withoutFiles));

        quizExerciseService.uploadNewFilesToNewImportedQuiz(quiz, List.of(image("background.png"), image("picture.png")));

        assertThat(withFiles.getBackgroundFilePath()).isNotEqualTo("background.png");
        assertThat(FilePathConverter.getDragAndDropBackgroundFilePath().resolve(withFiles.getBackgroundFilePath())).isRegularFile();
        String storedPicture = withFiles.getDragItems().getFirst().getPictureFilePath();
        assertThat(storedPicture).isNotEqualTo("picture.png");
        assertThat(FilePathConverter.getDragItemFilePath().resolve(storedPicture)).isRegularFile();
        assertThat(withoutFiles.getBackgroundFilePath()).isNull();
        assertThat(textItem.getPictureFilePath()).isNull();
    }

    @Test
    void uploadNewFilesToNewImportedQuiz_keepsFilesThatAlreadyExist() throws Exception {
        Files.createDirectories(FilePathConverter.getDragAndDropBackgroundFilePath());
        Files.createDirectories(FilePathConverter.getDragItemFilePath());
        FileUtils.writeStringToFile(FilePathConverter.getDragAndDropBackgroundFilePath().resolve("existing-background.png").toFile(), "bytes", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(FilePathConverter.getDragItemFilePath().resolve("existing-picture.png").toFile(), "bytes", StandardCharsets.UTF_8);

        var question = new DragAndDropQuestion();
        question.setBackgroundFilePath("existing-background.png");
        question.addDragItem(pictureItem("existing-picture.png"));
        var quiz = new QuizExercise();
        quiz.setQuizQuestions(List.of(question));

        // no uploads are provided: an existing file must not be looked up in them
        quizExerciseService.uploadNewFilesToNewImportedQuiz(quiz, List.of());

        assertThat(question.getBackgroundFilePath()).isEqualTo("existing-background.png");
        assertThat(question.getDragItems().getFirst().getPictureFilePath()).isEqualTo("existing-picture.png");
    }
}
