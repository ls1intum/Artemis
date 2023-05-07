package de.tum.in.www1.artemis.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropMapping;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropSubmittedAnswer;

@Service
public class DragAndDropQuizAnswerConversionService {

    private final FileService fileService;
    // Drop locations in quiz exercises are relatively positioned and sized using integers in the interval [0, 200]
    // this value needs to be consistent with MAX_SIZE_UNIT in quiz-exercise-generator.ts

    private static final int MAX_SIZE_UNIT = 200;

    public DragAndDropQuizAnswerConversionService(FileService fileService) {
        this.fileService = fileService;
    }

    public void convertDragAndDropQuizAnswerToImage(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer, Path outputDir) throws IOException {

        // TODO invalid drop locations
        // TODO drop location but no drag item on it
        DragAndDropQuestion question = (DragAndDropQuestion) dragAndDropSubmittedAnswer.getQuizQuestion();
        String backgroundFilePath = question.getBackgroundFilePath();
        BufferedImage backgroundImage = ImageIO.read(new File(fileService.actualPathForPublicPath(backgroundFilePath)));
        int backgroundImageWidth = backgroundImage.getWidth();
        int backgroundImageHeight = backgroundImage.getHeight();
        Graphics2D g = (Graphics2D) backgroundImage.getGraphics();
        g.setStroke(new BasicStroke(10));
        g.setColor(Color.BLUE);
        Set<DragAndDropMapping> mappings = dragAndDropSubmittedAnswer.getMappings();
        for (DragAndDropMapping mapping : mappings) {
            if (mapping.getDropLocation().isDropLocationCorrect(dragAndDropSubmittedAnswer)) {
                g.setColor(Color.GREEN);
            }
            else {
                g.setColor(Color.RED);
            }
            int dropLocationX = (int) (mapping.getDropLocation().getPosX() / MAX_SIZE_UNIT * backgroundImageWidth);
            int dropLocationY = (int) (mapping.getDropLocation().getPosY() / MAX_SIZE_UNIT * backgroundImageHeight);
            int dropLocationWidth = (int) (mapping.getDropLocation().getWidth() / MAX_SIZE_UNIT * backgroundImageWidth);
            int dropLocationHeight = (int) (mapping.getDropLocation().getHeight() / MAX_SIZE_UNIT * backgroundImageHeight);
            g.drawRect(dropLocationX, dropLocationY, dropLocationWidth, dropLocationHeight);
            g.setColor(Color.WHITE);
            g.fillRect(dropLocationX, dropLocationY, dropLocationWidth, dropLocationHeight);
            g.setColor(Color.BLACK);
            int dropLocationMidX = (int) (mapping.getDropLocation().getPosX() / MAX_SIZE_UNIT * backgroundImageWidth
                    + mapping.getDropLocation().getWidth() / MAX_SIZE_UNIT * backgroundImageWidth / 2);
            int dropLocationMidY = (int) (mapping.getDropLocation().getPosY() / MAX_SIZE_UNIT * backgroundImageHeight
                    + mapping.getDropLocation().getHeight() / MAX_SIZE_UNIT * backgroundImageHeight / 2);

            if (mapping.getDragItem().getPictureFilePath() == null) {
                g.setFont(new Font("Arial", Font.PLAIN, 20));
                g.drawString(mapping.getDragItem().getText(), dropLocationMidX, dropLocationMidY);
            }
            else {
                BufferedImage dragItem = ImageIO.read(new File(fileService.actualPathForPublicPath(mapping.getDragItem().getPictureFilePath())));
                g.drawImage(dragItem, (int) (mapping.getDropLocation().getPosX() / MAX_SIZE_UNIT * backgroundImageWidth),
                        (int) (mapping.getDropLocation().getPosY() / MAX_SIZE_UNIT * backgroundImageHeight), null);
            }
        }
        g.drawImage(backgroundImage, 0, 0, null);
        Path dndSubmissionPath = outputDir.resolve(
                "dragAndDropQuestion_" + dragAndDropSubmittedAnswer.getQuizQuestion().getId() + "submission_" + dragAndDropSubmittedAnswer.getSubmission().getId() + ".png");
        ImageIO.write(backgroundImage, "PNG", dndSubmissionPath.toFile());

    }
}
