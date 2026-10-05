package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;

/**
 * Unit tests for the copy of the files of a drag and drop question during a quiz import.
 */
class QuizExerciseImportServiceDragAndDropFilesTest {

    @TempDir
    Path uploadRoot;

    private Path originalUploadPath;

    private QuizExerciseImportService importService;

    @BeforeEach
    void setUp() throws Exception {
        originalUploadPath = FilePathConverter.getFileUploadPath();
        FilePathConverter.setFileUploadPath(uploadRoot);
        Files.createDirectories(FilePathConverter.getDragAndDropBackgroundFilePath());
        Files.createDirectories(FilePathConverter.getDragItemFilePath());
        importService = new QuizExerciseImportService(mock(QuizExerciseService.class), mock(ExampleSubmissionRepository.class), mock(SubmissionRepository.class),
                mock(ResultRepository.class), mock(ChannelService.class), mock(FeedbackService.class), Optional.empty(), mock(TeamAssignmentConfigRepository.class));
    }

    @AfterEach
    void tearDown() {
        if (originalUploadPath != null) {
            FilePathConverter.setFileUploadPath(originalUploadPath);
        }
    }

    private DragAndDropQuestion copy(DragAndDropQuestion original) {
        return ReflectionTestUtils.invokeMethod(importService, "copyDragAndDropQuestion", original);
    }

    private static DragItem pictureItem(String picture) {
        var item = new DragItem();
        item.setPictureFilePath(picture);
        return item;
    }

    @Test
    void copyDragAndDropQuestion_copiesExistingBackgroundAndPictureToNewFiles() throws Exception {
        FileUtils.writeStringToFile(FilePathConverter.getDragAndDropBackgroundFilePath().resolve("background.png").toFile(), "background", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(FilePathConverter.getDragItemFilePath().resolve("picture.png").toFile(), "picture", StandardCharsets.UTF_8);
        var original = new DragAndDropQuestion();
        original.setBackgroundFilePath("background.png");
        original.addDragItem(pictureItem("picture.png"));

        var copy = copy(original);

        assertThat(copy.getBackgroundFilePath()).isNotNull().isNotEqualTo("background.png");
        assertThat(FilePathConverter.getDragAndDropBackgroundFilePath().resolve(copy.getBackgroundFilePath())).hasContent("background");
        String copiedPicture = copy.getDragItems().getFirst().getPictureFilePath();
        assertThat(copiedPicture).isNotNull().isNotEqualTo("picture.png");
        assertThat(FilePathConverter.getDragItemFilePath().resolve(copiedPicture)).hasContent("picture");
        // the original files stay in place for the source exercise
        assertThat(FilePathConverter.getDragAndDropBackgroundFilePath().resolve("background.png")).exists();
        assertThat(FilePathConverter.getDragItemFilePath().resolve("picture.png")).exists();
    }

    @Test
    void copyDragAndDropQuestion_keepsPathsOfFilesThatDoNotExist() {
        var original = new DragAndDropQuestion();
        original.setBackgroundFilePath("missing-background.png");
        original.addDragItem(pictureItem("missing-picture.png"));

        var copy = copy(original);

        assertThat(copy.getBackgroundFilePath()).isEqualTo("missing-background.png");
        assertThat(copy.getDragItems().getFirst().getPictureFilePath()).isEqualTo("missing-picture.png");
    }

    @Test
    void copyDragAndDropQuestion_withoutBackgroundOrPicture_copiesNothing() {
        var original = new DragAndDropQuestion();
        var textItem = new DragItem();
        textItem.setText("text only");
        original.addDragItem(textItem);

        var copy = copy(original);

        assertThat(copy.getBackgroundFilePath()).isNull();
        assertThat(copy.getDragItems().getFirst().getText()).isEqualTo("text only");
        assertThat(copy.getDragItems().getFirst().getPictureFilePath()).isNull();
    }

    @Test
    void copyDragAndDropQuestion_withBackgroundPathOfAnotherDirectory_isReducedToItsFilename() throws Exception {
        FileUtils.writeStringToFile(FilePathConverter.getDragAndDropBackgroundFilePath().resolve("background.png").toFile(), "background", StandardCharsets.UTF_8);
        Path outsideFile = uploadRoot.resolve("secret.png");
        FileUtils.writeStringToFile(outsideFile.toFile(), "secret", StandardCharsets.UTF_8);
        var original = new DragAndDropQuestion();
        original.setBackgroundFilePath("nested/../secret.png");

        var copy = copy(original);

        // only the last path segment is ever resolved, inside the background directory, where no such file exists
        assertThat(copy.getBackgroundFilePath()).isEqualTo("secret.png");
        assertThat(FilePathConverter.getDragAndDropBackgroundFilePath().resolve("secret.png")).doesNotExist();
        assertThat(outsideFile).hasContent("secret");
        assertThat(FilePathConverter.getDragAndDropBackgroundFilePath()).isDirectoryContaining(path -> path.getFileName().toString().equals("background.png"));
    }

    @Test
    void copyDragAndDropQuestion_withPicturePathOfAnotherDirectory_isReducedToItsFilename() throws Exception {
        Path outsideFile = uploadRoot.resolve("secret.png");
        FileUtils.writeStringToFile(outsideFile.toFile(), "secret", StandardCharsets.UTF_8);
        var original = new DragAndDropQuestion();
        original.addDragItem(pictureItem("nested/../secret.png"));

        var copy = copy(original);

        assertThat(copy.getDragItems().getFirst().getPictureFilePath()).isEqualTo("secret.png");
        assertThat(FilePathConverter.getDragItemFilePath().resolve("secret.png")).doesNotExist();
        assertThat(outsideFile).hasContent("secret");
    }
}
