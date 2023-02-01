package de.tum.in.www1.artemis.service.exam;

import java.awt.*;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.web.rest.dto.ExamUserImageDTO;

/**
 * Service Implementation for managing Exam Users.
 */
@Service
public class ExamUserService {

    private final Logger log = LoggerFactory.getLogger(ExamUserService.class);

    // todo write javadoc
    public List<ExamUserImageDTO> parsePDF(MultipartFile file) {

        try (PDDocument document = PDDocument.load(file.getBytes())) {
            ImageExtractor imageExtractor = new ImageExtractor(document);
            imageExtractor.process();
            List<ExamUserImageDTO> images = imageExtractor.getImages();

            // load again as ImageExtractor closes the document
            PDDocument document1 = PDDocument.load(file.getBytes());

            System.out.println(images.size());
            for (ExamUserImageDTO image : images) {
                System.out.println(image.getXPosition());
                System.out.println(image.getYPosition());
                PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                stripper.setSortByPosition(true);
                // todo get the correct rectangle
                Rectangle rect = new Rectangle(Math.round(image.getXPosition()), Math.round(image.getYPosition()) + image.getRenderedHeight(), 2 * image.getOriginalWidth(),
                        2 * image.getRenderedHeight());
                stripper.addRegion("image:" + image.getPage(), rect);
                stripper.extractRegions(document1.getPage(image.getPage() - 1));
                String string = stripper.getTextForRegion("image:" + image.getPage());
                System.out.println(rect.x);
                System.out.println(rect.y);
                System.out.println(rect.height);
                System.out.println(rect.width);
                System.out.println(string);
            }
            document1.close();
            return images;
        }
        catch (IOException e) {
            log.error("Error while parsing PDF file", e);
            e.printStackTrace();
        }
        return null;
    }
}
