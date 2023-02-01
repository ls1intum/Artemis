package de.tum.in.www1.artemis.service.exam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

import de.tum.in.www1.artemis.web.rest.dto.ExamUserImageDTO;

/**
 * Processor to extract images from a PDF and get information.
 */
public class ImageExtractor extends PDFStreamEngine {

    private final PDDocument pdfDocument;

    private final List<ExamUserImageDTO> images;

    private static final String INVOKE_OPERATOR = "Do";

    private int currentPage;

    public ImageExtractor(PDDocument document) {
        images = new ArrayList<>();
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

                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();

                System.out.println("\nImage [" + objectName.getName() + "]");

                Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();
                float imageXScale = ctmNew.getScalingFactorX();
                float imageYScale = ctmNew.getScalingFactorY();

                // position of image in the pdf in terms of user space units
                System.out.println("position in PDF = " + ctmNew.getTranslateX() + ", " + ctmNew.getTranslateY() + " in user space units");
                // raw size in pixels
                System.out.println("raw image size  = " + imageWidth + ", " + imageHeight + " in pixels");
                // displayed size in user space units
                System.out.println("displayed size  = " + imageXScale + ", " + imageYScale + " in user space units");

                ExamUserImageDTO im = new ExamUserImageDTO();
                im.setImage(image.getImage());
                im.setPage(currentPage);
                im.setXPosition(ctmNew.getTranslateX());
                im.setYPosition(ctmNew.getTranslateY());
                im.setOriginalHeight(image.getHeight());
                im.setOriginalWidth(image.getWidth());
                im.setRenderedWidth(Math.round(ctmNew.getScaleX()));
                im.setRenderedHeight(Math.round(ctmNew.getScaleY()));
                images.add(im);

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
    public List<ExamUserImageDTO> getImages() {
        return images;
    }

}
