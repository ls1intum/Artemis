package de.tum.in.www1.artemis.service;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropMapping;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropSubmittedAnswer;
import de.tum.in.www1.artemis.domain.quiz.DropLocation;

/**
 * Service for converting a DragAndDropSubmittedAnswer to a PDF file displaying the submitted answer.
 */
@Service
public class DragAndDropQuizAnswerConversionService {

    private final FileService fileService;

    // Drop locations in quiz exercises are relatively positioned and sized using integers in the interval [0, 200]
    // this value needs to be consistent with MAX_SIZE_UNIT in quiz-exercise-generator.ts
    private static final int MAX_SIZE_UNIT = 200;

    public DragAndDropQuizAnswerConversionService(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Generates a pdf file of the submitted answer for a drag and drop quiz question.
     *
     * @param dragAndDropSubmittedAnswer the submitted answer
     * @param outputDir                  the directory where the pdf file will be stored
     */
    public void convertDragAndDropQuizAnswerAndStoreAsPdf(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer, Path outputDir) throws IOException {
        DragAndDropQuestion question = (DragAndDropQuestion) dragAndDropSubmittedAnswer.getQuizQuestion();
        String backgroundFilePath = question.getBackgroundFilePath();
        BufferedImage backgroundImage = ImageIO.read(new File(fileService.actualPathForPublicPath(backgroundFilePath)));

        generateDragAndDropSubmittedAnswerImage(backgroundImage, dragAndDropSubmittedAnswer);
        Path dndSubmissionPathPdf = outputDir.resolve(
                "dragAndDropQuestion_" + dragAndDropSubmittedAnswer.getQuizQuestion().getId() + "_submission_" + dragAndDropSubmittedAnswer.getSubmission().getId() + ".pdf");
        storeSubmissionAsPdf(backgroundImage, dndSubmissionPathPdf);
    }

    private void storeSubmissionAsPdf(BufferedImage backgroundImage, Path dndSubmissionPathPdf) throws IOException {
        PDDocument doc = new PDDocument();
        // creates a page in landscape mode, makes the image fit better
        PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
        doc.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, false);
        Dimension scaledDim = getScaledDimension(new Dimension(backgroundImage.getWidth(), backgroundImage.getHeight()),
                new Dimension((int) page.getMediaBox().getWidth(), (int) page.getMediaBox().getHeight()));
        PDImageXObject imageForPdf = LosslessFactory.createFromImage(doc, backgroundImage);
        contentStream.drawImage(imageForPdf, PDRectangle.A4.getLowerLeftX(), PDRectangle.A4.getLowerLeftY(), scaledDim.width, scaledDim.height);
        contentStream.close();
        doc.save(dndSubmissionPathPdf.toFile());
        doc.close();
    }

    private void generateDragAndDropSubmittedAnswerImage(BufferedImage backgroundImage, DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) throws IOException {
        int backgroundImageWidth = backgroundImage.getWidth();
        int backgroundImageHeight = backgroundImage.getHeight();
        DragAndDropQuestion question = (DragAndDropQuestion) dragAndDropSubmittedAnswer.getQuizQuestion();
        Graphics2D graphics = (Graphics2D) backgroundImage.getGraphics();
        graphics.setStroke(new BasicStroke(3));
        List<DropLocation> dropLocations = question.getDropLocations();
        for (var dropLocation : dropLocations) {
            int dropLocationX = (int) (dropLocation.getPosX() / MAX_SIZE_UNIT * backgroundImageWidth);
            int dropLocationY = (int) (dropLocation.getPosY() / MAX_SIZE_UNIT * backgroundImageHeight);
            int dropLocationWidth = (int) (dropLocation.getWidth() / MAX_SIZE_UNIT * backgroundImageWidth);
            int dropLocationHeight = (int) (dropLocation.getHeight() / MAX_SIZE_UNIT * backgroundImageHeight);
            DropLocationCoordinates dropLocationCoordinates = new DropLocationCoordinates(dropLocationX, dropLocationY, dropLocationWidth, dropLocationHeight);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(dropLocationX, dropLocationY, dropLocationWidth, dropLocationHeight);
            if (dropLocation.isDropLocationCorrect(dragAndDropSubmittedAnswer)) {
                graphics.setColor(Color.GREEN);
            }
            else if (dropLocation.isInvalid()) {
                markItemAsInvalid(graphics, dropLocationCoordinates);
            }
            // incorrect solution placed on drop location
            else {
                graphics.setColor(Color.RED);
            }
            graphics.setStroke(new BasicStroke(3));
            graphics.drawRect(dropLocationX, dropLocationY, dropLocationWidth, dropLocationHeight);

            graphics.setColor(Color.BLACK);
            int dropLocationMidY = dropLocationY + dropLocationHeight / 2;
            Set<DragAndDropMapping> mappings = dragAndDropSubmittedAnswer.getMappings();
            for (var mapping : mappings) {
                if (dropLocation.equals(mapping.getDropLocation())) {
                    if (mapping.getDragItem().getPictureFilePath() == null) {
                        graphics.setFont(new Font("Arial", Font.PLAIN, 20));
                        graphics.drawString(mapping.getDragItem().getText(), dropLocationX + 2, dropLocationMidY);
                    }
                    else {
                        BufferedImage dragItem = ImageIO.read(new File(fileService.actualPathForPublicPath(mapping.getDragItem().getPictureFilePath())));
                        Dimension scaledDimForDragItem = getScaledDimension(new Dimension(dragItem.getWidth(), dragItem.getHeight()),
                                new Dimension(dropLocationWidth, dropLocationHeight));
                        graphics.drawImage(dragItem, dropLocationX, dropLocationY, (int) scaledDimForDragItem.getWidth(), (int) scaledDimForDragItem.getHeight(), null);
                    }
                    // if the drop location is invalid, we already marked the spot as invalid, no need to mark it twice
                    if (mapping.getDragItem().isInvalid() && !mapping.getDropLocation().isInvalid()) {
                        markItemAsInvalid(graphics, dropLocationCoordinates);
                    }
                }
            }
        }
        graphics.drawImage(backgroundImage, 0, 0, null);

    }

    private Dimension getScaledDimension(Dimension oldDimension, Dimension newDimension) {
        int oldWith = oldDimension.width;
        int oldHeight = oldDimension.height;
        int newWidth = newDimension.width;
        int newHeight = newDimension.height;
        int tempWidth = oldWith;
        int tempHeight = oldHeight;

        // first check if we need to scale width
        if (oldWith > newWidth) {
            // scale width to fit
            tempWidth = newWidth;
            // scale height to maintain aspect ratio
            tempHeight = (tempWidth * oldHeight) / oldWith;
        }
        // then check if we need to scale even with the new height
        if (tempHeight > newHeight) {
            // scale height to fit instead
            tempHeight = newHeight;
            // scale width to maintain aspect ratio
            tempWidth = (tempHeight * oldWith) / oldHeight;
        }

        return new Dimension(tempWidth, tempHeight);
    }

    private void markItemAsInvalid(Graphics2D graphics, DropLocationCoordinates dropLocationCoordinates) {
        graphics.setColor(Color.DARK_GRAY);
        // create a cross to signal that the drop location is invalid
        Shape diagonalFromBottomToTop = new Line2D.Float(dropLocationCoordinates.x, dropLocationCoordinates.y + dropLocationCoordinates.height,
                dropLocationCoordinates.x + dropLocationCoordinates.width, dropLocationCoordinates.y);
        Shape diagonalFromTopToBottom = new Line2D.Float(dropLocationCoordinates.x, dropLocationCoordinates.y, dropLocationCoordinates.x + dropLocationCoordinates.width,
                dropLocationCoordinates.y + dropLocationCoordinates.height);
        graphics.draw(diagonalFromBottomToTop);
        graphics.draw(diagonalFromTopToBottom);
    }

    // record to pass the values more conveniently to methods
    private record DropLocationCoordinates(int x, int y, int width, int height) {
    }
}
