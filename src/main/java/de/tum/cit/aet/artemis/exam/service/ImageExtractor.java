package de.tum.cit.aet.artemis.exam.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import de.tum.cit.aet.artemis.web.rest.dto.ImageDTO;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;

/**
 * Processor to extract images from a PDF and get information.
 * ref: <a href="https://github.com/apache/pdfbox/blob/trunk/examples/src/main/java/org/apache/pdfbox/examples/util/PrintImageLocations.java">PrintImageLocations.java</a>
 */
public class ImageExtractor extends PDFStreamEngine {

    private final PDDocument pdfDocument;

    private final List<ImageDTO> images;

    private static final String INVOKE_OPERATOR = "Do";

    private int currentPage;

    public ImageExtractor(PDDocument document) {
        this.images = new ArrayList<>();
        this.pdfDocument = document;

        addOperator(new Concatenate(this));
        addOperator(new DrawObject(this));
        addOperator(new SetGraphicsStateParameters(this));
        addOperator(new Save(this));
        addOperator(new Restore(this));
        addOperator(new SetMatrix(this));
    }

    /**
     * process all pages of the pdfDocument
     *
     * @return the object to allow concatenating method calls
     */
    public ImageExtractor process() {
        try {
            currentPage = 0;
            for (PDPage page : pdfDocument.getPages()) {
                currentPage++;
                processPage(page);
            }
        }
        catch (IOException e) {
            throw new InternalServerErrorException("Error while extracting images from PDF");
        }
        return this;
    }

    /**
     * This is used to handle an operation.
     * It will get coordinates of all images in the pdf and store them in a list. This will run for every image in the pdf.
     *
     * @param operator The operation to perform.
     * @param operands The list of arguments.
     * @throws IOException If there is an error processing the operation.
     */
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();
        if (INVOKE_OPERATOR.equals(operation)) {
            COSName objectName = (COSName) operands.getFirst();
            PDXObject pdxObject = getResources().getXObject(objectName);
            if (pdxObject instanceof PDImageXObject image) {

                Matrix matrix = getGraphicsState().getCurrentTransformationMatrix();
                // store the image coordinates, currentPage and the image in bytes
                ImageDTO imageDTO = new ImageDTO(currentPage, matrix.getTranslateX(), matrix.getTranslateY(), image.getHeight(), image.getWidth(), Math.round(matrix.getScaleX()),
                        Math.round(matrix.getScaleY()), toByteArray(image.getImage(), "png"));
                images.add(imageDTO);
            }
            else if (pdxObject instanceof PDFormXObject form) {
                showForm(form);
            }
        }
        else {
            super.processOperator(operator, operands);
        }
    }

    /**
     * @return the images found after invoking {@link #process()} method.
     */
    public List<ImageDTO> getImages() {
        return images;
    }

    /**
     * Converts BufferedImage to byte[]
     *
     * @param bufferedImage the image to be converted
     * @param format        the format of the image (e.g. "png")
     */
    private byte[] toByteArray(BufferedImage bufferedImage, String format) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, format, outputStream);
            return outputStream.toByteArray();
        }
    }
}
