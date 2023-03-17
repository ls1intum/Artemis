package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class AttachmentUnitService {

    private final Logger log = LoggerFactory.getLogger(AttachmentUnitService.class);

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final CacheManager cacheManager;

    private final LectureRepository lectureRepository;

    private final LectureUnitProcessingService lectureUnitProcessingService;

    public AttachmentUnitService(LectureUnitProcessingService lectureUnitProcessingService, AttachmentUnitRepository attachmentUnitRepository,
            AttachmentRepository attachmentRepository, FileService fileService, CacheManager cacheManager, LectureRepository lectureRepository) {
        this.lectureUnitProcessingService = lectureUnitProcessingService;
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.cacheManager = cacheManager;
        this.lectureRepository = lectureRepository;
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
        lectureRepository.save(lecture);

        handleFile(file, attachment, keepFilename);

        // Default attachment
        attachment.setVersion(1);
        attachment.setAttachmentUnit(savedAttachmentUnit);

        Attachment savedAttachment = attachmentRepository.saveAndFlush(attachment);
        prepareAttachmentUnitForClient(savedAttachmentUnit, savedAttachment);
        evictCache(file, savedAttachmentUnit);

        return savedAttachmentUnit;
    }

    /**
     * Creates new attachment units for the given lecture.
     *
     * @param lectureUnitInformationDTO The split information which contains units as list, number of pages and removeBreakSlide flag
     * @param lecture                   The lecture linked to the attachmentUnits
     * @param file                      The file (lecture slide) to be split
     * @return The created attachment units
     */
    public List<AttachmentUnit> createAttachmentUnits(LectureUnitInformationDTO lectureUnitInformationDTO, Lecture lecture, MultipartFile file) {
        List<AttachmentUnit> createdUnits = new ArrayList<>();
        try {
            log.debug("Splitting attachment file {} with info {}", file, lectureUnitInformationDTO.units());
            List<LectureUnitDTO> lectureUnitsDTO = lectureUnitProcessingService.splitUnits(lectureUnitInformationDTO, file);
            lectureUnitsDTO.forEach(lectureUnit -> {
                lectureUnit.attachmentUnit().setLecture(null);
                AttachmentUnit savedAttachmentUnit = attachmentUnitRepository.saveAndFlush(lectureUnit.attachmentUnit());
                createdUnits.add(savedAttachmentUnit);
                lectureUnit.attachmentUnit().setLecture(lecture);
                lecture.addLectureUnit(savedAttachmentUnit);

                handleFile(lectureUnit.file(), lectureUnit.attachment(), true);

                lectureUnit.attachment().setAttachmentUnit(savedAttachmentUnit);
                lectureUnit.attachment().setVersion(1);

                Attachment savedAttachment = attachmentRepository.saveAndFlush(lectureUnit.attachment());
                lectureUnit.attachmentUnit().setAttachment(savedAttachment);
                evictCache(lectureUnit.file(), savedAttachmentUnit);
            });
            lectureRepository.save(lecture);
        }
        catch (IOException e) {
            log.error("Error while splitting attachment file", e);
            throw new InternalServerErrorException("Could not create attachment units");
        }

        return createdUnits;
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
            throw new ConflictException("Attachment unit must be associated to an attachment", "AttachmentUnit", "attachmentMissing");
        }

        updateAttachment(existingAttachment, updateAttachment, savedAttachmentUnit);
        handleFile(updateFile, existingAttachment, keepFilename);
        final int revision = existingAttachment.getVersion() == null ? 1 : existingAttachment.getVersion() + 1;
        existingAttachment.setVersion(revision);
        Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
        prepareAttachmentUnitForClient(savedAttachmentUnit, savedAttachment);
        evictCache(updateFile, savedAttachmentUnit);

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
    private void handleFile(MultipartFile file, Attachment attachment, boolean keepFilename) {
        if (file != null && !file.isEmpty()) {
            String filePath = fileService.handleSaveFile(file, keepFilename, false);
            attachment.setLink(filePath);
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
            this.cacheManager.getCache("files").evict(fileService.actualPathForPublicPathOrThrow(attachmentUnit.getAttachment().getLink()));
        }
    }

    /**
     * Cleans the attachment unit before sending it to the client and sets the attachment relationship.
     *
     * @param attachmentUnit The attachment unit to clean.
     */
    private void prepareAttachmentUnitForClient(AttachmentUnit attachmentUnit, Attachment attachment) {
        attachmentUnit.getLecture().setLectureUnits(null);
        attachmentUnit.getLecture().setAttachments(null);
        attachmentUnit.getLecture().setPosts(null);
        attachmentUnit.setAttachment(attachment);
    }
}
