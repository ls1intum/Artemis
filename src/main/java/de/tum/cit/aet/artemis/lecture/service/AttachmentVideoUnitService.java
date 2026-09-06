package de.tum.cit.aet.artemis.lecture.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.FilePathType;
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

    public AttachmentVideoUnitService(SlideSplitterService slideSplitterService, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            AttachmentRepository attachmentRepository, FileService fileService, Optional<CompetencyProgressApi> competencyProgressApi, LectureUnitService lectureUnitService,
            Optional<LectureContentProcessingService> contentProcessingService, AttachmentFileHashService attachmentFileHashService,
            LectureContentUpdateClassifierService lectureContentUpdateClassifierService, SlideRepository slideRepository, IrisLectureUnitSyncService irisLectureUnitSyncService) {
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
        irisLectureUnitSyncService.markVisibilityDirtyAfterCommit(buildSnapshot(savedAttachmentVideoUnit));

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
     * @param keepFilename                Whether to keep the original filename or not.
     * @param hiddenPages                 The hidden pages of attachment video unit.
     * @param pageOrder                   The new order of the edited attachment video unit
     * @param originalCompetencyIds       The competency IDs before the update (for progress tracking)
     * @return The updated attachment video unit.
     */
    public AttachmentVideoUnit updateAttachmentVideoUnit(AttachmentVideoUnit existingAttachmentVideoUnit, AttachmentVideoUnitDTO updateUnitDTO, Attachment updateAttachment,
            MultipartFile updateFile, boolean keepFilename, List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder, Set<Long> originalCompetencyIds) {
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
                }
            }
            else if (existingAttachment != null) {
                updateAttachment(existingAttachment, updateAttachment, savedAttachmentVideoUnit, hiddenPages);

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

                if (!hasUploadedFile && hiddenPages != null) {
                    slideSplitterService.updateSlideVisibility(savedAttachmentVideoUnit, hiddenPages);
                }

                // Slide splitting is intentionally identical to develop: it runs on every uploaded file. The SHA-256 comparison only gates the version bump (and therefore the
                // Pyris re-ingestion) above; it deliberately does not change the existing slide-splitting behavior.
                if (updateFile != null) {
                    // Split PDF into slides, respecting custom page order if provided
                    if ("pdf".equalsIgnoreCase(FilenameUtils.getExtension(updateFile.getOriginalFilename()))) {
                        if (pageOrder == null) {
                            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(savedAttachmentVideoUnit, null, null));
                            if (fileUpdateResult.fileBytesChanged()) {
                                projectedSlideHiddenUntilBySlideNumber = Map.of();
                            }
                        }
                        else {
                            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(savedAttachmentVideoUnit, hiddenPages, pageOrder));
                            projectedSlideHiddenUntilBySlideNumber = buildProjectedSlideHiddenUntilBySlideNumber(hiddenPages, pageOrder);
                        }
                    }
                }
            }
        }

        LectureContentUpdateSnapshot afterSnapshot = buildSnapshot(savedAttachmentVideoUnit, projectedSlideHiddenUntilBySlideNumber);
        var updateKinds = lectureContentUpdateClassifierService.classifyAll(beforeSnapshot, afterSnapshot, fileUpdateResult);
        triggerContentProcessingForUpdateKinds(savedAttachmentVideoUnit, afterSnapshot, updateKinds);
        prepareAttachmentVideoUnitForClient(savedAttachmentVideoUnit);

        return savedAttachmentVideoUnit;
    }

    private void triggerContentProcessingForUpdateKinds(AttachmentVideoUnit savedAttachmentVideoUnit, LectureContentUpdateSnapshot afterSnapshot,
            Set<LectureContentUpdateKind> updateKinds) {
        if (updateKinds.isEmpty()) {
            return;
        }

        if (updateKinds.contains(LectureContentUpdateKind.CONTENT)) {
            contentProcessingService.ifPresent(service -> service.triggerProcessing(savedAttachmentVideoUnit));
        }

        if (updateKinds.contains(LectureContentUpdateKind.METADATA)) {
            irisLectureUnitSyncService.markMetadataDirtyAfterCommit(afterSnapshot);
        }

        if (updateKinds.contains(LectureContentUpdateKind.VISIBILITY)) {
            irisLectureUnitSyncService.markVisibilityDirtyAfterCommit(afterSnapshot);
        }
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
        if (existingAttachment.getLink() == null) {
            return Optional.empty();
        }

        try {
            Path existingFilePath = new FileSystemLocation.AttachmentVideoUnitFile(attachmentVideoUnitId, existingAttachment.getLink()).path();
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
     * @param hiddenPages         the hidden pages in the attachment
     */
    private void updateAttachment(Attachment existingAttachment, Attachment updateAttachment, AttachmentVideoUnit attachmentVideoUnit, List<HiddenPageInfoDTO> hiddenPages) {
        // Make sure that the original references are preserved.
        existingAttachment.setAttachmentVideoUnit(attachmentVideoUnit);
        existingAttachment.setReleaseDate(updateAttachment.getReleaseDate());
        existingAttachment.setName(updateAttachment.getName());
        existingAttachment.setAttachmentType(updateAttachment.getAttachmentType());
        if (CollectionUtils.isEmpty(hiddenPages) && existingAttachment.getStudentVersion() != null) {
            existingAttachment.setStudentVersion(null);
        }
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
            // Delete the old student version
            if (attachment.getStudentVersion() != null) {
                Path localPath = new FileSystemLocation.StudentVersionSlides(attachmentVideoUnitId, attachment.getStudentVersion()).path();

                fileService.schedulePathForDeletion(localPath, 0);
                this.fileService.evictCacheForPath(localPath);
            }

            // Update student version of attachment
            Path basePath = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnitId.toString());
            Path savePath = FileUtil.saveFile(studentVersionFile, basePath.resolve("student"), FilePathType.STUDENT_VERSION_SLIDES, true);
            attachment.setStudentVersion(savePath.getFileName().toString());
            attachmentRepository.save(attachment);
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
            this.fileService.evictCacheForPath(new FileSystemLocation.AttachmentVideoUnitFile(attachmentVideoUnit.getId(), attachmentVideoUnit.getAttachment().getLink()).path());
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
