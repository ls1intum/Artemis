package de.tum.cit.aet.artemis.lecture.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
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

    // Package-private so the concurrency test can take the same lock instead of hardcoding its name.
    static final String SLIDE_LOCK_PREFIX = "slide-split-attachment-video-unit-";

    /**
     * How long to wait for another node to finish its slide work on the same unit. Splitting a large deck renders every
     * page to a PNG, so the wait has to allow for a slow document rather than only for lock hand-off.
     */
    private static final Duration SLIDE_LOCK_TIMEOUT = Duration.ofMinutes(5);

    private final SlideRepository slideRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final SlideUnhideService slideUnhideService;

    private final ExerciseRepository exerciseRepository;

    private final DistributedDataProvider distributedDataProvider;

    public SlideSplitterService(SlideRepository slideRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository, SlideUnhideService slideUnhideService,
            ExerciseRepository exerciseRepository, DistributedDataProvider distributedDataProvider) {
        this.slideRepository = slideRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.slideUnhideService = slideUnhideService;
        this.exerciseRepository = exerciseRepository;
        this.distributedDataProvider = distributedDataProvider;
    }

    /**
     * Splits an AttachmentVideoUnit file into single slides and saves them as PNG files asynchronously.
     *
     * @param job the immutable attachment revision and slide configuration to process
     * @return a future that completes after slide splitting finishes
     */
    @Async("longRunningJobExecutor")
    public CompletableFuture<Void> splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob job) {
        // Before waiting for the lock, not only after. The wait is minutes long and this executor has two threads,
        // shared with course and exam archiving, so a few quick re-uploads of the same file would otherwise park every
        // thread on a job that was already superseded. The authoritative check is the one under the lock below.
        if (isObsolete(job)) {
            return CompletableFuture.completedFuture(null);
        }
        DistributedLock lock = acquireSlideLock(job.attachmentVideoUnitId());
        try {
            AttachmentVideoUnit attachmentVideoUnit = attachmentVideoUnitRepository.findWithAttachmentById(job.attachmentVideoUnitId()).orElse(null);
            if (attachmentVideoUnit == null) {
                log.debug("Skipping slide split job for deleted AttachmentVideoUnit {}", job.attachmentVideoUnitId());
                return CompletableFuture.completedFuture(null);
            }
            if (!job.matches(attachmentVideoUnit.getAttachment())) {
                log.debug("Skipping obsolete slide split job for AttachmentVideoUnit {} and attachment revision {}/{}/{}", job.attachmentVideoUnitId(), job.attachmentId(),
                        job.attachmentVersion(), job.attachmentSha256Hash());
                return CompletableFuture.completedFuture(null);
            }

            Path attachmentPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(attachmentVideoUnit.getAttachment().getLink()), FilePathType.ATTACHMENT_UNIT);
            File file = attachmentPath.toFile();
            try (PDDocument document = Loader.loadPDF(file)) {
                String pdfFilename = file.getName();
                if (job.pageOrder() == null) {
                    splitIntoSingleSlides(document, attachmentVideoUnit, pdfFilename);
                }
                else {
                    splitIntoSingleSlides(document, attachmentVideoUnit, pdfFilename, job.hiddenPages(), job.pageOrder());
                }
            }
            catch (IOException e) {
                log.error("Error while splitting AttachmentVideoUnit {} into single slides", attachmentVideoUnit.getId(), e);
                throw new InternalServerErrorException("Could not split AttachmentVideoUnit into single slides: " + e.getMessage());
            }
            return CompletableFuture.completedFuture(null);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Updates slide visibility without rebuilding slide images or changing the attachment file.
     *
     * @param attachmentVideoUnit the attachment video unit whose slide visibility changed
     * @param hiddenPages         the complete set of hidden slides; omitted slides are made visible
     */
    public void updateSlideVisibility(AttachmentVideoUnit attachmentVideoUnit, List<HiddenPageInfoDTO> hiddenPages) {
        DistributedLock lock = acquireSlideLock(attachmentVideoUnit.getId());
        try {
            SlideOperation operation = new SlideOperation();
            Map<String, HiddenPageInfoDTO> hiddenPagesMap = hiddenPages.stream().collect(Collectors.toMap(HiddenPageInfoDTO::slideId, dto -> dto));
            // No file is written here, but rows are: a hidden date is written per slide, and a save part-way through
            // the list would otherwise leave the deck half hidden with none of the unhide scheduling done, so the
            // slides that were written would stay hidden past their date. Take a restore point and undo on failure,
            // exactly as the splitting paths do.
            operation.recordRestorePoint(slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId()));
            try {
                slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId()).forEach(slide -> {
                    ZonedDateTime previousHiddenValue = updateSlideHiddenStatus(slide, hiddenPagesMap, String.valueOf(slide.getId()));
                    Slide savedSlide = operation.save(slide);
                    scheduleUnhideIfNeeded(operation, savedSlide, previousHiddenValue, savedSlide.getHidden());
                });
            }
            catch (Throwable t) {
                operation.compensate();
                throw t;
            }
            // Outside the guarded region, for the reason given on the splitting overloads.
            operation.succeed();
        }
        finally {
            lock.unlock();
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
        DistributedLock lock = acquireSlideLock(attachmentVideoUnit.getId());
        try {
            splitIntoSingleSlides(document, attachmentVideoUnit, pdfFilename);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Writes one slide image and row per page of the document. The caller holds the slide lock for this unit.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to which the slides belong.
     * @param document            The PDF document that is already loaded.
     * @param pdfFilename         The name of the PDF file.
     */
    private void splitIntoSingleSlides(PDDocument document, AttachmentVideoUnit attachmentVideoUnit, String pdfFilename) {
        log.debug("Splitting AttachmentVideoUnit file {} into single slides", attachmentVideoUnit.getAttachment().getName());
        SlideOperation operation = new SlideOperation();
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
                operation.recordCreatedFile(savePath);

                Slide slideEntity = new Slide();
                slideEntity.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.SLIDE, (long) slideNumber).toString());
                slideEntity.setSlideNumber(slideNumber);
                slideEntity.setAttachmentVideoUnit(attachmentVideoUnit);
                operation.save(slideEntity);
            }
        }
        catch (IOException e) {
            operation.compensate();
            log.error("Error while splitting AttachmentVideoUnit {} into single slides", attachmentVideoUnit.getId(), e);
            throw new InternalServerErrorException("Could not split AttachmentVideoUnit into single slides: " + e.getMessage());
        }
        catch (Throwable t) {
            operation.compensate();
            throw t;
        }
        // Outside the guarded region on purpose. succeed() discards the images this operation replaced, so a throw
        // from inside it must not reach compensate(): that would delete the replacements too and restore rows pointing
        // at originals which are already gone, losing both copies of every slide.
        operation.succeed();
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
        DistributedLock lock = acquireSlideLock(attachmentVideoUnit.getId());
        try {
            splitIntoSingleSlides(document, attachmentVideoUnit, pdfFilename, hiddenPages, pageOrder);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Writes the slide images and rows for the given page order. The caller holds the slide lock for this unit.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to which the slides belong.
     * @param document            The PDF document that is already loaded.
     * @param pdfFilename         The name of the PDF file.
     * @param hiddenPages         The hidden pages information.
     * @param pageOrder           The order of pages in the PDF.
     */
    private void splitIntoSingleSlides(PDDocument document, AttachmentVideoUnit attachmentVideoUnit, String pdfFilename, List<HiddenPageInfoDTO> hiddenPages,
            List<SlideOrderDTO> pageOrder) {
        log.debug("Processing slides for Attachment Video Unit with hidden pages {}", attachmentVideoUnit.getAttachment().getName());
        SlideOperation operation = new SlideOperation();
        try {
            // Create a map of hiddenPages for easier lookup
            Map<String, HiddenPageInfoDTO> hiddenPagesMap = hiddenPages != null ? hiddenPages.stream().collect(Collectors.toMap(HiddenPageInfoDTO::slideId, dto -> dto)) : Map.of();

            // Retrieve existing slides. The second read is the restore point: the instances below are mutated in
            // place, so the undo log needs copies of its own rather than the same objects.
            List<Slide> existingSlides = slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId());
            operation.recordRestorePoint(slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId()));
            Map<String, Slide> existingSlidesMap = existingSlides.stream().collect(Collectors.toMap(slide -> String.valueOf(slide.getId()), slide -> slide));

            // Initialize PDF renderer and filename
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            String fileNameWithOutExt = FilenameUtils.removeExtension(pdfFilename);

            // Process each slide in the page order
            if (pageOrder != null) {
                for (SlideOrderDTO page : pageOrder) {
                    processSlide(operation, page, attachmentVideoUnit, existingSlidesMap, hiddenPagesMap, pdfRenderer, fileNameWithOutExt, document.getNumberOfPages());
                }
            }

            // Clean up slides that are no longer in the page order
            cleanupRemovedSlides(operation, pageOrder, existingSlides);
        }
        catch (IOException e) {
            operation.compensate();
            log.error("Error while splitting AttachmentVideoUnit {} into single slides", attachmentVideoUnit.getId(), e);
            throw new InternalServerErrorException("Could not split AttachmentVideoUnit into single slides: " + e.getMessage());
        }
        catch (Throwable t) {
            operation.compensate();
            throw t;
        }
        // Outside the guarded region: see the note on the overload above.
        operation.succeed();
    }

    /**
     * Process a single slide in the page order.
     */
    private void processSlide(SlideOperation operation, SlideOrderDTO page, AttachmentVideoUnit attachmentVideoUnit, Map<String, Slide> existingSlidesMap,
            Map<String, HiddenPageInfoDTO> hiddenPagesMap, PDFRenderer pdfRenderer, String fileNameWithOutExt, int totalPages) throws IOException {
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
            createNewSlideImage(operation, slideEntity, pdfRenderer, fileNameWithOutExt, attachmentVideoUnit, order, totalPages);
        }
        else {
            updateExistingSlideImage(operation, slideEntity, fileNameWithOutExt, attachmentVideoUnit, order);
        }

        // Save slide and schedule unhiding if needed
        Slide savedSlide = operation.save(slideEntity);
        scheduleUnhideIfNeeded(operation, savedSlide, previousHiddenValue, slideEntity.getHidden());
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
    private void createNewSlideImage(SlideOperation operation, Slide slideEntity, PDFRenderer pdfRenderer, String fileNameWithOutExt, AttachmentVideoUnit attachmentVideoUnit,
            int order, int totalPages) throws IOException {
        int pdfPageIndex = order - 1;
        if (pdfPageIndex >= 0 && pdfPageIndex < totalPages) {
            BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pdfPageIndex, 72, ImageType.RGB);
            byte[] imageInByte = bufferedImageToByteArray(bufferedImage, "png");
            String filename = uniqueSlideFilename(fileNameWithOutExt, attachmentVideoUnit.getId(), order);
            MultipartFile slideFile = FileUtil.convertByteArrayToMultipart(filename, ".png", imageInByte);
            Path savePath = FileUtil.saveFile(slideFile, FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnit.getId().toString()).resolve("slide")
                    .resolve(String.valueOf(order)).resolve(filename));
            operation.recordCreatedFile(savePath);

            slideEntity.setSlideImagePath(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.SLIDE, (long) order).toString());
        }
    }

    /**
     * Update image for an existing slide.
     */
    private void updateExistingSlideImage(SlideOperation operation, Slide slideEntity, String fileNameWithOutExt, AttachmentVideoUnit attachmentVideoUnit, int order) {
        String oldPath = slideEntity.getSlideImagePath();
        if (oldPath != null && !oldPath.isEmpty()) {
            Path originalPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(oldPath), FilePathType.SLIDE);
            String newFilename = uniqueSlideFilename(fileNameWithOutExt, attachmentVideoUnit.getId(), order);

            try {
                File existingFile = originalPath.toFile();
                if (existingFile.exists()) {
                    BufferedImage image = ImageIO.read(existingFile);
                    byte[] imageInByte = bufferedImageToByteArray(image, "png");

                    MultipartFile slideFile = FileUtil.convertByteArrayToMultipart(newFilename, ".png", imageInByte);
                    Path savePath = FileUtil.saveFile(slideFile, FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnit.getId().toString())
                            .resolve("slide").resolve(String.valueOf(order)).resolve(newFilename));
                    // The new image is removed again if this operation fails; the original is removed only once it
                    // has succeeded, so a failure leaves the slide pointing at a file that still exists.
                    operation.recordCreatedFile(savePath);
                    operation.recordSupersededFile(originalPath);

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
    private void scheduleUnhideIfNeeded(SlideOperation operation, Slide savedSlide, ZonedDateTime previousHiddenValue, ZonedDateTime newHiddenValue) {
        if (!Objects.equals(previousHiddenValue, newHiddenValue)) {
            operation.afterSuccess(() -> {
                slideUnhideService.handleSlideHiddenUpdate(savedSlide);
                log.debug("Scheduled unhiding for slide ID {} at time {}", savedSlide.getId(), newHiddenValue);
            });
        }
    }

    /**
     * Update slides that are no longer in the page order by setting their attachmentVideoUnit to null instead of deleting them.
     */
    private void cleanupRemovedSlides(SlideOperation operation, List<SlideOrderDTO> pageOrderList, List<Slide> existingSlides) {
        if (pageOrderList == null || pageOrderList.isEmpty()) {
            return;
        }

        Set<String> slideIdsInPageOrder = pageOrderList.stream().map(SlideOrderDTO::slideId).filter(id -> !id.startsWith("temp_")).collect(Collectors.toSet());

        if (!slideIdsInPageOrder.isEmpty()) {
            List<Slide> slidesToDetach = existingSlides.stream().filter(slide -> !slideIdsInPageOrder.contains(String.valueOf(slide.getId()))).toList();

            if (!slidesToDetach.isEmpty()) {
                for (Slide slide : slidesToDetach) {
                    slide.setAttachmentVideoUnit(null);
                    // Through the operation like every other write here. These rows already exist, so nothing is
                    // recorded as created and the restore point already covers them; routing it here keeps that true
                    // if this ever starts writing a row of its own.
                    operation.save(slide);
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

    /**
     * Acquire the cluster-wide lock that serializes slide work for one attachment video unit.
     * <p>
     * Replaces a {@code SELECT ... FOR UPDATE} on the unit row. That lock only excluded anyone while a transaction
     * spanned the whole operation, and this service must not declare one; a row lock without a transaction is released
     * at once and excludes nobody. A named distributed lock gives the same mutual exclusion, holds for exactly as long
     * as the operation, and works the same on every distributed-data backend.
     *
     * @param attachmentVideoUnitId the unit whose slides are about to be written
     * @return the acquired lock, which the caller must release in a finally block
     */
    /**
     * Whether this job has already been superseded, judged without holding the slide lock.
     *
     * @param job the slide split job
     * @return true if the unit is gone, or its attachment is no longer the revision the job was created for
     */
    private boolean isObsolete(AttachmentVideoUnitSlideSplitJob job) {
        return attachmentVideoUnitRepository.findWithAttachmentById(job.attachmentVideoUnitId()).map(unit -> !job.matches(unit.getAttachment())).orElse(true);
    }

    private DistributedLock acquireSlideLock(Long attachmentVideoUnitId) {
        if (attachmentVideoUnitId == null) {
            throw new IllegalStateException("Cannot update slides for an attachment video unit that has not been saved yet");
        }
        DistributedLock lock = distributedDataProvider.getLock(SLIDE_LOCK_PREFIX + attachmentVideoUnitId);
        if (!lock.tryLock(SLIDE_LOCK_TIMEOUT)) {
            throw new InternalServerErrorException(
                    "Could not acquire the slide lock for attachment video unit " + attachmentVideoUnitId + " within " + SLIDE_LOCK_TIMEOUT.toSeconds() + " seconds");
        }
        // Checked under the lock, because the pessimistic lock this replaced also refused to touch a unit that had
        // gone: it read the row to lock it. Without this, a unit deleted while the caller waited for the lock would
        // still have its slide rows rewritten, and they would be orphaned the moment they were written.
        //
        // Anything thrown between acquiring and returning has to release the lock here, including a failure of the
        // check itself. The caller only gets a reference on the happy path, so its finally block cannot help, and this
        // lock has no lease to fall back on — a leak would block every later slide operation on the unit until the
        // node restarts.
        try {
            if (!attachmentVideoUnitRepository.existsById(attachmentVideoUnitId)) {
                throw new IllegalStateException("Cannot update slides for missing attachment video unit " + attachmentVideoUnitId);
            }
        }
        catch (Throwable t) {
            lock.unlock();
            throw t;
        }
        return lock;
    }

    /**
     * Undo log for one slide operation, replacing the transaction that used to cover it.
     * <p>
     * Slide images live on disk and slide rows live in the database, so no single transaction ever covered both: the
     * previous code registered transaction synchronizations to delete files on rollback and after commit. With the
     * boundary gone those callbacks would silently do nothing, which is the worst possible failure here — orphaned
     * image files and half-written slide sets, with nothing logged.
     * <p>
     * So the compensation is explicit instead. Callers record what they create as they go; on success the superseded
     * originals are removed, and on failure everything this operation created is removed again, rows included.
     */
    private final class SlideOperation {

        private final List<Path> createdFiles = new ArrayList<>();

        private final List<Path> supersededFiles = new ArrayList<>();

        private final List<Long> createdSlideIds = new ArrayList<>();

        private final List<Slide> restorePoint = new ArrayList<>();

        private final List<Runnable> onSuccess = new ArrayList<>();

        /** A file this operation wrote, to be removed again if the operation fails. */
        private void recordCreatedFile(Path path) {
            createdFiles.add(path);
        }

        /** A file this operation replaced, to be removed once the operation has succeeded. */
        private void recordSupersededFile(Path path) {
            supersededFiles.add(path);
        }

        /**
         * Persist a slide, remembering a newly created one so a later failure can remove it again. Without this the
         * rows written before a failure would survive, and re-running the split would duplicate them.
         */
        private Slide save(Slide slide) {
            boolean isNew = slide.getId() == null;
            Slide saved = slideRepository.save(slide);
            if (isNew) {
                createdSlideIds.add(saved.getId());
            }
            return saved;
        }

        /**
         * Remember the state of the slides that already exist, so a failure can put them back.
         * <p>
         * Deleting the rows this operation created is not enough on its own: the operation also rewrites existing
         * slides, and a failure part-way through would otherwise leave the earlier ones pointing at a replacement
         * image while the later ones still point at the original. The caller must pass instances that it does not
         * then mutate, i.e. a separate read.
         *
         * @param existingSlides the untouched slides as they are in the database
         */
        private void recordRestorePoint(List<Slide> existingSlides) {
            restorePoint.addAll(existingSlides);
        }

        /** An action to run once the operation has succeeded, e.g. scheduling a slide to be unhidden. */
        private void afterSuccess(Runnable action) {
            onSuccess.add(action);
        }

        /**
         * Complete the operation: drop the images that were replaced, then run the deferred actions. Every repository
         * call has already committed by the time this runs, so this is the point the old afterCommit hook stood for.
         */
        private void succeed() {
            // Deferred actions first, each isolated. They reach the cluster messaging layer and the database, so one of
            // them failing during a rolling deploy is expected; it must not cost the others their turn, and it must not
            // abort the file cleanup below. Nothing here can be undone at this point anyway — the rows are committed.
            for (Runnable action : onSuccess) {
                try {
                    action.run();
                }
                catch (RuntimeException e) {
                    log.error("A deferred slide action failed after the slides were written; the slides themselves are intact", e);
                }
            }
            supersededFiles.forEach(SlideSplitterService.this::deleteFile);
        }

        /**
         * Roll the operation back as far as it can be rolled back. Failures here are logged and not rethrown: the
         * caller is already failing, and losing the original cause to a cleanup error would make the incident harder
         * to diagnose than the leftovers it is trying to remove.
         */
        private void compensate() {
            // Rows before files. If the row work fails once the files are already gone, the rows survive pointing at
            // nothing, which is the one leftover shape that is invisible in the database; the other way round leaves an
            // orphaned file, which is inert.
            try {
                if (!createdSlideIds.isEmpty()) {
                    slideRepository.deleteAllById(createdSlideIds);
                }
                if (!restorePoint.isEmpty()) {
                    slideRepository.saveAll(restorePoint);
                }
            }
            catch (RuntimeException e) {
                log.error("Could not undo the slide rows written before the failure; {} created and {} pre-existing slides may now be inconsistent", createdSlideIds.size(),
                        restorePoint.size(), e);
            }
            createdFiles.forEach(SlideSplitterService.this::deleteFile);
        }
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
