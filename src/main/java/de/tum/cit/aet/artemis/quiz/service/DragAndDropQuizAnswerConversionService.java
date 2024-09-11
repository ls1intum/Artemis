package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.service.FilePathService;

/**
 * Service for converting a DragAndDropSubmittedAnswer to a PDF file displaying the submitted answer.
 */
@Profile(PROFILE_CORE)
@Service
public class DragAndDropQuizAnswerConversionService {

    // Drop locations in quiz exercises are relatively positioned and sized using integers in the interval [0, 200]
    // this value needs to be consistent with MAX_SIZE_UNIT in quiz-exercise-generator.ts
    private static final int MAX_SIZE_UNIT = 200;

    /**
     * Generates a pdf file of the submitted answer for a drag and drop quiz question.
     *
     * @param dragAndDropSubmittedAnswer the submitted answer
     * @param outputDir                  the directory where the pdf file will be stored
     * @param showResult                 whether the result of the quiz question submission should be shown
     */
    public void convertDragAndDropQuizAnswerAndStoreAsPdf(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer, Path outputDir, boolean showResult) throws IOException {
        DragAndDropQuestion question = (DragAndDropQuestion) dragAndDropSubmittedAnswer.getQuizQuestion();
        String backgroundFilePath = question.getBackgroundFilePath();
        BufferedImage backgroundImage = ImageIO.read(FilePathService.actualPathForPublicPath(URI.create(backgroundFilePath)).toFile());

        generateDragAndDropSubmittedAnswerImage(backgroundImage, dragAndDropSubmittedAnswer, showResult);
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

    private void generateDragAndDropSubmittedAnswerImage(BufferedImage backgroundImage, DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer, boolean showResult)
            throws IOException {
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
            drawDropLocation(dragAndDropSubmittedAnswer, graphics, dropLocation, dropLocationCoordinates, showResult);
            drawDragItem(dragAndDropSubmittedAnswer, graphics, dropLocation, dropLocationCoordinates);
        }
        graphics.drawImage(backgroundImage, 0, 0, null);

    }

    private void drawDragItem(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer, Graphics2D graphics, DropLocation dropLocation,
            DropLocationCoordinates dropLocationCoordinates) throws IOException {
        graphics.setColor(Color.BLACK);
        int dropLocationMidY = dropLocationCoordinates.y + dropLocationCoordinates.height / 2;
        Set<DragAndDropMapping> mappings = dragAndDropSubmittedAnswer.getMappings();
        for (var mapping : mappings) {
            if (dropLocation.equals(mapping.getDropLocation())) {
                if (mapping.getDragItem().getPictureFilePath() == null) {
                    drawTextDragItem(graphics, dropLocationCoordinates, dropLocationMidY, mapping);
                }
                else {
                    drawPictureDragItem(graphics, dropLocationCoordinates, mapping);
                }
                // if the drop location is invalid, we already marked the spot as invalid, no need to mark it twice
                if (mapping.getDragItem().isInvalid() && !mapping.getDropLocation().isInvalid()) {
                    markItemAsInvalid(graphics, dropLocationCoordinates);
                }
            }
        }
    }

    private void drawTextDragItem(Graphics2D graphics, DropLocationCoordinates dropLocationCoordinates, int dropLocationMidY, DragAndDropMapping mapping) {
        graphics.setFont(new Font("Arial", Font.PLAIN, 20));
        graphics.drawString(mapping.getDragItem().getText(), dropLocationCoordinates.x + 5, dropLocationMidY);
    }

    private void drawPictureDragItem(Graphics2D graphics, DropLocationCoordinates dropLocationCoordinates, DragAndDropMapping mapping) throws IOException {
        BufferedImage dragItem = ImageIO.read(FilePathService.actualPathForPublicPath(URI.create(mapping.getDragItem().getPictureFilePath())).toFile());
        Dimension scaledDimForDragItem = getScaledDimension(new Dimension(dragItem.getWidth(), dragItem.getHeight()),
                new Dimension(dropLocationCoordinates.width, dropLocationCoordinates.height));
        graphics.drawImage(dragItem, dropLocationCoordinates.x, dropLocationCoordinates.y, (int) scaledDimForDragItem.getWidth(), (int) scaledDimForDragItem.getHeight(), null);
    }

    private void drawDropLocation(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer, Graphics2D graphics, DropLocation dropLocation,
            DropLocationCoordinates dropLocationCoordinates, boolean showResult) {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(dropLocationCoordinates.x, dropLocationCoordinates.y, dropLocationCoordinates.width, dropLocationCoordinates.height);

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

        if (!showResult) {
            graphics.setColor(Color.BLACK);
        }
        graphics.setStroke(new BasicStroke(3));
        graphics.drawRect(dropLocationCoordinates.x, dropLocationCoordinates.y, dropLocationCoordinates.width, dropLocationCoordinates.height);
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
