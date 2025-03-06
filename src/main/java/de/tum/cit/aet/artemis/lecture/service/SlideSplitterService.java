package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Service Implementation for managing the split of AttachmentUnit into single slides and save them as PNG.
 */
@Profile(PROFILE_CORE)
@Service
public class SlideSplitterService {

    private static final Logger log = LoggerFactory.getLogger(SlideSplitterService.class);

    private final FileService fileService;

    private final SlideRepository slideRepository;

    private final SlideUnhideService slideUnhideService;

    public SlideSplitterService(FileService fileService, SlideRepository slideRepository, SlideUnhideService slideUnhideService) {
        this.fileService = fileService;
        this.slideRepository = slideRepository;
        this.slideUnhideService = slideUnhideService;
    }

    /**
     * Splits an Attachment Unit file into single slides and saves them as PNG files asynchronously.
     *
     * @param attachmentUnit The attachment unit to which the slides belong.
     */
    @Async
    public void splitAttachmentUnitIntoSingleSlides(AttachmentUnit attachmentUnit) {
        splitAttachmentUnitIntoSingleSlides(attachmentUnit, null);
    }

    /**
     * Splits an Attachment Unit file into single slides and saves them as PNG files asynchronously.
     *
     * @param attachmentUnit The attachment unit to which the slides belong.
     * @param hiddenPages    The hidden pages of the attachment unit.
     */
    @Async
    public void splitAttachmentUnitIntoSingleSlides(AttachmentUnit attachmentUnit, String hiddenPages) {
        Path attachmentPath = FilePathService.actualPathForPublicPath(URI.create(attachmentUnit.getAttachment().getLink()));
        File file = attachmentPath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            String pdfFilename = file.getName();
            splitAttachmentUnitIntoSingleSlides(document, attachmentUnit, pdfFilename, hiddenPages);
        }
        catch (IOException e) {
            log.error("Error while splitting Attachment Unit {} into single slides", attachmentUnit.getId(), e);
            throw new InternalServerErrorException("Could not split Attachment Unit into single slides: " + e.getMessage());
        }
    }

    /**
     * Splits an Attachment Unit file into single slides and saves them as PNG files or updates existing slides.
     *
     * @param attachmentUnit The attachment unit to which the slides belong.
     * @param document       The PDF document that is already loaded.
     * @param pdfFilename    The name of the PDF file.
     */
    public void splitAttachmentUnitIntoSingleSlides(PDDocument document, AttachmentUnit attachmentUnit, String pdfFilename) {
        splitAttachmentUnitIntoSingleSlides(document, attachmentUnit, pdfFilename, null);
    }

    /**
     * Splits an Attachment Unit file into single slides and saves them as PNG files or updates existing slides.
     *
     * @param attachmentUnit The attachment unit to which the slides belong.
     * @param document       The PDF document that is already loaded.
     * @param pdfFilename    The name of the PDF file.
     * @param hiddenPages    The hidden pages of the attachment unit.
     */
    public void splitAttachmentUnitIntoSingleSlides(PDDocument document, AttachmentUnit attachmentUnit, String pdfFilename, String hiddenPages) {
        log.debug("Splitting Attachment Unit file {} into single slides", attachmentUnit.getAttachment().getName());
        try {
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);
            int numPages = document.getNumberOfPages();
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            Map<Integer, java.sql.Timestamp> hiddenPagesMap = hiddenPages != null ? new ObjectMapper().readValue(hiddenPages, new TypeReference<List<Map<String, Object>>>() {
            }).stream().collect(Collectors.toMap(map -> (Integer) map.get("pageIndex"), map -> {
                String dateStr = (String) map.get("date");
                return Timestamp.from(Instant.parse(dateStr));
            })) : Collections.emptyMap();

            for (int page = 0; page < numPages; page++) {
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 72, ImageType.RGB);
                byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
                int slideNumber = page + 1;
                String filename = fileNameWithOutExt + "_" + attachmentUnit.getId() + "_Slide_" + slideNumber + ".png";
                MultipartFile slideFile = fileService.convertByteArrayToMultipart(filename, ".png", imageInByte);
                Path savePath = fileService.saveFile(slideFile, FilePathService.getAttachmentUnitFilePath().resolve(attachmentUnit.getId().toString()).resolve("slide")
                        .resolve(String.valueOf(slideNumber)).resolve(filename));

                Optional<Slide> existingSlideOpt = Optional.ofNullable(slideRepository.findSlideByAttachmentUnitIdAndSlideNumber(attachmentUnit.getId(), slideNumber));
                Slide slide = existingSlideOpt.orElseGet(Slide::new);
                slide.setSlideImagePath(FilePathService.publicPathForActualPath(savePath, (long) slideNumber).toString());
                slide.setSlideNumber(slideNumber);
                slide.setAttachmentUnit(attachmentUnit);

                // Check if the hidden status is changing
                java.util.Date previousHiddenValue = slide.getHidden();
                java.util.Date newHiddenValue = hiddenPagesMap.getOrDefault(slideNumber, null);
                slide.setHidden(newHiddenValue);

                Slide savedSlide = slideRepository.save(slide);

                // Schedule unhiding if the hidden value is set or has changed
                if (previousHiddenValue != newHiddenValue) {
                    slideUnhideService.handleSlideHiddenUpdate(savedSlide);
                    log.debug("Scheduled unhiding for slide ID {} at time {}", savedSlide.getId(), newHiddenValue);
                }
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
