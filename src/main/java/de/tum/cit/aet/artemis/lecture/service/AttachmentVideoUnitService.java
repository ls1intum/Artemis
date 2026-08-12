package de.tum.cit.aet.artemis.lecture.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUpdateIntent;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
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

    private final AttachmentService attachmentService;

    private final LectureContentUpdateClassifierService lectureContentUpdateClassifierService;

    private final SlideRepository slideRepository;

    private final IrisLectureUnitSyncService irisLectureUnitSyncService;

    private final SlideVisibilityUpdateService slideVisibilityUpdateService;

    private final LectureUnitService lectureUnitService;

    private final AttachmentVideoUnitPostCommitService postCommitService;

    private final TransactionTemplate transactionTemplate;

    public AttachmentVideoUnitService(AttachmentVideoUnitRepository attachmentVideoUnitRepository, AttachmentRepository attachmentRepository, FileService fileService,
            LectureUnitService lectureUnitService, AttachmentFileHashService attachmentFileHashService, AttachmentService attachmentService,
            LectureContentUpdateClassifierService lectureContentUpdateClassifierService, SlideRepository slideRepository, IrisLectureUnitSyncService irisLectureUnitSyncService,
            SlideVisibilityUpdateService slideVisibilityUpdateService, AttachmentVideoUnitPostCommitService postCommitService, PlatformTransactionManager transactionManager) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.attachmentFileHashService = attachmentFileHashService;
        this.attachmentService = attachmentService;
        this.lectureContentUpdateClassifierService = lectureContentUpdateClassifierService;
        this.slideRepository = slideRepository;
        this.irisLectureUnitSyncService = irisLectureUnitSyncService;
        this.slideVisibilityUpdateService = slideVisibilityUpdateService;
        this.lectureUnitService = lectureUnitService;
        this.postCommitService = postCommitService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

        if (!isPdfFile(file)) {
            irisLectureUnitSyncService.markVisibilityDirtyAfterCommit(buildSnapshot(savedAttachmentVideoUnit));
        }
        // Trigger automated content processing (transcription and ingestion)
        postCommitService.triggerContentProcessing(savedAttachmentVideoUnit);

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
        return transactionTemplate.execute(status -> updateAttachmentVideoUnitWithinTransaction(existingAttachmentVideoUnit, updateUnitDTO, updateAttachment, updateFile,
                studentVersionFile, keepFilename, hiddenPages, pageOrder, originalCompetencyIds));
    }

    private AttachmentVideoUnit updateAttachmentVideoUnitWithinTransaction(AttachmentVideoUnit existingAttachmentVideoUnit, AttachmentVideoUnitDTO updateUnitDTO,
            Attachment updateAttachment, MultipartFile updateFile, MultipartFile studentVersionFile, boolean keepFilename, List<HiddenPageInfoDTO> hiddenPages,
            List<SlideOrderDTO> pageOrder, Set<Long> originalCompetencyIds) {
        Long attachmentVideoUnitId = existingAttachmentVideoUnit.getId();
        if (attachmentVideoUnitId == null || attachmentVideoUnitRepository.findByIdForUpdate(attachmentVideoUnitId).isEmpty()) {
            throw new EntityNotFoundException("AttachmentVideoUnit", attachmentVideoUnitId);
        }
        LectureContentUpdateSnapshot beforeSnapshot = buildSnapshot(existingAttachmentVideoUnit);
        existingAttachmentVideoUnit.setDescription(updateUnitDTO.description());
        existingAttachmentVideoUnit.setName(updateUnitDTO.name());
        existingAttachmentVideoUnit.setReleaseDate(updateUnitDTO.releaseDate());
        existingAttachmentVideoUnit.setVideoSource(updateUnitDTO.videoSource());
        boolean hasUploadedFile = updateFile != null && !updateFile.isEmpty();
        boolean hasHiddenPagesRequestPart = hiddenPages != null;
        AttachmentUpdateIntent attachmentUpdateIntent = updateUnitDTO.attachmentUpdateIntent();
        // Note: competency links are updated by the resource layer using lectureUnitService.updateCompetencyLinks

        Attachment existingAttachment = existingAttachmentVideoUnit.getAttachment();
        AttachmentFileUpdateResult fileUpdateResult = AttachmentFileUpdateResult.unchanged(existingAttachment != null ? existingAttachment.getVersion() : null);
        boolean createdNewAttachment = false;
        boolean visibilitySyncDeferredToSlideSplit = false;
        Map<Integer, ZonedDateTime> projectedSlideHiddenUntilBySlideNumber = null;

        if (existingAttachment == null && updateAttachment != null) {
            createAttachment(updateAttachment, existingAttachmentVideoUnit, updateFile, keepFilename);
            fileUpdateResult = AttachmentFileUpdateResult.attachmentAdded(existingAttachmentVideoUnit.getAttachment().getVersion());
            createdNewAttachment = true;
        }

        AttachmentVideoUnit savedAttachmentVideoUnit = attachmentVideoUnitRepository.save(existingAttachmentVideoUnit);

        postCommitService.updateCompetencyProgress(originalCompetencyIds, savedAttachmentVideoUnit);

        // Process attachment if provided
        if (updateAttachment != null) {
            if (createdNewAttachment) {
                // Split PDF files into individual slides for easier navigation
                if (updateFile != null && "pdf".equalsIgnoreCase(FilenameUtils.getExtension(updateFile.getOriginalFilename()))) {
                    postCommitService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit);
                    visibilitySyncDeferredToSlideSplit = true;
                    projectedSlideHiddenUntilBySlideNumber = Map.of();
                }
            }
            else if (existingAttachment != null) {
                boolean isFileNeutralSlideMetadataUpdate = attachmentUpdateIntent == AttachmentUpdateIntent.NO_FILE_CHANGE && hasHiddenPagesRequestPart;
                if (hasUploadedFile) {
                    fileUpdateResult = updateAttachmentFileIfChanged(updateFile, existingAttachment, keepFilename, savedAttachmentVideoUnit.getId());
                }
                updateAttachment(existingAttachment, updateAttachment, savedAttachmentVideoUnit);

                Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
                savedAttachmentVideoUnit.setAttachment(savedAttachment);
                if (studentVersionFile != null && !studentVersionFile.isEmpty()) {
                    handleStudentVersionFile(studentVersionFile, savedAttachment, savedAttachmentVideoUnit.getId());
                }
                else if (fileUpdateResult.fileBytesChanged()) {
                    attachmentService.removeStudentVersionFile(savedAttachment);
                }

                if (isFileNeutralSlideMetadataUpdate) {
                    slideVisibilityUpdateService.updateVisibilityAndStudentVersion(savedAttachmentVideoUnit, hiddenPages);
                }
                else if (hasUploadedFile) {
                    if (fileUpdateResult.fileBytesChanged()) {
                        log.debug("Updated attachment {} file bytes from version {} to {}", existingAttachment.getId(), fileUpdateResult.oldVersion(),
                                fileUpdateResult.newVersion());
                        evictCache(updateFile, savedAttachmentVideoUnit);

                        // Split PDF into slides, respecting custom page order if provided
                        if ("pdf".equalsIgnoreCase(FilenameUtils.getExtension(updateFile.getOriginalFilename()))) {
                            visibilitySyncDeferredToSlideSplit = true;
                            if (pageOrder == null) {
                                detachSlidesForBasicReplacement(savedAttachmentVideoUnit.getId());
                                projectedSlideHiddenUntilBySlideNumber = Map.of();
                                postCommitService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit);
                            }
                            else {
                                postCommitService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit, hiddenPages, pageOrder);
                                projectedSlideHiddenUntilBySlideNumber = buildProjectedSlideHiddenUntilBySlideNumber(hiddenPages, pageOrder);
                            }
                        }
                    }
                    else if (hasHiddenPagesRequestPart) {
                        slideVisibilityUpdateService.updateVisibilityAndStudentVersion(savedAttachmentVideoUnit, hiddenPages);
                    }
                }
            }
        }

        LectureContentUpdateSnapshot afterSnapshot = buildSnapshot(savedAttachmentVideoUnit, projectedSlideHiddenUntilBySlideNumber);
        var updateKinds = lectureContentUpdateClassifierService.classifyAll(beforeSnapshot, afterSnapshot, fileUpdateResult);
        triggerContentProcessingForUpdateKinds(savedAttachmentVideoUnit, afterSnapshot, updateKinds, visibilitySyncDeferredToSlideSplit);
        return savedAttachmentVideoUnit;
    }

    /**
     * Detaches the previous slide generation before a basic PDF replacement is committed. The replacement split runs after commit, so clearing the association here prevents
     * stale hidden-slide rows from blocking the student download while the new slides are being generated or if that asynchronous split needs to be retried.
     */
    private void detachSlidesForBasicReplacement(Long attachmentVideoUnitId) {
        List<Slide> slides = slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnitId);
        slides.forEach(slide -> slide.setAttachmentVideoUnit(null));
        slideRepository.saveAll(slides);
    }

    private void triggerContentProcessingForUpdateKinds(AttachmentVideoUnit savedAttachmentVideoUnit, LectureContentUpdateSnapshot afterSnapshot,
            Set<LectureContentUpdateKind> updateKinds, boolean visibilitySyncDeferredToSlideSplit) {
        if (updateKinds.isEmpty()) {
            return;
        }

        if (updateKinds.contains(LectureContentUpdateKind.METADATA)) {
            irisLectureUnitSyncService.markMetadataDirtyAfterCommit(afterSnapshot);
        }

        if (updateKinds.contains(LectureContentUpdateKind.VISIBILITY) && !visibilitySyncDeferredToSlideSplit) {
            irisLectureUnitSyncService.markVisibilityDirtyAfterCommit(afterSnapshot);
        }

        if (updateKinds.contains(LectureContentUpdateKind.CONTENT)) {
            postCommitService.triggerContentProcessing(savedAttachmentVideoUnit);
        }
    }

    private static boolean isPdfFile(MultipartFile file) {
        return file != null && !file.isEmpty() && "pdf".equalsIgnoreCase(FilenameUtils.getExtension(file.getOriginalFilename()));
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

    private Map<Integer, ZonedDateTime> buildProjectedSlideHiddenUntilBySlideNumber(List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder) {
        Map<String, ZonedDateTime> hiddenUntilBySlideId = hiddenPages == null ? Map.of()
                : hiddenPages.stream().collect(LinkedHashMap::new, (map, hiddenPage) -> map.put(hiddenPage.slideId(), hiddenPage.date()), LinkedHashMap::putAll);
        var projectedSlideHiddenUntilBySlideNumber = new LinkedHashMap<Integer, ZonedDateTime>();
        pageOrder.stream().sorted(Comparator.comparingInt(SlideOrderDTO::order))
                .forEach(orderedSlide -> projectedSlideHiddenUntilBySlideNumber.put(orderedSlide.order(), hiddenUntilBySlideId.get(orderedSlide.slideId())));
        return projectedSlideHiddenUntilBySlideNumber;
    }

    private Map<Integer, ZonedDateTime> buildSlideHiddenUntilBySlideNumber(Long attachmentVideoUnitId) {
        return SlideVisibilitySnapshotHelper.toSortedHiddenUntilBySlideNumber(slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnitId));
    }

    private AttachmentFileUpdateResult updateAttachmentFileIfChanged(MultipartFile uploadedFile, Attachment existingAttachment, boolean keepFilename, Long attachmentVideoUnitId) {
        Integer oldVersion = existingAttachment.getVersion();
        String uploadedHash = attachmentFileHashService.sha256(uploadedFile).value();
        Optional<String> storedHash = getOrBackfillStoredFileSha256Hash(existingAttachment);

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

    private Optional<String> getOrBackfillStoredFileSha256Hash(Attachment existingAttachment) {
        String existingHash = existingAttachment.getSha256Hash();
        if (existingHash != null) {
            return Optional.of(existingHash);
        }
        if (existingAttachment.getLink() == null) {
            return Optional.empty();
        }

        try {
            Path existingFilePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(existingAttachment.getLink()), FilePathType.ATTACHMENT_UNIT);
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
     * Sets the required parameters for an attachment on update
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
            attachment.setLink(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.ATTACHMENT_UNIT, attachmentVideoUnitId).toString());
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
                throw new InternalServerErrorException("Could not create student version file", e);
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
            var attachmentUri = URI.create(attachmentVideoUnit.getAttachment().getLink());
            this.fileService.evictCacheForPath(FilePathConverter.fileSystemPathForExternalUri(attachmentUri, FilePathType.ATTACHMENT_UNIT));
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
        lecture.setAttachments(null);
        lectureUnitService.disconnectCompetencyLectureUnitLinks(attachmentVideoUnit);
    }
}
