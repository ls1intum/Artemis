package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Service Implementation for managing the split of AttachmentVideoUnit into single slides and save them as PNG.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SlideSplitterService {

    private static final Logger log = LoggerFactory.getLogger(SlideSplitterService.class);

    private final SlideRepository slideRepository;

    private final SlideUnhideService slideUnhideService;

    private final ExerciseRepository exerciseRepository;

    public SlideSplitterService(SlideRepository slideRepository, SlideUnhideService slideUnhideService, ExerciseRepository exerciseRepository) {
        this.slideRepository = slideRepository;
        this.slideUnhideService = slideUnhideService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Splits an AttachmentVideoUnit file into single slides and saves them as PNG files asynchronously.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to which the slides belong.
     */
    @Async
    public void splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnit attachmentVideoUnit) {
        Path attachmentPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(attachmentVideoUnit.getAttachment().getLink()), FilePathType.ATTACHMENT_UNIT);
        File file = attachmentPath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            String pdfFilename = file.getName();
            splitAttachmentVideoUnitIntoSingleSlides(document, attachmentVideoUnit, pdfFilename);
        }
        catch (IOException e) {
            log.error("Error while splitting AttachmentVideoUnit {} into single slides", attachmentVideoUnit.getId(), e);
            throw new InternalServerErrorException("Could not split AttachmentVideoUnit into single slides: " + e.getMessage());
        }
    }

    /**
     * Splits an AttachmentVideoUnit file into single slides and saves them as PNG files asynchronously.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to which the slides belong.
     * @param hiddenPages         The hidden pages of the attachmentVideoUnit.
     * @param pageOrder           The page order of the attachmentVideoUnit.
     */
    @Async
    public void splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnit attachmentVideoUnit, List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder) {
        Path attachmentPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(attachmentVideoUnit.getAttachment().getLink()), FilePathType.ATTACHMENT_UNIT);
        File file = attachmentPath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            String pdfFilename = file.getName();
            splitAttachmentVideoUnitIntoSingleSlides(document, attachmentVideoUnit, pdfFilename, hiddenPages, pageOrder);
        }
        catch (IOException e) {
            log.error("Error while splitting AttachmentVideoUnit {} into single slides", attachmentVideoUnit.getId(), e);
            throw new InternalServerErrorException("Could not split AttachmentVideoUnit into single slides: " + e.getMessage());
        }
    }

    /**
     * Splits an AttachmentVideoUnit file into single slides and saves them as PNG files.
     * Only creates new slides; never updates existing ones to keep slide referencing.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to which the slides belong.
     * @param document            The PDF document that is already loaded.
     * @param pdfFilename         The name of the PDF file.
     */
    public void splitAttachmentVideoUnitIntoSingleSlides(PDDocument document, AttachmentVideoUnit attachmentVideoUnit, String pdfFilename) {
        log.debug("Splitting AttachmentVideoUnit file {} into single slides", attachmentVideoUnit.getAttachment().getName());
        try {
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);
            int numPages = document.getNumberOfPages();
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < numPages; page++) {
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 72, ImageType.RGB);
                byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
                int slideNumber = page + 1;
                String filename = fileNameWithOutExt + "_" + attachmentVideoUnit.getId() + "_Slide_" + slideNumber + ".png";
                MultipartFile slideFile = FileUtil.convertByteArrayToMultipart(filename, ".png", imageInByte);
                var path = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnit.getId().toString()).resolve("slide")
                        .resolve(String.valueOf(slideNumber)).resolve(filename);
                Path savePath = FileUtil.saveFile(slideFile, path);

                Slide slideEntity = new Slide();
                slideEntity.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.SLIDE, (long) slideNumber).toString());
                slideEntity.setSlideNumber(slideNumber);
                slideEntity.setAttachmentVideoUnit(attachmentVideoUnit);
                slideRepository.save(slideEntity);
            }
        }
        catch (IOException e) {
            log.error("Error while splitting AttachmentVideoUnit {} into single slides", attachmentVideoUnit.getId(), e);
            throw new InternalServerErrorException("Could not split AttachmentVideoUnit into single slides: " + e.getMessage());
        }
    }

    /**
     * Splits an AttachmentVideoUnit file into single slides and saves them as PNG files or updates existing slides.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to which the slides belong.
     * @param document            The PDF document that is already loaded.
     * @param pdfFilename         The name of the PDF file.
     * @param hiddenPages         The hidden pages information.
     * @param pageOrder           The order of pages in the PDF.
     */
    public void splitAttachmentVideoUnitIntoSingleSlides(PDDocument document, AttachmentVideoUnit attachmentVideoUnit, String pdfFilename, List<HiddenPageInfoDTO> hiddenPages,
            List<SlideOrderDTO> pageOrder) {
        log.debug("Processing slides for Attachment Video Unit with hidden pages {}", attachmentVideoUnit.getAttachment().getName());

        try {
            // Create a map of hiddenPages for easier lookup
            Map<String, HiddenPageInfoDTO> hiddenPagesMap = hiddenPages != null ? hiddenPages.stream().collect(Collectors.toMap(HiddenPageInfoDTO::slideId, dto -> dto)) : Map.of();

            // Retrieve existing slides
            List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId());
            Map<String, Slide> existingSlidesMap = existingSlides.stream().collect(Collectors.toMap(slide -> String.valueOf(slide.getId()), slide -> slide));

            // Initialize PDF renderer and filename
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);

            // Process each slide in the page order
            if (pageOrder != null) {
                for (SlideOrderDTO page : pageOrder) {
                    processSlide(page, attachmentVideoUnit, existingSlidesMap, hiddenPagesMap, pdfRenderer, fileNameWithOutExt, document.getNumberOfPages());
                }
            }

            // Clean up slides that are no longer in the page order
            cleanupRemovedSlides(pageOrder, existingSlides);
        }
        catch (IOException e) {
            log.error("Error while splitting AttachmentVideoUnit {} into single slides", attachmentVideoUnit.getId(), e);
            throw new InternalServerErrorException("Could not split AttachmentVideoUnit into single slides: " + e.getMessage());
        }
    }

    /**
     * Process a single slide in the page order.
     */
    private void processSlide(SlideOrderDTO page, AttachmentVideoUnit attachmentVideoUnit, Map<String, Slide> existingSlidesMap, Map<String, HiddenPageInfoDTO> hiddenPagesMap,
            PDFRenderer pdfRenderer, String fileNameWithOutExt, int totalPages) throws IOException {
        String slideId = page.slideId();
        int order = page.order();

        Slide slideEntity;
        boolean isNewSlide = false;

        // Determine if this is a new slide or an existing one
        if (slideId.startsWith("temp_") || !existingSlidesMap.containsKey(slideId)) {
            isNewSlide = true;
            slideEntity = new Slide();
            slideEntity.setAttachmentVideoUnit(attachmentVideoUnit);
        }
        else {
            slideEntity = existingSlidesMap.get(slideId);
        }

        slideEntity.setSlideNumber(order);
        ZonedDateTime previousHiddenValue = updateSlideHiddenStatus(slideEntity, hiddenPagesMap, slideId);

        if (isNewSlide) {
            createNewSlideImage(slideEntity, pdfRenderer, fileNameWithOutExt, attachmentVideoUnit, order, totalPages);
        }
        else {
            updateExistingSlideImage(slideEntity, fileNameWithOutExt, attachmentVideoUnit, order);
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
    private ZonedDateTime updateSlideHiddenStatus(Slide slideEntity, Map<String, HiddenPageInfoDTO> hiddenPagesMap, String slideId) {
        ZonedDateTime previousHiddenValue = slideEntity.getHidden();
        HiddenPageInfoDTO hiddenPageInfo = hiddenPagesMap.get(slideId);

        if (hiddenPageInfo != null) {
            slideEntity.setHidden(hiddenPageInfo.date());

            if (hiddenPageInfo.hasExercise()) {
                Optional<Exercise> exercise = exerciseRepository.findById(hiddenPageInfo.exerciseId());
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
    private void createNewSlideImage(Slide slideEntity, PDFRenderer pdfRenderer, String fileNameWithOutExt, AttachmentVideoUnit attachmentVideoUnit, int order, int totalPages)
            throws IOException {
        int pdfPageIndex = order - 1;
        if (pdfPageIndex >= 0 && pdfPageIndex < totalPages) {
            BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pdfPageIndex, 72, ImageType.RGB);
            byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
            String filename = fileNameWithOutExt + "_" + attachmentVideoUnit.getId() + "_Slide_" + order + ".png";
            MultipartFile slideFile = FileUtil.convertByteArrayToMultipart(filename, ".png", imageInByte);
            Path savePath = FileUtil.saveFile(slideFile, FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnit.getId().toString()).resolve("slide")
                    .resolve(String.valueOf(order)).resolve(filename));

            slideEntity.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.SLIDE, (long) order).toString());
        }
    }

    /**
     * Update image for an existing slide.
     */
    private void updateExistingSlideImage(Slide slideEntity, String fileNameWithOutExt, AttachmentVideoUnit attachmentVideoUnit, int order) {
        String oldPath = slideEntity.getSlideImagePath();
        if (oldPath != null && !oldPath.isEmpty()) {
            Path originalPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(oldPath), FilePathType.SLIDE);
            String newFilename = fileNameWithOutExt + "_" + attachmentVideoUnit.getId() + "_Slide_" + order + ".png";

            try {
                File existingFile = originalPath.toFile();
                if (existingFile.exists()) {
                    BufferedImage image = ImageIO.read(existingFile);
                    byte[] imageInByte = bufferedImageToByteArray(image, "png");

                    MultipartFile slideFile = FileUtil.convertByteArrayToMultipart(newFilename, ".png", imageInByte);
                    Path savePath = FileUtil.saveFile(slideFile, FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnit.getId().toString())
                            .resolve("slide").resolve(String.valueOf(order)).resolve(newFilename));

                    slideEntity.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.SLIDE, (long) order).toString());
                    existingFile.delete();
                }
                else {
                    log.warn("Could not find existing slide file at path: {}", originalPath);
                    throw new InternalServerErrorException("Could not find existing slide file at path: " + originalPath);
                }
            }
            catch (IOException e) {
                log.error("Failed to update slide image for reordering", e);
                throw new InternalServerErrorException("Failed to update slide image for reordering: " + e.getMessage());
            }
        }
    }

    /**
     * Schedule unhiding for a slide if the hidden date has changed.
     */
    private void scheduleUnhideIfNeeded(Slide savedSlide, ZonedDateTime previousHiddenValue, ZonedDateTime newHiddenValue) {
        if (!Objects.equals(previousHiddenValue, newHiddenValue)) {
            slideUnhideService.handleSlideHiddenUpdate(savedSlide);
            log.debug("Scheduled unhiding for slide ID {} at time {}", savedSlide.getId(), newHiddenValue);
        }
    }

    /**
     * Update slides that are no longer in the page order by setting their attachmentVideoUnit to null instead of deleting them.
     */
    private void cleanupRemovedSlides(List<SlideOrderDTO> pageOrderList, List<Slide> existingSlides) {
        if (pageOrderList == null || pageOrderList.isEmpty()) {
            return;
        }

        Set<String> slideIdsInPageOrder = pageOrderList.stream().map(SlideOrderDTO::slideId).filter(id -> !id.startsWith("temp_")).collect(Collectors.toSet());

        if (!slideIdsInPageOrder.isEmpty()) {
            List<Slide> slidesToDetach = existingSlides.stream().filter(slide -> !slideIdsInPageOrder.contains(String.valueOf(slide.getId()))).toList();

            if (!slidesToDetach.isEmpty()) {
                for (Slide slide : slidesToDetach) {
                    slide.setAttachmentVideoUnit(null);
                    slideRepository.save(slide);
                }
                log.debug("Detached {} slides that are no longer in the page order by setting their attachmentVideoUnit to null", slidesToDetach.size());
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
