package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@Service
public class AttachmentUnitService {

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final CacheManager cacheManager;

    private final LectureRepository lectureRepository;

    public AttachmentUnitService(AttachmentUnitRepository attachmentUnitRepository, AttachmentRepository attachmentRepository, FileService fileService, CacheManager cacheManager,
            LectureRepository lectureRepository) {
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.cacheManager = cacheManager;
        this.lectureRepository = lectureRepository;
    }

    /**
     * Creates a new attachment unit for the given lecture.
     * @param attachmentUnit The attachmentUnit to create
     * @param attachment The attachment to create the attachmentUnit for
     * @param lecture The lecture linked to the attachmentUnit
     * @param file The file to upload
     * @param keepFilename Whether to keep the original filename or not.
     * @return The created attachment unit
     */
    public AttachmentUnit createAttachmentUnit(AttachmentUnit attachmentUnit, Attachment attachment, Lecture lecture, MultipartFile file, boolean keepFilename) {
        // persist lecture unit before lecture to prevent "null index column for collection" error
        attachmentUnit.setLecture(null);
        AttachmentUnit savedAttachmentUnit = attachmentUnitRepository.saveAndFlush(attachmentUnit);
        attachmentUnit.setLecture(lecture);
        lecture.addLectureUnit(savedAttachmentUnit);
        lectureRepository.save(lecture);

        // Default attachment
        attachment.setVersion(0);

        handleFile(file, attachment, keepFilename);
        attachment.setAttachmentUnit(savedAttachmentUnit);
        Attachment savedAttachment = attachmentRepository.saveAndFlush(attachment);

        prepareAttachmentUnitForClient(savedAttachmentUnit, savedAttachment);
        evictCache(file, savedAttachmentUnit);

        return savedAttachmentUnit;
    }

    /**
     * Updates the provided attachment unit with an optional file.
     * @param existingAttachmentUnit The attachment unit to update.
     * @param updateUnit The new attachment unit data.
     * @param updateAttachment The new attachment data.
     * @param updateFile The optional file.
     * @param keepFilename Whether to keep the original filename or not.
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
            throw new ConflictException("Attachment unit must be associated to an attachment", "AttachmentUnit", "attachmentMissing");
        }

        updateAttachment(existingAttachment, updateAttachment, savedAttachmentUnit);
        handleFile(updateFile, existingAttachment, keepFilename);

        Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);

        prepareAttachmentUnitForClient(savedAttachmentUnit, savedAttachment);
        evictCache(updateFile, savedAttachmentUnit);

        return savedAttachmentUnit;
    }

    /**
     * Sets the required parameters for an attachment on update
     * @param existingAttachment the existing attachment
     * @param updateAttachment the new attachment containing updated information
     * @param attachmentUnit the attachment unit to update
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
     * @param file Potential file to handle
     * @param attachment Attachment linked to the file.
     * @param keepFilename Whether to keep the original filename or not.
     */
    private void handleFile(MultipartFile file, Attachment attachment, boolean keepFilename) {
        if (file != null && !file.isEmpty()) {
            String filePath = fileService.handleSaveFile(file, keepFilename, false);
            attachment.setLink(filePath);
            attachment.setVersion(attachment.getVersion() + 1);
            attachment.setUploadDate(ZonedDateTime.now());
        }
    }

    /**
     * If a file was provided the cache for that file gets evicted.
     * @param file Potential file to evict the cache for.
     * @param attachmentUnit Attachment unit liked to the file.
     */
    private void evictCache(MultipartFile file, AttachmentUnit attachmentUnit) {
        if (file != null && !file.isEmpty()) {
            this.cacheManager.getCache("files").evict(fileService.actualPathForPublicPath(attachmentUnit.getAttachment().getLink()));
        }
    }

    /**
     * Cleans the attachment unit before sending it to the client and sets the attachment relationship.
     * @param attachmentUnit The attachment unit to clean.
     */
    private void prepareAttachmentUnitForClient(AttachmentUnit attachmentUnit, Attachment attachment) {
        attachmentUnit.getLecture().setLectureUnits(null);
        attachmentUnit.getLecture().setAttachments(null);
        attachmentUnit.getLecture().setPosts(null);
        attachmentUnit.setAttachment(attachment);
    }
}
