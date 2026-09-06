package de.tum.cit.aet.artemis.lecture.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Service Implementation for managing the split of AttachmentVideoUnit into single slides and save them as PNG.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Service
public class SlideSplitterService {

    private static final Logger log = LoggerFactory.getLogger(SlideSplitterService.class);

    private final SlideRepository slideRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final SlideUnhideService slideUnhideService;

    private final ExerciseRepository exerciseRepository;

    public SlideSplitterService(SlideRepository slideRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository, SlideUnhideService slideUnhideService,
            ExerciseRepository exerciseRepository) {
        this.slideRepository = slideRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.slideUnhideService = slideUnhideService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Splits an AttachmentVideoUnit file into single slides and saves them as PNG files asynchronously.
     *
     * @param job the immutable attachment revision and slide configuration to process
     * @return a future that completes after slide splitting finishes
     */
    @Async("longRunningJobExecutor")
    @Transactional
    public CompletableFuture<Void> splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob job) {
        Optional<AttachmentVideoUnit> attachmentVideoUnitForUpdate = attachmentVideoUnitRepository.findByIdForUpdate(job.attachmentVideoUnitId());
        if (attachmentVideoUnitForUpdate.isEmpty()) {
            log.debug("Skipping slide split job for deleted AttachmentVideoUnit {}", job.attachmentVideoUnitId());
            return CompletableFuture.completedFuture(null);
        }

        AttachmentVideoUnit attachmentVideoUnit = attachmentVideoUnitRepository.findWithAttachmentById(job.attachmentVideoUnitId())
                .orElseThrow(() -> new IllegalStateException("Locked AttachmentVideoUnit disappeared before slide splitting " + job.attachmentVideoUnitId()));
        if (!job.matches(attachmentVideoUnit.getAttachment())) {
            log.debug("Skipping obsolete slide split job for AttachmentVideoUnit {} and attachment revision {}/{}/{}", job.attachmentVideoUnitId(), job.attachmentId(),
                    job.attachmentVersion(), job.attachmentSha256Hash());
            return CompletableFuture.completedFuture(null);
        }

        Path attachmentPath = new FileSystemLocation.AttachmentVideoUnitFile(attachmentVideoUnit.getId(), attachmentVideoUnit.getAttachment().getLink()).path();
        File file = attachmentPath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            String pdfFilename = file.getName();
            if (job.pageOrder() == null) {
                splitAttachmentVideoUnitIntoSingleSlides(document, attachmentVideoUnit, pdfFilename);
            }
            else {
                splitAttachmentVideoUnitIntoSingleSlides(document, attachmentVideoUnit, pdfFilename, job.hiddenPages(), job.pageOrder());
            }
        }
        catch (IOException e) {
            log.error("Error while splitting AttachmentVideoUnit {} into single slides", attachmentVideoUnit.getId(), e);
            throw new InternalServerErrorException("Could not split AttachmentVideoUnit into single slides: " + e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Updates slide visibility without rebuilding slide images or changing the attachment file.
     *
     * @param attachmentVideoUnit the attachment video unit whose slide visibility changed
     * @param hiddenPages         the complete set of hidden slides; omitted slides are made visible
     */
    @Transactional
    public void updateSlideVisibility(AttachmentVideoUnit attachmentVideoUnit, List<HiddenPageInfoDTO> hiddenPages) {
        lockAttachmentVideoUnit(attachmentVideoUnit);
        Map<String, HiddenPageInfoDTO> hiddenPagesMap = hiddenPages.stream().collect(Collectors.toMap(HiddenPageInfoDTO::slideId, dto -> dto));
        slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId()).forEach(slide -> {
            ZonedDateTime previousHiddenValue = updateSlideHiddenStatus(slide, hiddenPagesMap, String.valueOf(slide.getId()));
            Slide savedSlide = slideRepository.save(slide);
            scheduleUnhideIfNeeded(savedSlide, previousHiddenValue, savedSlide.getHidden());
        });
    }

    /**
     * Splits an AttachmentVideoUnit file into single slides and saves them as PNG files.
     * Only creates new slides; never updates existing ones to keep slide referencing.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to which the slides belong.
     * @param document            The PDF document that is already loaded.
     * @param pdfFilename         The name of the PDF file.
     */
    @Transactional
    public void splitAttachmentVideoUnitIntoSingleSlides(PDDocument document, AttachmentVideoUnit attachmentVideoUnit, String pdfFilename) {
        lockAttachmentVideoUnit(attachmentVideoUnit);
        log.debug("Splitting AttachmentVideoUnit file {} into single slides", attachmentVideoUnit.getAttachment().getName());
        try {
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);
            int numPages = document.getNumberOfPages();
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < numPages; page++) {
                BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(page, 72, ImageType.RGB);
                byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
                int slideNumber = page + 1;
                String filename = uniqueSlideFilename(fileNameWithOutExt, attachmentVideoUnit.getId(), slideNumber);
                MultipartFile slideFile = FileUtil.convertByteArrayToMultipart(filename, ".png", imageInByte);
                var path = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnit.getId().toString()).resolve("slide")
                        .resolve(String.valueOf(slideNumber)).resolve(filename);
                Path savePath = FileUtil.saveFile(slideFile, path);
                deleteFileAfterRollback(savePath);

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
    @Transactional
    public void splitAttachmentVideoUnitIntoSingleSlides(PDDocument document, AttachmentVideoUnit attachmentVideoUnit, String pdfFilename, List<HiddenPageInfoDTO> hiddenPages,
            List<SlideOrderDTO> pageOrder) {
        lockAttachmentVideoUnit(attachmentVideoUnit);
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

        // The slide image is stored in a directory named by the slide's number, so the number the slide had while that image was written is needed to find it again. Read it
        // before the new order overwrites it.
        int numberTheImageWasWrittenUnder = slideEntity.getSlideNumber();
        slideEntity.setSlideNumber(order);
        ZonedDateTime previousHiddenValue = updateSlideHiddenStatus(slideEntity, hiddenPagesMap, slideId);

        if (isNewSlide) {
            createNewSlideImage(slideEntity, pdfRenderer, fileNameWithOutExt, attachmentVideoUnit, order, totalPages);
        }
        else {
            updateExistingSlideImage(slideEntity, fileNameWithOutExt, attachmentVideoUnit, order, numberTheImageWasWrittenUnder);
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
            String filename = uniqueSlideFilename(fileNameWithOutExt, attachmentVideoUnit.getId(), order);
            MultipartFile slideFile = FileUtil.convertByteArrayToMultipart(filename, ".png", imageInByte);
            Path savePath = FileUtil.saveFile(slideFile, FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnit.getId().toString()).resolve("slide")
                    .resolve(String.valueOf(order)).resolve(filename));
            deleteFileAfterRollback(savePath);

            slideEntity.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.SLIDE, (long) order).toString());
        }
    }

    /**
     * Update image for an existing slide.
     *
     * @param slideEntity                   the slide being renumbered
     * @param fileNameWithOutExt            the name of the document the slide belongs to, without its extension
     * @param attachmentVideoUnit           the attachment video unit the slide belongs to
     * @param order                         the number the slide is being given
     * @param numberTheImageWasWrittenUnder the number the slide had while its current image was written, which names the directory that image is in
     */
    private void updateExistingSlideImage(Slide slideEntity, String fileNameWithOutExt, AttachmentVideoUnit attachmentVideoUnit, int order, int numberTheImageWasWrittenUnder) {
        String oldPath = slideEntity.getSlideImagePath();
        if (oldPath != null && !oldPath.isEmpty()) {
            Path originalPath = new FileSystemLocation.Slide(attachmentVideoUnit.getId(), numberTheImageWasWrittenUnder, oldPath).path();
            String newFilename = uniqueSlideFilename(fileNameWithOutExt, attachmentVideoUnit.getId(), order);

            try {
                File existingFile = originalPath.toFile();
                if (existingFile.exists()) {
                    BufferedImage image = ImageIO.read(existingFile);
                    byte[] imageInByte = bufferedImageToByteArray(image, "png");

                    MultipartFile slideFile = FileUtil.convertByteArrayToMultipart(newFilename, ".png", imageInByte);
                    Path savePath = FileUtil.saveFile(slideFile, FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnit.getId().toString())
                            .resolve("slide").resolve(String.valueOf(order)).resolve(newFilename));
                    replaceFileAfterCommit(originalPath, savePath);

                    slideEntity.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.SLIDE, (long) order).toString());
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
            runAfterCommit(() -> {
                slideUnhideService.handleSlideHiddenUpdate(savedSlide);
                log.debug("Scheduled unhiding for slide ID {} at time {}", savedSlide.getId(), newHiddenValue);
            });
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

    private void lockAttachmentVideoUnit(AttachmentVideoUnit attachmentVideoUnit) {
        attachmentVideoUnitRepository.findByIdForUpdate(attachmentVideoUnit.getId())
                .orElseThrow(() -> new IllegalStateException("Cannot update slides for missing attachment video unit " + attachmentVideoUnit.getId()));
    }

    private void replaceFileAfterCommit(Path oldPath, Path newPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteFile(oldPath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                deleteFile(oldPath);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteFile(newPath);
                }
            }
        });
    }

    private void deleteFileAfterRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteFile(path);
                }
            }
        });
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException e) {
            log.error("Could not delete slide image {}", path, e);
        }
    }

    private static String uniqueSlideFilename(String filenameWithoutExtension, long attachmentVideoUnitId, int slideNumber) {
        return filenameWithoutExtension + "_" + attachmentVideoUnitId + "_" + UUID.randomUUID().toString().substring(0, 8) + "_Slide_" + slideNumber + ".png";
    }
}
