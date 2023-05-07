package de.tum.in.www1.artemis.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropMapping;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropSubmittedAnswer;

@Service
public class DragAndDropQuizAnswerConversionService {

    private final FileService fileService;

    public DragAndDropQuizAnswerConversionService(FileService fileService) {
        this.fileService = fileService;
    }

    public void convertDragAndDropQuizAnswerToImage(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) throws IOException {
        DragAndDropQuestion question = (DragAndDropQuestion) dragAndDropSubmittedAnswer.getQuizQuestion();
        String backgroundFilePath = question.getBackgroundFilePath();
        BufferedImage backgroundImage = ImageIO.read(new File(fileService.actualPathForPublicPath(backgroundFilePath)));
        Graphics2D g = (Graphics2D) backgroundImage.getGraphics();
        g.setStroke(new BasicStroke(3));
        g.setColor(Color.BLUE);
        Set<DragAndDropMapping> mappings = dragAndDropSubmittedAnswer.getMappings();
        for (DragAndDropMapping mapping : mappings) {
            if (mapping.getDropLocation().isDropLocationCorrect(dragAndDropSubmittedAnswer)) {
                g.setColor(Color.GREEN);
            }
            else {
                g.setColor(Color.RED);
            }
            g.drawRect(mapping.getDropLocation().getPosX().intValue() * 3, mapping.getDropLocation().getPosY().intValue() * 3, mapping.getDropLocation().getWidth().intValue() * 3,
                    mapping.getDropLocation().getHeight().intValue() * 3);
            g.setColor(Color.BLACK);
            if (mapping.getDragItem().getPictureFilePath() == null) {
                g.drawString(mapping.getDragItem().getText(), mapping.getDropLocation().getPosX().intValue() * 3 + 10, mapping.getDropLocation().getPosY().intValue() * 3 + 10);
            }
            else {
                BufferedImage dragItem = ImageIO.read(new File(fileService.actualPathForPublicPath(mapping.getDragItem().getPictureFilePath())));
                g.drawImage(dragItem, mapping.getDropLocation().getPosX().intValue(), mapping.getDropLocation().getPosY().intValue(), null);
            }
        }
        g.drawImage(backgroundImage, 0, 0, null);
        ImageIO.write(backgroundImage, "PNG", new File("dragAndDropQuestionSubmission.png"));

    }
}
