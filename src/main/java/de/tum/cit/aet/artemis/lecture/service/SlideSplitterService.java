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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
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

    private final ExerciseRepository exerciseRepository;

    public SlideSplitterService(FileService fileService, SlideRepository slideRepository, SlideUnhideService slideUnhideService, ExerciseRepository exerciseRepository) {
        this.fileService = fileService;
        this.slideRepository = slideRepository;
        this.slideUnhideService = slideUnhideService;
        this.exerciseRepository = exerciseRepository;
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
     * Splits an Attachment Unit file into single slides and saves them as PNG files asynchronously.
     *
     * @param attachmentUnit The attachment unit to which the slides belong.
     * @param hiddenPages    The hidden pages of the attachment unit.
     * @param pageOrder      The page order of the attachment unit.
     */
    @Async
    public void splitAttachmentUnitIntoSingleSlides(AttachmentUnit attachmentUnit, String hiddenPages, String pageOrder) {
        Path attachmentPath = FilePathService.actualPathForPublicPath(URI.create(attachmentUnit.getAttachment().getLink()));
        File file = attachmentPath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            String pdfFilename = file.getName();
            splitAttachmentUnitIntoSingleSlides(document, attachmentUnit, pdfFilename, hiddenPages, pageOrder);
        }
        catch (IOException e) {
            log.error("Error while splitting Attachment Unit {} into single slides", attachmentUnit.getId(), e);
            throw new InternalServerErrorException("Could not split Attachment Unit into single slides: " + e.getMessage());
        }
    }

    /**
     * Splits an Attachment Unit file into single slides and saves them as PNG files.
     * Only creates new slides; never updates existing ones to keep slide referencing.
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
                slideEntity.setSlideImagePath(FilePathService.publicPathForActualPathOrThrow(savePath, (long) slideNumber).toString());
                slideEntity.setSlideNumber(slideNumber);
                slideEntity.setAttachmentUnit(attachmentUnit);
                slideEntity.setHidden(null);
                slideEntity.setExercise(null);
                slideRepository.save(slideEntity);
            }
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
     * @param hiddenPages    The hidden pages information.
     * @param pageOrder      The order of pages in the PDF.
     */
    public void splitAttachmentUnitIntoSingleSlides(PDDocument document, AttachmentUnit attachmentUnit, String pdfFilename, String hiddenPages, String pageOrder) {
        log.debug("Processing slides for Attachment Unit with hidden pages {}", attachmentUnit.getAttachment().getName());

        try {
            // Parse the page order and hidden pages information
            List<Map<String, Object>> pageOrderList = parsePageOrder(pageOrder);
            Map<String, Map<String, Object>> hiddenPagesMap = parseHiddenPages(hiddenPages);

            // Retrieve existing slides
            List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(attachmentUnit.getId());
            Map<String, Slide> existingSlidesMap = existingSlides.stream().collect(Collectors.toMap(slide -> String.valueOf(slide.getId()), slide -> slide));

            // Initialize PDF renderer and filename
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);

            // Process each slide in the page order
            for (Map<String, Object> page : pageOrderList) {
                processSlide(page, attachmentUnit, existingSlidesMap, hiddenPagesMap, pdfRenderer, fileNameWithOutExt, document.getNumberOfPages());
            }

            // Clean up slides that are no longer in the page order
            cleanupRemovedSlides(pageOrderList, existingSlides);
        }
        catch (IOException e) {
            log.error("Error while splitting Attachment Unit {} into single slides", attachmentUnit.getId(), e);
            throw new InternalServerErrorException("Could not split Attachment Unit into single slides: " + e.getMessage());
        }
    }

    /**
     * Parse the page order JSON string into a list of maps.
     */
    private List<Map<String, Object>> parsePageOrder(String pageOrder) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(pageOrder, new TypeReference<>() {
        });
    }

    /**
     * Parse the hidden pages JSON string into a map of slide ID to hidden page data.
     */
    private Map<String, Map<String, Object>> parseHiddenPages(String hiddenPages) {
        Map<String, Map<String, Object>> hiddenPagesMap = new HashMap<>();

        if (hiddenPages != null && !hiddenPages.isEmpty()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, Object>> hiddenPagesList = objectMapper.readValue(hiddenPages, new TypeReference<>() {
                });

                hiddenPagesMap = hiddenPagesList.stream().collect(Collectors.toMap(page -> String.valueOf(page.get("slideId")), page -> {
                    Map<String, Object> data = new HashMap<>();
                    String dateStr = (String) page.get("date");
                    data.put("date", Timestamp.from(Instant.parse(dateStr)));

                    if (page.get("exerciseId") != null) {
                        data.put("exerciseId", page.get("exerciseId"));
                    }
                    return data;
                }));
            }
            catch (Exception e) {
                log.warn("Failed to parse hidden pages data: {}", e.getMessage());
            }
        }

        return hiddenPagesMap;
    }

    /**
     * Process a single slide in the page order.
     */
    private void processSlide(Map<String, Object> page, AttachmentUnit attachmentUnit, Map<String, Slide> existingSlidesMap, Map<String, Map<String, Object>> hiddenPagesMap,
            PDFRenderer pdfRenderer, String fileNameWithOutExt, int totalPages) throws IOException {
        String slideId = String.valueOf(page.get("slideId"));
        int order = ((Number) page.get("order")).intValue();

        Slide slideEntity;
        boolean isNewSlide = false;

        // Determine if this is a new slide or an existing one
        if (slideId.startsWith("temp_") || !existingSlidesMap.containsKey(slideId)) {
            isNewSlide = true;
            slideEntity = new Slide();
            slideEntity.setAttachmentUnit(attachmentUnit);
        }
        else {
            slideEntity = existingSlidesMap.get(slideId);
        }

        slideEntity.setSlideNumber(order);

        // Handle hidden status and associated exercise
        java.util.Date previousHiddenValue = updateSlideHiddenStatus(slideEntity, hiddenPagesMap.get(slideId));

        // Handle slide image
        if (isNewSlide) {
            createNewSlideImage(slideEntity, pdfRenderer, fileNameWithOutExt, attachmentUnit, order, totalPages);
        }
        else {
            updateExistingSlideImage(slideEntity, fileNameWithOutExt, attachmentUnit, order);
        }

        // Save slide and schedule unhiding if needed
        Slide savedSlide = slideRepository.save(slideEntity);
        scheduleUnhideIfNeeded(savedSlide, previousHiddenValue, slideEntity.getHidden());
    }

    /**
     * Update the hidden status and associated exercise for a slide.
     *
     * @return The previous hidden value
     */
    private java.util.Date updateSlideHiddenStatus(Slide slideEntity, Map<String, Object> hiddenData) {
        java.util.Date previousHiddenValue = slideEntity.getHidden();

        if (hiddenData != null && hiddenData.containsKey("date")) {
            slideEntity.setHidden((java.util.Date) hiddenData.get("date"));

            if (hiddenData.containsKey("exerciseId") && hiddenData.get("exerciseId") != null) {
                Number exerciseId = (Number) hiddenData.get("exerciseId");
                Optional<Exercise> exercise = exerciseRepository.findById(exerciseId.longValue());
                exercise.ifPresent(slideEntity::setExercise);
            }
            else {
                slideEntity.setExercise(null);
            }
        }
        else {
            slideEntity.setHidden(null);
            slideEntity.setExercise(null);
        }

        return previousHiddenValue;
    }

    /**
     * Create image for a new slide.
     */
    private void createNewSlideImage(Slide slideEntity, PDFRenderer pdfRenderer, String fileNameWithOutExt, AttachmentUnit attachmentUnit, int order, int totalPages)
            throws IOException {
        int pdfPageIndex = order - 1;
        if (pdfPageIndex >= 0 && pdfPageIndex < totalPages) {
            BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pdfPageIndex, 72, ImageType.RGB);
            byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
            String filename = fileNameWithOutExt + "_" + attachmentUnit.getId() + "_Slide_" + order + ".png";
            MultipartFile slideFile = fileService.convertByteArrayToMultipart(filename, ".png", imageInByte);
            Path savePath = fileService.saveFile(slideFile,
                    FilePathService.getAttachmentUnitFilePath().resolve(attachmentUnit.getId().toString()).resolve("slide").resolve(String.valueOf(order)).resolve(filename));

            slideEntity.setSlideImagePath(FilePathService.publicPathForActualPath(savePath, (long) order).toString());
        }
    }

    /**
     * Update image for an existing slide.
     */
    private void updateExistingSlideImage(Slide slideEntity, String fileNameWithOutExt, AttachmentUnit attachmentUnit, int order) {
        String oldPath = slideEntity.getSlideImagePath();
        if (oldPath != null && !oldPath.isEmpty()) {
            Path originalPath = FilePathService.actualPathForPublicPath(URI.create(oldPath));
            String newFilename = fileNameWithOutExt + "_" + attachmentUnit.getId() + "_Slide_" + order + ".png";

            try {
                File existingFile = originalPath.toFile();
                if (existingFile.exists()) {
                    BufferedImage image = ImageIO.read(existingFile);
                    byte[] imageInByte = bufferedImageToByteArray(image, "png");

                    MultipartFile slideFile = fileService.convertByteArrayToMultipart(newFilename, ".png", imageInByte);
                    Path savePath = fileService.saveFile(slideFile, FilePathService.getAttachmentUnitFilePath().resolve(attachmentUnit.getId().toString()).resolve("slide")
                            .resolve(String.valueOf(order)).resolve(newFilename));

                    slideEntity.setSlideImagePath(FilePathService.publicPathForActualPath(savePath, (long) order).toString());
                    existingFile.delete();
                }
                else {
                    log.warn("Could not find existing slide file at path: {}", originalPath);
                }
            }
            catch (IOException e) {
                log.error("Failed to update slide image for reordering", e);
            }
        }
    }

    /**
     * Schedule unhiding for a slide if the hidden date has changed.
     */
    private void scheduleUnhideIfNeeded(Slide savedSlide, java.util.Date previousHiddenValue, java.util.Date newHiddenValue) {
        if (!Objects.equals(previousHiddenValue, newHiddenValue)) {
            slideUnhideService.handleSlideHiddenUpdate(savedSlide);
            log.debug("Scheduled unhiding for slide ID {} at time {}", savedSlide.getId(), newHiddenValue);
        }
    }

    /**
     * Clean up slides that are no longer in the page order.
     */
    private void cleanupRemovedSlides(List<Map<String, Object>> pageOrderList, List<Slide> existingSlides) {
        Set<String> slideIdsInPageOrder = pageOrderList.stream().map(page -> String.valueOf(page.get("slideId"))).filter(id -> !id.startsWith("temp_")).collect(Collectors.toSet());

        if (!slideIdsInPageOrder.isEmpty()) {
            List<Slide> slidesToRemove = existingSlides.stream().filter(slide -> !slideIdsInPageOrder.contains(String.valueOf(slide.getId()))).toList();

            if (!slidesToRemove.isEmpty()) {
                slideRepository.deleteAll(slidesToRemove);
                log.debug("Removed {} slides that are no longer in the page order", slidesToRemove.size());
            }
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
