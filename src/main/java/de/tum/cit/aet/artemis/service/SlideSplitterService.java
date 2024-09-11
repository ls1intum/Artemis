package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.domain.lecture.AttachmentUnit;
import de.tum.cit.aet.artemis.domain.lecture.Slide;
import de.tum.cit.aet.artemis.repository.SlideRepository;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service Implementation for managing the split of AttachmentUnit into single slides and save them as PNG.
 */
@Profile(PROFILE_CORE)
@Service
public class SlideSplitterService {

    private static final Logger log = LoggerFactory.getLogger(SlideSplitterService.class);

    private final FileService fileService;

    private final SlideRepository slideRepository;

    public SlideSplitterService(FileService fileService, SlideRepository slideRepository) {
        this.fileService = fileService;
        this.slideRepository = slideRepository;
    }

    /**
     * Splits an Attachment Unit file into single slides and saves them as PNG files asynchronously.
     *
     * @param attachmentUnit The attachment unit to which the slides belong.
     */
    @Async
    public void splitAttachmentUnitIntoSingleSlides(AttachmentUnit attachmentUnit) {
        Path attachmentPath = FilePathService.actualPathForPublicPath(URI.create(attachmentUnit.getAttachment().getLink()));
        File file = attachmentPath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            String pdfFilename = file.getName();
            splitAttachmentUnitIntoSingleSlides(document, attachmentUnit, pdfFilename);
        }
        catch (IOException e) {
            log.error("Error while splitting Attachment Unit {} into single slides", attachmentUnit.getId(), e);
            throw new InternalServerErrorException("Could not split Attachment Unit into single slides: " + e.getMessage());
        }
    }

    /**
     * Splits an Attachment Unit file into single slides and saves them as PNG files. Document is closed where this method is called.
     *
     * @param attachmentUnit The attachment unit to which the slides belong.
     * @param document       The PDF document that is already loaded.
     * @param pdfFilename    The name of the PDF file.
     */
    public void splitAttachmentUnitIntoSingleSlides(PDDocument document, AttachmentUnit attachmentUnit, String pdfFilename) {
        log.debug("Splitting Attachment Unit file {} into single slides", attachmentUnit.getAttachment().getName());
        try {
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);
            int numPages = document.getNumberOfPages();
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < numPages; page++) {
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 72, ImageType.RGB);
                byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
                int slideNumber = page + 1;
                String filename = fileNameWithOutExt + "_" + attachmentUnit.getId() + "_Slide_" + slideNumber + ".png";
                MultipartFile slideFile = fileService.convertByteArrayToMultipart(filename, ".png", imageInByte);
                Path savePath = fileService.saveFile(slideFile, FilePathService.getAttachmentUnitFilePath().resolve(attachmentUnit.getId().toString()).resolve("slide")
                        .resolve(String.valueOf(slideNumber)).resolve(filename));
                Slide slideEntity = new Slide();
                slideEntity.setSlideImagePath(FilePathService.publicPathForActualPath(savePath, (long) slideNumber).toString());
                slideEntity.setSlideNumber(slideNumber);
                slideEntity.setAttachmentUnit(attachmentUnit);
                slideRepository.save(slideEntity);
            }
        }
        catch (IOException e) {
            log.error("Error while splitting Attachment Unit {} into single slides", attachmentUnit.getId(), e);
            throw new InternalServerErrorException("Could not split Attachment Unit into single slides: " + e.getMessage());
        }
    }

    /**
     * Converts BufferedImage to byte[]
     *
     * @param bufferedImage the image to convert
     * @param format        the format of the image (e.g. png)
     */
    private byte[] bufferedImageToByteArray(BufferedImage bufferedImage, String format) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, format, outputStream);
            return outputStream.toByteArray();
        }
    }
}
