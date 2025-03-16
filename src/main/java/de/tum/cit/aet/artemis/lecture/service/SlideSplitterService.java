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
     * Splits an Attachment Unit file into single slides and saves them as PNG files or updates existing slides.
     *
     * @param attachmentUnit The attachment unit to which the slides belong.
     * @param document       The PDF document that is already loaded.
     * @param pdfFilename    The name of the PDF file.
     */
    public void splitAttachmentUnitIntoSingleSlides(PDDocument document, AttachmentUnit attachmentUnit, String pdfFilename) {
        log.debug("Processing slides for Attachment Unit {}", attachmentUnit.getId());
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

                Optional<Slide> existingSlideOpt = Optional.ofNullable(slideRepository.findSlideByAttachmentUnitIdAndSlideNumber(attachmentUnit.getId(), slideNumber));
                Slide slide = existingSlideOpt.orElseGet(Slide::new);
                slide.setSlideImagePath(FilePathService.publicPathForActualPath(savePath, (long) slideNumber).toString());
                slide.setSlideNumber(slideNumber);
                slide.setAttachmentUnit(attachmentUnit);
                slide.setHidden(null);
                slide.setExercise(null);
                slideRepository.save(slide);
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
            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, Object>> pageOrderList = objectMapper.readValue(pageOrder, new TypeReference<>() {
            });

            Map<String, Map<String, Object>> hiddenPagesMap = new HashMap<>();
            List<Map<String, Object>> hiddenPagesList = objectMapper.readValue(hiddenPages, new TypeReference<>() {
            });

            hiddenPagesMap = hiddenPagesList.stream().collect(Collectors.toMap(page -> (String) page.get("slideId"), page -> {
                Map<String, Object> data = new HashMap<>();
                String dateStr = (String) page.get("date");
                data.put("date", Timestamp.from(Instant.parse(dateStr)));

                if (page.get("exerciseId") != null) {
                    data.put("exerciseId", page.get("exerciseId"));
                }
                return data;
            }));

            List<Slide> existingSlides = slideRepository.findAllByAttachmentUnitId(attachmentUnit.getId());
            Map<String, Slide> existingSlidesMap = existingSlides.stream().collect(Collectors.toMap(Slide::getId, slide -> slide));

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);

            for (Map<String, Object> page : pageOrderList) {
                String slideId = (String) page.get("slideId");
                int order = ((Number) page.get("order")).intValue();
                int pageIndex = ((Number) page.get("pageIndex")).intValue();

                Slide slide;
                boolean isNewSlide = false;

                if (slideId.startsWith("temp_") || !existingSlidesMap.containsKey(slideId)) {
                    isNewSlide = true;
                    slide = new Slide();
                    slide.setAttachmentUnit(attachmentUnit);
                }
                else {
                    slide = existingSlidesMap.get(slideId);
                }

                slide.setSlideNumber(order);

                Map<String, Object> hiddenData = hiddenPagesMap.get(slideId);
                java.util.Date previousHiddenValue = slide.getHidden();

                if (hiddenData != null && hiddenData.containsKey("date")) {
                    slide.setHidden((java.util.Date) hiddenData.get("date"));

                    if (hiddenData.containsKey("exerciseId") && hiddenData.get("exerciseId") != null) {
                        Number exerciseId = (Number) hiddenData.get("exerciseId");
                        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId.longValue());
                        exercise.ifPresent(slide::setExercise);
                    }
                    else {
                        slide.setExercise(null);
                    }
                }
                else {
                    slide.setHidden(null);
                    slide.setExercise(null);
                }

                if (isNewSlide) {
                    int pdfPageIndex = pageIndex - 1;
                    if (pdfPageIndex >= 0 && pdfPageIndex < document.getNumberOfPages()) {
                        BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pdfPageIndex, 72, ImageType.RGB);
                        byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
                        String filename = fileNameWithOutExt + "_" + attachmentUnit.getId() + "_Slide_" + order + ".png";
                        MultipartFile slideFile = fileService.convertByteArrayToMultipart(filename, ".png", imageInByte);
                        Path savePath = fileService.saveFile(slideFile, FilePathService.getAttachmentUnitFilePath().resolve(attachmentUnit.getId().toString()).resolve("slide")
                                .resolve(String.valueOf(order)).resolve(filename));

                        slide.setSlideImagePath(FilePathService.publicPathForActualPath(savePath, (long) order).toString());
                    }
                }

                Slide savedSlide = slideRepository.save(slide);

                // Schedule unhiding if the hidden date has changed
                if (!Objects.equals(previousHiddenValue, slide.getHidden())) {
                    slideUnhideService.handleSlideHiddenUpdate(savedSlide);
                    log.debug("Scheduled unhiding for slide ID {} at time {}", savedSlide.getId(), slide.getHidden());
                }
            }

            // Clean up slides that are no longer in the page order
            Set<String> slideIdsInPageOrder = pageOrderList.stream().map(page -> (String) page.get("slideId")).filter(id -> !id.startsWith("temp_"))  // Don't include temporary IDs
                                                                                                                                                      // in the cleanup check
                    .collect(Collectors.toSet());

            if (!slideIdsInPageOrder.isEmpty()) {
                List<Slide> slidesToRemove = existingSlides.stream().filter(slide -> !slideIdsInPageOrder.contains(slide.getId())).toList();

                if (!slidesToRemove.isEmpty()) {
                    slideRepository.deleteAll(slidesToRemove);
                    log.debug("Removed {} slides that are no longer in the page order", slidesToRemove.size());
                }
            }
        }
        catch (IOException e) {
            log.error("Error while processing slides for Attachment Unit {}", attachmentUnit.getId(), e);
            throw new InternalServerErrorException("Could not process slides: " + e.getMessage());
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
