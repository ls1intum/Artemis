package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.hibernate.Hibernate;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;

@Profile(PROFILE_CORE)
@Service
@Lazy
public class AttachmentVideoUnitService {

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final LectureUnitService lectureUnitService;

    public AttachmentVideoUnitService(SlideSplitterService slideSplitterService, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            AttachmentRepository attachmentRepository, FileService fileService, Optional<IrisLectureApi> irisLectureApi, Optional<CompetencyProgressApi> competencyProgressApi,
            LectureUnitService lectureUnitService) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.slideSplitterService = slideSplitterService;
        this.irisLectureApi = irisLectureApi;
        this.competencyProgressApi = competencyProgressApi;
        this.lectureUnitService = lectureUnitService;
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
        AttachmentVideoUnit savedAttachmentVideoUnit = lectureUnitService.saveWithCompetencyLinks(attachmentVideoUnit, attachmentVideoUnitRepository::saveAndFlush);

        if (attachment != null && file != null) {
            createAttachment(attachment, savedAttachmentVideoUnit, file, keepFilename);
            irisLectureApi.ifPresent(api -> api.autoUpdateAttachmentVideoUnitsInPyris(List.of(savedAttachmentVideoUnit)));
        }

        return savedAttachmentVideoUnit;
    }

    /**
     * Updates the provided attachment video unit with an optional file.
     *
     * @param existingAttachmentVideoUnit The attachment video unit to update.
     * @param updateUnit                  The new attachment video unit data.
     * @param updateAttachment            The new attachment data.
     * @param updateFile                  The optional file.
     * @param keepFilename                Whether to keep the original filename or not.
     * @param hiddenPages                 The hidden pages of attachment video unit.
     * @param pageOrder                   The new order of the edited attachment video unit
     * @return The updated attachment video unit.
     */
    public AttachmentVideoUnit updateAttachmentVideoUnit(AttachmentVideoUnit existingAttachmentVideoUnit, AttachmentVideoUnit updateUnit, Attachment updateAttachment,
            MultipartFile updateFile, boolean keepFilename, List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder) {
        Set<CompetencyLectureUnitLink> existingCompetencyLinks = new HashSet<>(existingAttachmentVideoUnit.getCompetencyLinks());

        existingAttachmentVideoUnit.setDescription(updateUnit.getDescription());
        existingAttachmentVideoUnit.setName(updateUnit.getName());
        existingAttachmentVideoUnit.setReleaseDate(updateUnit.getReleaseDate());
        existingAttachmentVideoUnit.setCompetencyLinks(updateUnit.getCompetencyLinks());
        existingAttachmentVideoUnit.setVideoSource(updateUnit.getVideoSource());

        Attachment existingAttachment = existingAttachmentVideoUnit.getAttachment();

        if (existingAttachment == null && updateAttachment != null) {
            createAttachment(updateAttachment, existingAttachmentVideoUnit, updateFile, keepFilename);
        }

        AttachmentVideoUnit savedAttachmentVideoUnit = lectureUnitService.saveWithCompetencyLinks(existingAttachmentVideoUnit, attachmentVideoUnitRepository::saveAndFlush);

        // Set the original competencies back to the attachment video unit so that the competencyProgressService can determine which competencies changed
        existingAttachmentVideoUnit.setCompetencyLinks(existingCompetencyLinks);
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(existingAttachmentVideoUnit, Optional.of(updateUnit)));

        if (updateAttachment == null) {
            prepareAttachmentVideoUnitForClient(existingAttachmentVideoUnit);
            return existingAttachmentVideoUnit;
        }

        if (existingAttachment != null) {
            updateAttachment(existingAttachment, updateAttachment, savedAttachmentVideoUnit, hiddenPages);
            handleFile(updateFile, existingAttachment, keepFilename, savedAttachmentVideoUnit.getId());
            final int revision = existingAttachment.getVersion() == null ? 1 : existingAttachment.getVersion() + 1;
            existingAttachment.setVersion(revision);
            Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
            savedAttachmentVideoUnit.setAttachment(savedAttachment);
            evictCache(updateFile, savedAttachmentVideoUnit);

            if (updateFile != null) {
                // Split the updated file into single slides only if it is a pdf
                if ("pdf".equalsIgnoreCase(FilenameUtils.getExtension(updateFile.getOriginalFilename()))) {
                    if (pageOrder == null) {
                        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit);
                    }
                    else {
                        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit, hiddenPages, pageOrder);
                    }
                }
            }

            irisLectureApi.ifPresent(api -> api.autoUpdateAttachmentVideoUnitsInPyris(List.of(savedAttachmentVideoUnit)));
        }

        prepareAttachmentVideoUnitForClient(savedAttachmentVideoUnit);

        return savedAttachmentVideoUnit;
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
    // TODO: we should consider using DTOs for sending data to the client instead of cleaning entities
    public void prepareAttachmentVideoUnitForClient(AttachmentVideoUnit attachmentVideoUnit) {
        var lecture = attachmentVideoUnit.getLecture();
        if (Hibernate.isInitialized(lecture.getLectureUnits())) {
            lecture.setLectureUnits(null);
        }
        lecture.setAttachments(null);
    }
}
