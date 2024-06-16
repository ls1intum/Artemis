package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.Slide;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.SlideRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@Service
public class AttachmentUnitService {

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final SlideRepository slideRepository;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final Optional<IrisSettingsRepository> irisSettingsRepository;

    public AttachmentUnitService(SlideRepository slideRepository, SlideSplitterService slideSplitterService, AttachmentUnitRepository attachmentUnitRepository,
            AttachmentRepository attachmentRepository, FileService fileService, Optional<PyrisWebhookService> pyrisWebhookService,
            Optional<IrisSettingsRepository> irisSettingsRepository) {
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.slideSplitterService = slideSplitterService;
        this.slideRepository = slideRepository;
        this.pyrisWebhookService = pyrisWebhookService;
        this.irisSettingsRepository = irisSettingsRepository;
    }

    /**
     * Creates a new attachment unit for the given lecture.
     *
     * @param attachmentUnit The attachmentUnit to create
     * @param attachment     The attachment to create the attachmentUnit for
     * @param lecture        The lecture linked to the attachmentUnit
     * @param file           The file to upload
     * @param keepFilename   Whether to keep the original filename or not.
     * @return The created attachment unit
     */
    public AttachmentUnit createAttachmentUnit(AttachmentUnit attachmentUnit, Attachment attachment, Lecture lecture, MultipartFile file, boolean keepFilename) {
        // persist lecture unit before lecture to prevent "null index column for collection" error
        attachmentUnit.setLecture(null);
        AttachmentUnit savedAttachmentUnit = attachmentUnitRepository.saveAndFlush(attachmentUnit);
        attachmentUnit.setLecture(lecture);
        lecture.addLectureUnit(savedAttachmentUnit);

        handleFile(file, attachment, keepFilename, savedAttachmentUnit.getId());
        // Default attachment
        attachment.setVersion(1);
        attachment.setAttachmentUnit(savedAttachmentUnit);

        Attachment savedAttachment = attachmentRepository.saveAndFlush(attachment);
        savedAttachmentUnit.setAttachment(savedAttachment);
        evictCache(file, savedAttachmentUnit);
        if (pyrisWebhookService.isPresent() && irisSettingsRepository.isPresent()) {
            pyrisWebhookService.get().autoUpdateAttachmentUnitsInPyris(lecture.getCourse().getId(), List.of(savedAttachmentUnit));
        }
        return savedAttachmentUnit;
    }

    /**
     * Updates the provided attachment unit with an optional file.
     *
     * @param existingAttachmentUnit The attachment unit to update.
     * @param updateUnit             The new attachment unit data.
     * @param updateAttachment       The new attachment data.
     * @param updateFile             The optional file.
     * @param keepFilename           Whether to keep the original filename or not.
     * @return The updated attachment unit.
     */
    public AttachmentUnit updateAttachmentUnit(AttachmentUnit existingAttachmentUnit, AttachmentUnit updateUnit, Attachment updateAttachment, MultipartFile updateFile,
            boolean keepFilename) {
        existingAttachmentUnit.setDescription(updateUnit.getDescription());
        existingAttachmentUnit.setName(updateUnit.getName());
        existingAttachmentUnit.setReleaseDate(updateUnit.getReleaseDate());

        AttachmentUnit savedAttachmentUnit = attachmentUnitRepository.saveAndFlush(existingAttachmentUnit);

        Attachment existingAttachment = existingAttachmentUnit.getAttachment();
        if (existingAttachment == null) {
            throw new BadRequestAlertException("Attachment unit must be associated to an attachment", "AttachmentUnit", "attachmentMissing");
        }

        updateAttachment(existingAttachment, updateAttachment, savedAttachmentUnit);
        handleFile(updateFile, existingAttachment, keepFilename, savedAttachmentUnit.getId());
        final int revision = existingAttachment.getVersion() == null ? 1 : existingAttachment.getVersion() + 1;
        existingAttachment.setVersion(revision);
        Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
        savedAttachmentUnit.setAttachment(savedAttachment);
        prepareAttachmentUnitForClient(savedAttachmentUnit);
        evictCache(updateFile, savedAttachmentUnit);

        if (updateFile != null) {
            if (existingAttachmentUnit.getSlides() != null && !existingAttachmentUnit.getSlides().isEmpty()) {
                List<Slide> slides = existingAttachmentUnit.getSlides();
                for (Slide slide : slides) {
                    fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(URI.create(slide.getSlideImagePath())), 5);
                }
                slideRepository.deleteAll(existingAttachmentUnit.getSlides());
            }
            // Split the updated file into single slides only if it is a pdf
            if (Objects.equals(FilenameUtils.getExtension(updateFile.getOriginalFilename()), "pdf")) {
                slideSplitterService.splitAttachmentUnitIntoSingleSlides(savedAttachmentUnit);
            }
            if (pyrisWebhookService.isPresent() && irisSettingsRepository.isPresent()) {
                pyrisWebhookService.get().autoUpdateAttachmentUnitsInPyris(savedAttachmentUnit.getLecture().getCourse().getId(), List.of(savedAttachmentUnit));
            }
        }
        return savedAttachmentUnit;
    }

    /**
     * Sets the required parameters for an attachment on update
     *
     * @param existingAttachment the existing attachment
     * @param updateAttachment   the new attachment containing updated information
     * @param attachmentUnit     the attachment unit to update
     */
    private void updateAttachment(Attachment existingAttachment, Attachment updateAttachment, AttachmentUnit attachmentUnit) {
        // Make sure that the original references are preserved.
        existingAttachment.setAttachmentUnit(attachmentUnit);
        existingAttachment.setReleaseDate(updateAttachment.getReleaseDate());
        existingAttachment.setName(updateAttachment.getName());
        existingAttachment.setReleaseDate(updateAttachment.getReleaseDate());
        existingAttachment.setAttachmentType(updateAttachment.getAttachmentType());
    }

    /**
     * Handles the file after upload if provided.
     *
     * @param file         Potential file to handle
     * @param attachment   Attachment linked to the file.
     * @param keepFilename Whether to keep the original filename or not.
     */
    private void handleFile(MultipartFile file, Attachment attachment, boolean keepFilename, Long attachmentUnitId) {
        if (file != null && !file.isEmpty()) {
            Path basePath = FilePathService.getAttachmentUnitFilePath().resolve(attachmentUnitId.toString());
            Path savePath = fileService.saveFile(file, basePath, keepFilename);
            attachment.setLink(FilePathService.publicPathForActualPathOrThrow(savePath, attachmentUnitId).toString());
            attachment.setUploadDate(ZonedDateTime.now());
        }
    }

    /**
     * If a file was provided the cache for that file gets evicted.
     *
     * @param file           Potential file to evict the cache for.
     * @param attachmentUnit Attachment unit liked to the file.
     */
    private void evictCache(MultipartFile file, AttachmentUnit attachmentUnit) {
        if (file != null && !file.isEmpty()) {
            this.fileService.evictCacheForPath(FilePathService.actualPathForPublicPathOrThrow(URI.create(attachmentUnit.getAttachment().getLink())));
        }
    }

    /**
     * Cleans the attachment unit before sending it to the client and sets the attachment relationship.
     *
     * @param attachmentUnit The attachment unit to clean.
     */
    public void prepareAttachmentUnitForClient(AttachmentUnit attachmentUnit) {
        attachmentUnit.getLecture().setLectureUnits(null);
        attachmentUnit.getLecture().setAttachments(null);
        attachmentUnit.getLecture().setPosts(null);
    }
}
