package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentUnitRepository;

@Profile(PROFILE_CORE)
@Service
public class AttachmentUnitService {

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final Optional<IrisSettingsRepository> irisSettingsRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final LectureUnitService lectureUnitService;

    public AttachmentUnitService(SlideSplitterService slideSplitterService, AttachmentUnitRepository attachmentUnitRepository, AttachmentRepository attachmentRepository,
            FileService fileService, Optional<PyrisWebhookService> pyrisWebhookService, Optional<IrisSettingsRepository> irisSettingsRepository,
            Optional<CompetencyProgressApi> competencyProgressApi, LectureUnitService lectureUnitService) {
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.slideSplitterService = slideSplitterService;
        this.pyrisWebhookService = pyrisWebhookService;
        this.irisSettingsRepository = irisSettingsRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.lectureUnitService = lectureUnitService;
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

        AttachmentUnit savedAttachmentUnit = lectureUnitService.saveWithCompetencyLinks(attachmentUnit, attachmentUnitRepository::saveAndFlush);

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
     * @param studentVersionFile     The student version of the original file.
     * @param keepFilename           Whether to keep the original filename or not.
     * @param hiddenPages            The hidden pages of attachment unit.
     * @param pageOrder              The new order of the edited attachment unit
     * @return The updated attachment unit.
     */
    public AttachmentUnit updateAttachmentUnit(AttachmentUnit existingAttachmentUnit, AttachmentUnit updateUnit, Attachment updateAttachment, MultipartFile updateFile,
            MultipartFile studentVersionFile, boolean keepFilename, String hiddenPages, String pageOrder) {
        Set<CompetencyLectureUnitLink> existingCompetencyLinks = new HashSet<>(existingAttachmentUnit.getCompetencyLinks());

        existingAttachmentUnit.setDescription(updateUnit.getDescription());
        existingAttachmentUnit.setName(updateUnit.getName());
        existingAttachmentUnit.setReleaseDate(updateUnit.getReleaseDate());
        existingAttachmentUnit.setCompetencyLinks(updateUnit.getCompetencyLinks());

        AttachmentUnit savedAttachmentUnit = lectureUnitService.saveWithCompetencyLinks(existingAttachmentUnit, attachmentUnitRepository::saveAndFlush);

        Attachment existingAttachment = existingAttachmentUnit.getAttachment();
        if (existingAttachment == null) {
            throw new BadRequestAlertException("Attachment unit must be associated to an attachment", "AttachmentUnit", "attachmentMissing");
        }

        updateAttachment(existingAttachment, updateAttachment, savedAttachmentUnit, hiddenPages);
        handleFile(updateFile, existingAttachment, keepFilename, savedAttachmentUnit.getId());
        handleStudentVersionFile(studentVersionFile, existingAttachment, savedAttachmentUnit.getId());
        final int revision = existingAttachment.getVersion() == null ? 1 : existingAttachment.getVersion() + 1;
        existingAttachment.setVersion(revision);
        Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
        savedAttachmentUnit.setAttachment(savedAttachment);
        prepareAttachmentUnitForClient(savedAttachmentUnit);
        evictCache(updateFile, savedAttachmentUnit);

        if (updateFile != null) {
            // Split the updated file into single slides only if it is a pdf
            if (Objects.equals(FilenameUtils.getExtension(updateFile.getOriginalFilename()), "pdf")) {
                if (pageOrder == null) {
                    slideSplitterService.splitAttachmentUnitIntoSingleSlides(savedAttachmentUnit);
                }
                else {
                    slideSplitterService.splitAttachmentUnitIntoSingleSlides(savedAttachmentUnit, hiddenPages, pageOrder);
                }
            }
            if (pyrisWebhookService.isPresent() && irisSettingsRepository.isPresent()) {
                pyrisWebhookService.get().autoUpdateAttachmentUnitsInPyris(savedAttachmentUnit.getLecture().getCourse().getId(), List.of(savedAttachmentUnit));
            }
        }

        // Set the original competencies back to the attachment unit so that the competencyProgressService can determine which competencies changed
        existingAttachmentUnit.setCompetencyLinks(existingCompetencyLinks);
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(existingAttachmentUnit, Optional.of(updateUnit)));

        return savedAttachmentUnit;
    }

    /**
     * Sets the required parameters for an attachment on update
     *
     * @param existingAttachment the existing attachment
     * @param updateAttachment   the new attachment containing updated information
     * @param attachmentUnit     the attachment unit to update
     * @param hiddenPages        the hidden pages in the attachment
     */
    private void updateAttachment(Attachment existingAttachment, Attachment updateAttachment, AttachmentUnit attachmentUnit, String hiddenPages) {
        // Make sure that the original references are preserved.
        existingAttachment.setAttachmentUnit(attachmentUnit);
        existingAttachment.setReleaseDate(updateAttachment.getReleaseDate());
        existingAttachment.setName(updateAttachment.getName());
        existingAttachment.setReleaseDate(updateAttachment.getReleaseDate());
        existingAttachment.setAttachmentType(updateAttachment.getAttachmentType());
        if (hiddenPages == null && existingAttachment.getStudentVersion() != null) {
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
    private void handleFile(MultipartFile file, Attachment attachment, boolean keepFilename, Long attachmentUnitId) {
        if (file != null && !file.isEmpty()) {
            Path basePath = FilePathService.getAttachmentUnitFilePath().resolve(attachmentUnitId.toString());
            Path savePath = fileService.saveFile(file, basePath, keepFilename);
            attachment.setLink(FilePathService.publicPathForActualPathOrThrow(savePath, attachmentUnitId).toString());
            attachment.setUploadDate(ZonedDateTime.now());
        }
    }

    /**
     * Handles the student version file of an attachment, updates its reference in the database,
     * and deletes the old version if it exists.
     *
     * @param studentVersionFile the new student version file to be saved
     * @param attachment         the existing attachment
     * @param attachmentUnitId   the id of the attachment unit
     */
    public void handleStudentVersionFile(MultipartFile studentVersionFile, Attachment attachment, Long attachmentUnitId) {
        if (studentVersionFile != null) {
            // Delete the old student version
            if (attachment.getStudentVersion() != null) {
                URI oldStudentVersionPath = URI.create(attachment.getStudentVersion());
                fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(oldStudentVersionPath), 0);
                this.fileService.evictCacheForPath(FilePathService.actualPathForPublicPathOrThrow(oldStudentVersionPath));
            }

            // Update student version of attachment
            Path basePath = FilePathService.getAttachmentUnitFilePath().resolve(attachmentUnitId.toString());
            Path savePath = fileService.saveFile(studentVersionFile, basePath.resolve("student"), true);
            attachment.setStudentVersion(FilePathService.publicPathForActualPath(savePath, attachmentUnitId).toString());
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
