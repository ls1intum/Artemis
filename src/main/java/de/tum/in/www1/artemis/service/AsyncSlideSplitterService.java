package de.tum.in.www1.artemis.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.Slide;
import de.tum.in.www1.artemis.repository.SlideRepository;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service Implementation for managing the split of AttachmentUnit into single slides and save them as PNG.
 */
@Service
public class AsyncSlideSplitterService {

    private final Logger log = LoggerFactory.getLogger(AsyncSlideSplitterService.class);

    private final FileService fileService;

    private final SlideRepository slideRepository;

    public AsyncSlideSplitterService(FileService fileService, SlideRepository slideRepository) {
        this.fileService = fileService;
        this.slideRepository = slideRepository;
    }

    /**
     * Splits an Attachment Unit file into single slides and saves them as PNG files.
     *
     * @param file           The PDF file to split.
     * @param attachmentUnit The attachment unit to which the slides belong.
     */
    @Async
    public void splitAttachmentUnitIntoSingleSlides(MultipartFile file, AttachmentUnit attachmentUnit) {
        log.debug("Splitting Attachment Unit file {} into single slides", file);
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            String pdfFilename = file.getOriginalFilename();
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);
            int numPages = document.getNumberOfPages();
            List<CompletableFuture<Slide>> slideFutures = new ArrayList<>();
            for (int page = 0; page < numPages; ++page) {
                int finalPage = page;
                CompletableFuture<Slide> slideFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        // this needs to be done in a separate thread, otherwise the PDFBox library will throw an IllegalStateException
                        // when trying to render the image. This causes to not render some pages at all.
                        // (e.g. Possible recursion found when searching for page)
                        PDFRenderer pdfRenderer = new PDFRenderer(document);

                        BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(finalPage, 96, ImageType.RGB);
                        byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
                        MultipartFile slideFile = fileService.convertByteArrayToMultipart(fileNameWithOutExt + "-SLIDE-" + (finalPage + 1), ".png", imageInByte);
                        String filePath = fileService.handleSaveFile(slideFile, true, false);
                        Slide slideEntity = new Slide();
                        slideEntity.setSlideImagePath(filePath);
                        slideEntity.setSlideNumber(finalPage + 1);
                        slideEntity.setAttachmentUnit(attachmentUnit);
                        return slideRepository.save(slideEntity);
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                slideFutures.add(slideFuture);
            }

            CompletableFuture<Void> allSlidesFuture = CompletableFuture.allOf(slideFutures.toArray(new CompletableFuture[0]));
            allSlidesFuture.join();
        }
        catch (IOException e) {
            log.error("Error while splitting Attachment Unit into single slides", e);
            throw new InternalServerErrorException("Could not split Attachment Unit into single slides");
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
