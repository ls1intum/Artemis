package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;

/**
 * Unit tests for the validation of the {@link DragAndDropQuizAnswerConversionService}.
 */
class DragAndDropQuizAnswerConversionServiceTest {

    @TempDir
    Path outputDir;

    @TempDir
    Path uploadRoot;

    private Path originalFileUploadPath;

    @BeforeEach
    void setUpUploadPath() {
        // process-wide static, restore it so later integration tests keep resolving uploads against their own root
        originalFileUploadPath = FilePathConverter.getFileUploadPath();
        FilePathConverter.setFileUploadPath(uploadRoot);
    }

    @AfterEach
    void restoreUploadPath() {
        if (originalFileUploadPath != null) {
            FilePathConverter.setFileUploadPath(originalFileUploadPath);
        }
    }

    private static DropLocation dropLocation(long id, double posX, double posY) {
        var dropLocation = new DropLocation();
        dropLocation.setId(id);
        dropLocation.setPosX(posX);
        dropLocation.setPosY(posY);
        dropLocation.setWidth(60.0);
        dropLocation.setHeight(60.0);
        return dropLocation;
    }

    private static DragAndDropMapping mapping(DragItem dragItem, DropLocation dropLocation) {
        var mapping = new DragAndDropMapping();
        mapping.setDragItem(dragItem);
        mapping.setDropLocation(dropLocation);
        return mapping;
    }

    @Test
    void convertDragAndDropQuizAnswerAndStoreAsPdf_withPictureTextAndInvalidItems_writesPdf() throws IOException {
        Path backgrounds = FilePathConverter.getDragAndDropBackgroundFilePath();
        Path dragItems = FilePathConverter.getDragItemFilePath();
        Files.createDirectories(backgrounds);
        Files.createDirectories(dragItems);
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", backgrounds.resolve("background.png").toFile());
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", dragItems.resolve("item.png").toFile());

        var pictureItem = new DragItem();
        pictureItem.setId(1L);
        pictureItem.setPictureFilePath("item.png");
        // an invalid item on a valid drop location is crossed out
        pictureItem.setInvalid(true);
        var textItem = new DragItem();
        textItem.setId(2L);
        textItem.setText("text item");
        var invalidLocation = dropLocation(12L, 120.0, 120.0);
        invalidLocation.setInvalid(true);
        var pictureLocation = dropLocation(10L, 10.0, 10.0);
        var textLocation = dropLocation(11L, 10.0, 100.0);

        var question = new DragAndDropQuestion();
        question.setId(7L);
        question.setBackgroundFilePath("background.png");
        question.setDropLocations(List.of(pictureLocation, textLocation, invalidLocation));
        question.setDragItems(List.of(pictureItem, textItem));

        var submission = new QuizSubmission();
        submission.setId(9L);
        var answer = new DragAndDropSubmittedAnswer();
        answer.setQuizQuestion(question);
        answer.setSubmission(submission);
        answer.addMappings(mapping(pictureItem, pictureLocation));
        answer.addMappings(mapping(textItem, textLocation));

        new DragAndDropQuizAnswerConversionService().convertDragAndDropQuizAnswerAndStoreAsPdf(answer, outputDir, true);

        assertThat(outputDir.resolve("dragAndDropQuestion_7_submission_9.pdf")).isNotEmptyFile();
    }

    @Test
    void convertDragAndDropQuizAnswerAndStoreAsPdf_forAQuestionWithoutBackground_isRejected() {
        var question = new DragAndDropQuestion();
        question.setId(5L);
        var answer = new DragAndDropSubmittedAnswer();
        answer.setQuizQuestion(question);

        var service = new DragAndDropQuizAnswerConversionService();

        assertThatExceptionOfType(IOException.class).isThrownBy(() -> service.convertDragAndDropQuizAnswerAndStoreAsPdf(answer, outputDir, true)).withMessageContaining("5")
                .withMessageContaining("no background image");
    }
}
