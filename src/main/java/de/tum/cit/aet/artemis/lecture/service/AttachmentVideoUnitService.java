package de.tum.cit.aet.artemis.lecture.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
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
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;

@Conditional(LectureEnabled.class)
@Service
@Lazy
public class AttachmentVideoUnitService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentVideoUnitService.class);

    private static final int HASH_BUFFER_SIZE = 8192;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final LectureUnitService lectureUnitService;

    private final Optional<LectureContentProcessingService> contentProcessingService;

    public AttachmentVideoUnitService(SlideSplitterService slideSplitterService, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            AttachmentRepository attachmentRepository, FileService fileService, Optional<CompetencyProgressApi> competencyProgressApi, LectureUnitService lectureUnitService,
            Optional<LectureContentProcessingService> contentProcessingService) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
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

        if (attachment != null && file != null) {
            createAttachment(attachment, savedAttachmentVideoUnit, file, keepFilename);
        }

        // Trigger automated content processing (transcription and ingestion)
        contentProcessingService.ifPresent(api -> api.triggerProcessing(savedAttachmentVideoUnit));

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
        int payloadFingerprintBeforeUpdate = buildIngestionPayloadFingerprint(existingAttachmentVideoUnit);
        existingAttachmentVideoUnit.setDescription(updateUnitDTO.description());
        existingAttachmentVideoUnit.setName(updateUnitDTO.name());
        existingAttachmentVideoUnit.setReleaseDate(updateUnitDTO.releaseDate());
        existingAttachmentVideoUnit.setVideoSource(updateUnitDTO.videoSource());
        boolean hasUploadedFile = updateFile != null && !updateFile.isEmpty();
        // Note: competency links are updated by the resource layer using lectureUnitService.updateCompetencyLinks

        Attachment existingAttachment = existingAttachmentVideoUnit.getAttachment();
        boolean createdNewAttachment = false;

        if (existingAttachment == null && updateAttachment != null) {
            createAttachment(updateAttachment, existingAttachmentVideoUnit, updateFile, keepFilename);
            createdNewAttachment = true;
        }

        AttachmentVideoUnit savedAttachmentVideoUnit = attachmentVideoUnitRepository.save(existingAttachmentVideoUnit);

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, savedAttachmentVideoUnit));

        // Process attachment if provided
        if (updateAttachment != null) {
            if (createdNewAttachment) {
                // Split PDF files into individual slides for easier navigation
                if (hasUploadedFile && "pdf".equalsIgnoreCase(FilenameUtils.getExtension(updateFile.getOriginalFilename()))) {
                    slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit);
                }
            }
            else if (existingAttachment != null) {
                updateAttachment(existingAttachment, updateAttachment, savedAttachmentVideoUnit, hiddenPages);

                // Re-uploading a file with identical content must not bump the version or trigger a re-ingest.
                // Compare the uploaded content against the stored file and only treat it as a real change if they differ.
                boolean fileContentChanged = hasUploadedFile && !isUploadedFileContentIdenticalToStored(updateFile, existingAttachment);

                // Update file and increment version number only when the uploaded content actually changed
                if (fileContentChanged) {
                    handleFile(updateFile, existingAttachment, keepFilename, savedAttachmentVideoUnit.getId());
                    final int revision = existingAttachment.getVersion() == null ? 1 : existingAttachment.getVersion() + 1;
                    existingAttachment.setVersion(revision);
                }

                Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
                savedAttachmentVideoUnit.setAttachment(savedAttachment);
                evictCache(updateFile, savedAttachmentVideoUnit);

                if (fileContentChanged) {
                    // Split PDF into slides, respecting custom page order if provided
                    if ("pdf".equalsIgnoreCase(FilenameUtils.getExtension(updateFile.getOriginalFilename()))) {
                        if (pageOrder == null) {
                            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit);
                        }
                        else {
                            slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit, hiddenPages, pageOrder);
                        }
                    }
                }
            }
        }

        // Trigger content processing if the ingestion payload changed and prepare unit for client response
        triggerContentProcessingBasedOnPayloadChange(payloadFingerprintBeforeUpdate, savedAttachmentVideoUnit);
        prepareAttachmentVideoUnitForClient(savedAttachmentVideoUnit);

        return savedAttachmentVideoUnit;
    }

    private void triggerContentProcessingBasedOnPayloadChange(int payloadFingerprintBeforeUpdate, AttachmentVideoUnit savedAttachmentVideoUnit) {
        int payloadFingerprintAfterUpdate = buildIngestionPayloadFingerprint(savedAttachmentVideoUnit);
        boolean ingestionPayloadChanged = payloadFingerprintBeforeUpdate != payloadFingerprintAfterUpdate;

        if (!ingestionPayloadChanged) {
            // No changes in the ingestion payload - skip processing entirely
            return;
        }

        // Something changed in the payload (could be metadata or content)
        // Use triggerProcessingForMetadataChange to force reprocessing even if only metadata changed
        contentProcessingService.ifPresent(api -> api.triggerProcessingForMetadataChange(savedAttachmentVideoUnit));
    }

    private int buildIngestionPayloadFingerprint(AttachmentVideoUnit unit) {
        var lecture = unit.getLecture();
        var course = lecture != null ? lecture.getCourse() : null;
        var attachment = unit.getAttachment();
        return Objects.hash(unit.getId(), unit.getName(), lecture != null ? lecture.getId() : null, lecture != null ? lecture.getTitle() : null,
                course != null ? course.getId() : null, course != null ? course.getTitle() : null, course != null && course.getDescription() != null ? course.getDescription() : "",
                attachment != null ? attachment.getVersion() : -1, attachment != null && attachment.getLink() != null ? attachment.getLink() : "",
                unit.getVideoSource() != null && !unit.getVideoSource().isBlank() ? unit.getVideoSource() : null);
    }

    /**
     * Checks whether the uploaded file has the exact same binary content as the file currently stored for the attachment.
     * This is used to avoid bumping the attachment version (and triggering a costly re-ingest) when the same file is re-uploaded.
     *
     * @param uploadedFile       the newly uploaded file
     * @param existingAttachment the attachment whose currently stored file the upload is compared against
     * @return {@code true} if a stored file exists and its content hash matches the uploaded file's content hash, {@code false} otherwise
     */
    private boolean isUploadedFileContentIdenticalToStored(MultipartFile uploadedFile, Attachment existingAttachment) {
        if (existingAttachment == null || existingAttachment.getLink() == null) {
            return false;
        }
        Path existingFilePath = FilePathConverter.fileSystemPathForExternalUri(URI.create(existingAttachment.getLink()), FilePathType.ATTACHMENT_UNIT);
        if (!Files.exists(existingFilePath)) {
            return false;
        }
        try (InputStream uploadedStream = uploadedFile.getInputStream(); InputStream existingStream = Files.newInputStream(existingFilePath)) {
            byte[] uploadedHash = computeContentHash(uploadedStream);
            byte[] existingHash = computeContentHash(existingStream);
            return MessageDigest.isEqual(uploadedHash, existingHash);
        }
        catch (IOException e) {
            // If the comparison fails for any reason, fall back to treating the upload as changed content so that processing still happens.
            log.warn("Could not compare uploaded file with the stored attachment file (attachment {}). Treating the upload as changed content: {}", existingAttachment.getId(),
                    e.getMessage());
            return false;
        }
    }

    /**
     * Computes a SHA-256 hash over the full content of the given input stream. The stream is read in chunks so that large files do not have to be held in memory at once.
     *
     * @param inputStream the stream to hash; the caller is responsible for closing it
     * @return the SHA-256 digest of the stream content
     * @throws IOException if reading the stream fails
     */
    private static byte[] computeContentHash(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[HASH_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return digest.digest();
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private void createAttachment(Attachment attachment, AttachmentVideoUnit attachmentVideoUnit, MultipartFile file, boolean keepFilename) {
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
            // Delete the old student version
            if (attachment.getStudentVersion() != null) {
                URI oldStudentVersionPath = URI.create(attachment.getStudentVersion());
                Path localPath = FilePathConverter.fileSystemPathForExternalUri(oldStudentVersionPath, FilePathType.STUDENT_VERSION_SLIDES);

                fileService.schedulePathForDeletion(localPath, 0);
                this.fileService.evictCacheForPath(localPath);
            }

            // Update student version of attachment
            Path basePath = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnitId.toString());
            Path savePath = FileUtil.saveFile(studentVersionFile, basePath.resolve("student"), FilePathType.STUDENT_VERSION_SLIDES, true);
            attachment.setStudentVersion(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.STUDENT_VERSION_SLIDES, attachmentVideoUnitId).toString());
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
