package de.tum.in.www1.artemis.service.exam;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.*;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import de.tum.in.www1.artemis.web.rest.dto.ImageDTO;

/**
 * Processor to extract images from a PDF and get information.
 * ref: <a href="https://github.com/apache/pdfbox/blob/trunk/examples/src/main/java/org/apache/pdfbox/examples/util/PrintImageLocations.java">...</a>
 */
public class ImageExtractor extends PDFStreamEngine {

    private final PDDocument pdfDocument;

    private final List<ImageDTO> images;

    private static final String INVOKE_OPERATOR = "Do";

    private int currentPage;

    public ImageExtractor(PDDocument document) {
        this.images = new ArrayList<>();
        this.pdfDocument = document;

        addOperator(new Concatenate());
        addOperator(new DrawObject());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new Save());
        addOperator(new Restore());
        addOperator(new SetMatrix());
    }

    public ImageExtractor process() {
        try {
            currentPage = 0;
            for (PDPage page : pdfDocument.getPages()) {
                currentPage++;
                processPage(page);
            }

            pdfDocument.close();
        }
        catch (IOException e) {
        }
        return this;
    }

    /**
     * This is used to handle an operation.
     *
     * @param operator The operation to perform.
     * @param operands The list of arguments.
     * @throws IOException If there is an error processing the operation.
     */
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();
        if (INVOKE_OPERATOR.equals(operation)) {
            COSName objectName = (COSName) operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);
            if (xobject instanceof PDImageXObject) {

                PDImageXObject image = (PDImageXObject) xobject;
                Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();
                ImageDTO imageDTO = new ImageDTO(currentPage, ctmNew.getTranslateX(), ctmNew.getTranslateY(), image.getHeight(), image.getWidth(), Math.round(ctmNew.getScaleX()),
                        Math.round(ctmNew.getScaleY()), toByteArray(image.getImage(), "png"));
                images.add(imageDTO);

            }
            else if (xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject) xobject;
                showForm(form);
            }
        }
        else {
            super.processOperator(operator, operands);
        }
    }

    /**
     * Returns the images found after invoking {@link #process()}  method.
     */
    public List<ImageDTO> getImages() {
        return images;
    }

    /**
     * Converts BufferedImage to byte[]
     */
    private byte[] toByteArray(BufferedImage bi, String format) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bi, format, baos);
        byte[] bytes = baos.toByteArray();
        return bytes;
    }
}
