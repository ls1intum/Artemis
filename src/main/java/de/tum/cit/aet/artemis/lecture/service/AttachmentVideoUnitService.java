package de.tum.cit.aet.artemis.lecture.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Conditional(LectureEnabled.class)
@Service
@Lazy
public class AttachmentVideoUnitService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentVideoUnitService.class);

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final AttachmentFileHashService attachmentFileHashService;

    private final LectureContentUpdateClassifierService lectureContentUpdateClassifierService;

    private final SlideRepository slideRepository;

    private final IrisLectureUnitSyncService irisLectureUnitSyncService;

    private final SlideSplitterService slideSplitterService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final LectureUnitService lectureUnitService;

    private final Optional<LectureContentProcessingService> contentProcessingService;

    private final AttachmentService attachmentService;

    public AttachmentVideoUnitService(SlideSplitterService slideSplitterService, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            AttachmentRepository attachmentRepository, FileService fileService, Optional<CompetencyProgressApi> competencyProgressApi, LectureUnitService lectureUnitService,
            Optional<LectureContentProcessingService> contentProcessingService, AttachmentFileHashService attachmentFileHashService,
            LectureContentUpdateClassifierService lectureContentUpdateClassifierService, SlideRepository slideRepository, IrisLectureUnitSyncService irisLectureUnitSyncService,
            AttachmentService attachmentService) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.attachmentFileHashService = attachmentFileHashService;
        this.lectureContentUpdateClassifierService = lectureContentUpdateClassifierService;
        this.slideRepository = slideRepository;
        this.irisLectureUnitSyncService = irisLectureUnitSyncService;
        this.slideSplitterService = slideSplitterService;
        this.competencyProgressApi = competencyProgressApi;
        this.lectureUnitService = lectureUnitService;
        this.contentProcessingService = contentProcessingService;
        this.attachmentService = attachmentService;
    }

    /**
     * Creates a new attachment video unit for the given lecture.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to create
     * @param attachment          The attachment to create the attachmentVideoUnit for
     * @param file                The file to upload
     * @param keepFilename        Whether to keep the original filename or not.
     * @return The created attachment video unit
     */
    public AttachmentVideoUnit saveAttachmentVideoUnit(AttachmentVideoUnit attachmentVideoUnit, Attachment attachment, MultipartFile file, boolean keepFilename) {
        // TODO: switch to the new mechanism of lectureUnitService.updateCompetencyLinks
        AttachmentVideoUnit savedAttachmentVideoUnit = attachmentVideoUnitRepository.save(attachmentVideoUnit);

        if (attachment != null) {
            createAttachment(attachment, savedAttachmentVideoUnit, file, keepFilename);
        }

        // Trigger automated content processing (transcription and ingestion)
        contentProcessingService.ifPresent(api -> api.triggerProcessing(savedAttachmentVideoUnit));
        irisLectureUnitSyncService.markVisibilityDirty(buildSnapshot(savedAttachmentVideoUnit));

        return savedAttachmentVideoUnit;
    }

    /**
     * Updates the provided attachment video unit with an optional file.
     * Note: Competency links must be updated by the caller before invoking this method.
     *
     * @param existingAttachmentVideoUnit The attachment video unit to update.
     * @param updateUnitDTO               The DTO with the new attachment video unit data.
     * @param updateAttachment            The new attachment data.
     * @param updateFile                  The optional file.
     * @param studentVersionFile          The optional student PDF matching the updated file and visibility.
     * @param keepFilename                Whether to keep the original filename or not.
     * @param hiddenPages                 The hidden pages of attachment video unit.
     * @param pageOrder                   The new order of the edited attachment video unit
     * @param originalCompetencyIds       The competency IDs before the update (for progress tracking)
     * @return The updated attachment video unit.
     */
    public AttachmentVideoUnit updateAttachmentVideoUnit(AttachmentVideoUnit existingAttachmentVideoUnit, AttachmentVideoUnitDTO updateUnitDTO, Attachment updateAttachment,
            MultipartFile updateFile, MultipartFile studentVersionFile, boolean keepFilename, List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder,
            Set<Long> originalCompetencyIds) {
        // Written before anything else changes, so an invalid or unwritable student version fails the request without side effects. It is only referenced at the end.
        boolean acceptsStudentVersion = studentVersionFile != null && !studentVersionFile.isEmpty() && existingAttachmentVideoUnit.getAttachment() != null
                && updateAttachment != null;
        String stagedStudentVersion = acceptsStudentVersion ? stageStudentVersionFile(studentVersionFile, existingAttachmentVideoUnit.getId()) : null;
        boolean stagedStudentVersionHandedOver = false;
        try {
            AttachmentVideoUnit savedAttachmentVideoUnit = applyUpdate(existingAttachmentVideoUnit, updateUnitDTO, updateAttachment, updateFile, stagedStudentVersion != null,
                    keepFilename, hiddenPages, pageOrder, originalCompetencyIds);
            if (stagedStudentVersion != null) {
                stagedStudentVersionHandedOver = true;
                publishStagedStudentVersion(savedAttachmentVideoUnit, stagedStudentVersion);
            }
            prepareAttachmentVideoUnitForClient(savedAttachmentVideoUnit);
            return savedAttachmentVideoUnit;
        }
        finally {
            if (stagedStudentVersion != null && !stagedStudentVersionHandedOver) {
                attachmentService.discardStagedStudentVersionFile(existingAttachmentVideoUnit.getId(), stagedStudentVersion);
            }
        }
    }

    private AttachmentVideoUnit applyUpdate(AttachmentVideoUnit existingAttachmentVideoUnit, AttachmentVideoUnitDTO updateUnitDTO, Attachment updateAttachment,
            MultipartFile updateFile, boolean hasStudentVersionFile, boolean keepFilename, List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder,
            Set<Long> originalCompetencyIds) {
        LectureContentUpdateSnapshot beforeSnapshot = buildSnapshot(existingAttachmentVideoUnit);
        existingAttachmentVideoUnit.setDescription(updateUnitDTO.description());
        existingAttachmentVideoUnit.setName(updateUnitDTO.name());
        existingAttachmentVideoUnit.setReleaseDate(updateUnitDTO.releaseDate());
        existingAttachmentVideoUnit.setVideoSource(updateUnitDTO.videoSource());
        boolean hasUploadedFile = updateFile != null && !updateFile.isEmpty();
        // Note: competency links are updated by the resource layer using lectureUnitService.updateCompetencyLinks

        Attachment existingAttachment = existingAttachmentVideoUnit.getAttachment();
        AttachmentFileUpdateResult fileUpdateResult = AttachmentFileUpdateResult.unchanged(existingAttachment != null ? existingAttachment.getVersion() : null);
        boolean createdNewAttachment = false;
        boolean regenerateStudentVersion = false;
        boolean visibilitySyncDeferredToSlideSplit = false;
        Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber = null;

        if (existingAttachment == null && updateAttachment != null) {
            createAttachment(updateAttachment, existingAttachmentVideoUnit, updateFile, keepFilename);
            fileUpdateResult = AttachmentFileUpdateResult.attachmentAdded(existingAttachmentVideoUnit.getAttachment().getVersion());
            createdNewAttachment = true;
        }

        AttachmentVideoUnit savedAttachmentVideoUnit = attachmentVideoUnitRepository.save(existingAttachmentVideoUnit);

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, savedAttachmentVideoUnit));

        // Process attachment if provided
        if (updateAttachment != null) {
            if (createdNewAttachment) {
                // Split PDF files into individual slides for easier navigation
                if (updateFile != null && "pdf".equalsIgnoreCase(FilenameUtils.getExtension(updateFile.getOriginalFilename()))) {
                    slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(savedAttachmentVideoUnit, null, null));
                    projectedSlideHiddenUntilBySlideNumber = Map.of();
                    visibilitySyncDeferredToSlideSplit = true;
                }
            }
            else if (existingAttachment != null) {
                updateAttachment(existingAttachment, updateAttachment, savedAttachmentVideoUnit);

                if (hasUploadedFile) {
                    fileUpdateResult = updateAttachmentFileIfChanged(updateFile, existingAttachment, keepFilename, savedAttachmentVideoUnit.getId());
                    if (fileUpdateResult.fileBytesChanged()) {
                        log.debug("Updated attachment {} file bytes from version {} to {}", existingAttachment.getId(), fileUpdateResult.oldVersion(),
                                fileUpdateResult.newVersion());
                    }
                }

                Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
                savedAttachmentVideoUnit.setAttachment(savedAttachment);
                evictCache(updateFile, savedAttachmentVideoUnit);

                if (!hasStudentVersionFile && fileUpdateResult.fileBytesChanged()) {
                    // A student version derived from the replaced file no longer matches it. A submitted one replaces it at the end of the request instead.
                    attachmentService.removeStudentVersionFile(savedAttachment);
                }

                boolean isPdfUpload = hasUploadedFile && "pdf".equalsIgnoreCase(FilenameUtils.getExtension(updateFile.getOriginalFilename()));
                // The same bytes again need no new slides, unless the unit has none: a split that failed after a re-upload leaves an empty deck, and uploading the file again
                // is how an instructor retries it.
                boolean splitsSlides = isPdfUpload
                        && (fileUpdateResult.fileBytesChanged() || slideRepository.findAllByAttachmentVideoUnitId(savedAttachmentVideoUnit.getId()).isEmpty());
                if (splitsSlides) {
                    // Split the PDF into slides, respecting a custom page order if provided
                    if (pageOrder == null) {
                        AttachmentVideoUnitSlideSplitJob job = AttachmentVideoUnitSlideSplitJob.of(savedAttachmentVideoUnit, null, null);
                        retireSlidesOfReplacedFile(job);
                        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(job);
                        projectedSlideHiddenUntilBySlideNumber = Map.of();
                    }
                    else {
                        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(savedAttachmentVideoUnit, hiddenPages, pageOrder));
                        projectedSlideHiddenUntilBySlideNumber = buildProjectedSlideHiddenUntilBySlideNumber(hiddenPages, pageOrder);
                    }
                    visibilitySyncDeferredToSlideSplit = true;
                }
                else if (!fileUpdateResult.fileBytesChanged() && hiddenPages != null) {
                    // No file, or one with the same bytes as the stored file: the slides stay as they are, so this is at most a visibility change. Visibility is only
                    // touched when the request carries it; an omitted part leaves slides and student version unchanged.
                    slideSplitterService.updateSlideVisibility(savedAttachmentVideoUnit, hiddenPages);
                    regenerateStudentVersion = !hasStudentVersionFile;
                }
            }
        }
        else if (existingAttachment != null) {
            keepAttachmentInStepWithUnit(existingAttachment, savedAttachmentVideoUnit);
        }

        LectureContentUpdateSnapshot afterSnapshot = buildSnapshot(savedAttachmentVideoUnit, projectedSlideHiddenUntilBySlideNumber);
        var updateKinds = lectureContentUpdateClassifierService.classifyAll(beforeSnapshot, afterSnapshot, fileUpdateResult);
        triggerContentProcessingForUpdateKinds(savedAttachmentVideoUnit, afterSnapshot, updateKinds, visibilitySyncDeferredToSlideSplit);
        // Only after Iris was told about the new visibility: a failure here must not leave Iris with the old one. It cannot fail the request either, because the slide
        // visibility is already saved.
        if (regenerateStudentVersion) {
            attachmentService.regenerateStudentVersionOrRemoveOutdated(savedAttachmentVideoUnit.getAttachment());
        }
        return savedAttachmentVideoUnit;
    }

    private String stageStudentVersionFile(MultipartFile studentVersionFile, long attachmentVideoUnitId) {
        try {
            return attachmentService.stageUploadedStudentVersionFile(studentVersionFile.getBytes(), attachmentVideoUnitId, studentVersionFile.getOriginalFilename());
        }
        catch (IOException e) {
            throw new InternalServerErrorException("Could not store the student version file", e);
        }
    }

    /**
     * References the student version staged at the start of the request. Everything else the request changes is already done, so a failure here only concerns the student
     * version: a previous student version of a replaced file no longer matches it and is removed, so downloads fail closed rather than serve it.
     */
    private void publishStagedStudentVersion(AttachmentVideoUnit savedAttachmentVideoUnit, String stagedStudentVersion) {
        Attachment attachment = savedAttachmentVideoUnit.getAttachment();
        try {
            attachmentService.publishStudentVersionFile(attachment, savedAttachmentVideoUnit.getId(), stagedStudentVersion);
        }
        catch (RuntimeException exception) {
            attachmentService.removeStudentVersionFile(attachment);
            throw exception;
        }
    }

    /**
     * Starts the follow-up work for what the update changed.
     * <p>
     * When the update scheduled a slide split, Iris is not told about the projected visibility here: the split may already have finished, or failed and been undone, and
     * it tells Iris the visibility of the slides it actually saved once it is done.
     */
    private void triggerContentProcessingForUpdateKinds(AttachmentVideoUnit savedAttachmentVideoUnit, LectureContentUpdateSnapshot afterSnapshot,
            Set<LectureContentUpdateKind> updateKinds, boolean visibilitySyncDeferredToSlideSplit) {
        if (updateKinds.isEmpty()) {
            return;
        }

        if (updateKinds.contains(LectureContentUpdateKind.CONTENT)) {
            contentProcessingService.ifPresent(service -> service.triggerProcessing(savedAttachmentVideoUnit));
        }

        if (updateKinds.contains(LectureContentUpdateKind.METADATA)) {
            irisLectureUnitSyncService.markMetadataDirty(afterSnapshot);
        }

        if (updateKinds.contains(LectureContentUpdateKind.VISIBILITY) && !visibilitySyncDeferredToSlideSplit) {
            irisLectureUnitSyncService.markVisibilityDirty(afterSnapshot);
        }
    }

    /**
     * Retires the slides of the file a basic upload just replaced, before the new file is split asynchronously.
     * <p>
     * Until the split finishes, the old slides would otherwise still count as the current deck, so their hidden pages would block the download of the new, fully visible
     * file. The update only applies while the unit still holds the revision of this upload, so it never retires the deck of a newer upload. The split restores only current
     * slides when it fails, so it cannot bring the retired deck back.
     *
     * @param job the split job of the upload, carrying the revision it replaced the file with
     */
    private void retireSlidesOfReplacedFile(AttachmentVideoUnitSlideSplitJob job) {
        int retiredSlides = slideRepository.supersedeCurrentSlidesIfAttachmentRevisionMatches(job.attachmentVideoUnitId(), job.attachmentId(), job.attachmentVersion(),
                job.attachmentSha256Hash());
        log.debug("Retired {} slides of the replaced file of attachment video unit {}", retiredSlides, job.attachmentVideoUnitId());
    }

    private LectureContentUpdateSnapshot buildSnapshot(AttachmentVideoUnit unit) {
        return buildSnapshot(unit, null);
    }

    private LectureContentUpdateSnapshot buildSnapshot(AttachmentVideoUnit unit, Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber) {
        Lecture lecture = unit.getLecture();
        Course course = lecture != null ? lecture.getCourse() : null;
        Attachment attachment = unit.getAttachment();

        return new LectureContentUpdateSnapshot(unit.getId(), unit.getName(), lecture != null ? lecture.getTitle() : null, course != null ? course.getTitle() : null,
                course != null ? course.getDescription() : null, attachment != null ? attachment.getVersion() : null, attachment != null ? attachment.getLink() : null,
                unit.getVideoSource(), unit.resolveReleaseDate(),
                projectedSlideHiddenUntilBySlideNumber != null ? projectedSlideHiddenUntilBySlideNumber : buildSlideHiddenUntilBySlideNumber(unit.getId()));
    }

    private static Map<Integer, ZonedDateTime> buildProjectedSlideHiddenUntilBySlideNumber(List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder) {
        var hiddenUntilBySlideId = new LinkedHashMap<String, ZonedDateTime>();
        if (hiddenPages != null) {
            hiddenPages.forEach(hiddenPage -> hiddenUntilBySlideId.put(hiddenPage.slideId(), hiddenPage.date()));
        }

        var hiddenUntilBySlideNumber = new LinkedHashMap<Integer, ZonedDateTime>();
        pageOrder.forEach(page -> hiddenUntilBySlideNumber.put(page.order(), hiddenUntilBySlideId.get(page.slideId())));
        return hiddenUntilBySlideNumber;
    }

    private Map<Integer, ZonedDateTime> buildSlideHiddenUntilBySlideNumber(Long attachmentVideoUnitId) {
        return SlideVisibilitySnapshotHelper.toSortedHiddenUntilBySlideNumber(slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnitId));
    }

    private AttachmentFileUpdateResult updateAttachmentFileIfChanged(MultipartFile uploadedFile, Attachment existingAttachment, boolean keepFilename, Long attachmentVideoUnitId) {
        Integer oldVersion = existingAttachment.getVersion();
        String uploadedHash = attachmentFileHashService.sha256(uploadedFile).value();
        Optional<String> storedHash = getOrBackfillStoredFileSha256Hash(existingAttachment, attachmentVideoUnitId);

        if (storedHash.isPresent() && storedHash.get().equals(uploadedHash)) {
            existingAttachment.setSha256Hash(uploadedHash);
            return AttachmentFileUpdateResult.unchanged(oldVersion);
        }

        handleFile(uploadedFile, existingAttachment, keepFilename, attachmentVideoUnitId);
        int newVersion = oldVersion == null ? 1 : oldVersion + 1;
        existingAttachment.setVersion(newVersion);
        existingAttachment.setSha256Hash(uploadedHash);
        return AttachmentFileUpdateResult.changed(oldVersion, newVersion);
    }

    private Optional<String> getOrBackfillStoredFileSha256Hash(Attachment existingAttachment, long attachmentVideoUnitId) {
        String existingHash = existingAttachment.getSha256Hash();
        if (existingHash != null) {
            return Optional.of(existingHash);
        }
        // Empty for a missing link and for one that points outside this application: in both cases there is no stored file to hash, so the upload counts as changed content.
        Optional<FileSystemLocation> existingFileLocation = existingAttachment.fileLocation();
        if (existingFileLocation.isEmpty()) {
            return Optional.empty();
        }

        try {
            Path existingFilePath = existingFileLocation.get().path();
            if (!Files.exists(existingFilePath)) {
                log.warn("Stored attachment file {} does not exist. Treating uploaded file as changed content.", existingAttachment.getLink());
                return Optional.empty();
            }

            String storedHash = attachmentFileHashService.sha256(existingFilePath).value();
            existingAttachment.setSha256Hash(storedHash);
            return Optional.of(storedHash);
        }
        catch (AttachmentFileHashException | IllegalArgumentException | SecurityException e) {
            log.warn("Could not compute stored attachment SHA-256 hash for attachment {}. Treating uploaded file as changed content: {}", existingAttachment.getId(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private void createAttachment(Attachment attachment, AttachmentVideoUnit attachmentVideoUnit, MultipartFile file, boolean keepFilename) {
        if (file != null && !file.isEmpty()) {
            attachment.setSha256Hash(attachmentFileHashService.sha256(file).value());
        }
        handleFile(file, attachment, keepFilename, attachmentVideoUnit.getId());
        // Default attachment
        attachment.setVersion(1);
        attachment.setAttachmentVideoUnit(attachmentVideoUnit);

        Attachment savedAttachment = attachmentRepository.saveAndFlush(attachment);
        attachmentVideoUnit.setAttachment(savedAttachment);
        evictCache(file, attachmentVideoUnit);
    }

    /**
     * Gives the attachment the name and the release date of its unit after an update that sends no attachment metadata, such as the automatic save of an item edited in
     * place. Access to the file and the release date Iris sees are decided on the attachment, so a unit whose release date was cleared or moved would otherwise keep its file
     * hidden or show it early. The file and its student version stay as they are: the student version leaves out the hidden slides.
     *
     * @param existingAttachment  the attachment of the unit
     * @param attachmentVideoUnit the saved unit
     */
    private void keepAttachmentInStepWithUnit(Attachment existingAttachment, AttachmentVideoUnit attachmentVideoUnit) {
        if (Objects.equals(existingAttachment.getName(), attachmentVideoUnit.getName())
                && Objects.equals(existingAttachment.getReleaseDate(), attachmentVideoUnit.getReleaseDate())) {
            return;
        }
        existingAttachment.setAttachmentVideoUnit(attachmentVideoUnit);
        existingAttachment.setName(attachmentVideoUnit.getName());
        existingAttachment.setReleaseDate(attachmentVideoUnit.getReleaseDate());
        attachmentVideoUnit.setAttachment(attachmentRepository.saveAndFlush(existingAttachment));
    }

    /**
     * Sets the required parameters for an attachment on update. The student version is left alone: whether it still matches is decided by the file and visibility handling of
     * the update.
     *
     * @param existingAttachment  the existing attachment
     * @param updateAttachment    the new attachment containing updated information
     * @param attachmentVideoUnit the attachment video unit to update
     */
    private void updateAttachment(Attachment existingAttachment, Attachment updateAttachment, AttachmentVideoUnit attachmentVideoUnit) {
        // Make sure that the original references are preserved.
        existingAttachment.setAttachmentVideoUnit(attachmentVideoUnit);
        existingAttachment.setReleaseDate(updateAttachment.getReleaseDate());
        existingAttachment.setName(updateAttachment.getName());
        existingAttachment.setAttachmentType(updateAttachment.getAttachmentType());
    }

    /**
     * Handles the file after upload if provided.
     *
     * @param file         Potential file to handle
     * @param attachment   Attachment linked to the file.
     * @param keepFilename Whether to keep the original filename or not.
     */
    private void handleFile(MultipartFile file, Attachment attachment, boolean keepFilename, Long attachmentVideoUnitId) {
        if (file != null && !file.isEmpty()) {
            Path basePath = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnitId.toString());
            Path savePath = FileUtil.saveFile(file, basePath, FilePathType.ATTACHMENT_UNIT, keepFilename);
            attachment.setLink(savePath.getFileName().toString());
            // The new file is in the unit's own directory, which is where Attachment.fileLocation looks first, so an attachment whose file the lecture migration left behind
            // stops resolving to that lecture's directory as soon as it is replaced here.
            attachment.setUploadDate(ZonedDateTime.now());
        }
    }

    /**
     * Handles the student version file of an attachment, updates its reference in the database,
     * and deletes the old version if it exists.
     *
     * @param studentVersionFile    the new student version file to be saved
     * @param attachment            the existing attachment
     * @param attachmentVideoUnitId the id of the attachment video unit
     */
    public void handleStudentVersionFile(MultipartFile studentVersionFile, Attachment attachment, Long attachmentVideoUnitId) {
        if (studentVersionFile != null) {
            try {
                attachmentService.replaceUploadedStudentVersionFile(studentVersionFile.getBytes(), attachment, attachmentVideoUnitId, studentVersionFile.getOriginalFilename());
            }
            catch (IOException e) {
                throw new InternalServerErrorException("Could not store the student version file", e);
            }
        }
    }

    /**
     * If a file was provided the cache for that file gets evicted.
     *
     * @param file                Potential file to evict the cache for.
     * @param attachmentVideoUnit Attachment video unit liked to the file.
     */
    private void evictCache(MultipartFile file, AttachmentVideoUnit attachmentVideoUnit) {
        if (file != null && !file.isEmpty()) {
            // Nothing of ours is cached for an attachment that links elsewhere.
            attachmentVideoUnit.getAttachment().fileLocation().ifPresent(location -> this.fileService.evictCacheForPath(location.path()));
        }
    }

    /**
     * Cleans the attachment video unit before sending it to the client and sets the attachment relationship.
     *
     * @param attachmentVideoUnit The attachment video unit to clean.
     */
    // TODO: use a DTO for sending data to the client instead of manipulating entity objects
    public void prepareAttachmentVideoUnitForClient(AttachmentVideoUnit attachmentVideoUnit) {
        var lecture = attachmentVideoUnit.getLecture();
        var lectureUnits = lecture.getLectureUnits();
        if (lectureUnits != null && !lectureUnits.isEmpty()) {
            lecture.setLectureUnits(null);
        }
        lectureUnitService.disconnectCompetencyLectureUnitLinks(attachmentVideoUnit);
    }
}
