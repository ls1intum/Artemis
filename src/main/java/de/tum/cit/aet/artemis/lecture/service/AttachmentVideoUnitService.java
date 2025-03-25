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
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Profile(PROFILE_CORE)
@Service
public class AttachmentVideoUnitService {

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final SlideRepository slideRepository;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final Optional<IrisSettingsRepository> irisSettingsRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final LectureUnitService lectureUnitService;

    public AttachmentVideoUnitService(SlideRepository slideRepository, SlideSplitterService slideSplitterService, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            AttachmentRepository attachmentRepository, FileService fileService, Optional<PyrisWebhookService> pyrisWebhookService,
            Optional<IrisSettingsRepository> irisSettingsRepository, Optional<CompetencyProgressApi> competencyProgressApi, LectureUnitService lectureUnitService) {
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.slideSplitterService = slideSplitterService;
        this.slideRepository = slideRepository;
        this.pyrisWebhookService = pyrisWebhookService;
        this.irisSettingsRepository = irisSettingsRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.lectureUnitService = lectureUnitService;
    }

    /**
     * Creates a new attachment unit for the given lecture.
     *
     * @param attachmentVideoUnit The attachmentVideoUnit to create
     * @param attachment          The attachment to create the attachmentVideoUnit for
     * @param lecture             The lecture linked to the attachmentVideoUnit
     * @param file                The file to upload
     * @param keepFilename        Whether to keep the original filename or not.
     * @return The created attachment unit
     */
    public AttachmentVideoUnit createAttachmentVideoUnit(AttachmentVideoUnit attachmentVideoUnit, Attachment attachment, Lecture lecture, MultipartFile file,
            boolean keepFilename) {
        // persist lecture unit before lecture to prevent "null index column for collection" error
        attachmentVideoUnit.setLecture(null);

        AttachmentVideoUnit savedAttachmentVideoUnit = lectureUnitService.saveWithCompetencyLinks(attachmentVideoUnit, attachmentVideoUnitRepository::saveAndFlush);

        attachmentVideoUnit.setLecture(lecture);
        lecture.addLectureUnit(savedAttachmentVideoUnit);

        if (attachment != null && file != null) {
            createAttachment(attachment, savedAttachmentVideoUnit, file, keepFilename);
        }

        if (pyrisWebhookService.isPresent() && irisSettingsRepository.isPresent()) {
            pyrisWebhookService.get().autoUpdateAttachmentVideoUnitsInPyris(lecture.getCourse().getId(), List.of(savedAttachmentVideoUnit));
        }
        return savedAttachmentVideoUnit;
    }

    /**
     * Updates the provided attachment unit with an optional file.
     *
     * @param existingAttachmentVideoUnit The attachment unit to update.
     * @param updateUnit                  The new attachment unit data.
     * @param updateAttachment            The new attachment data.
     * @param updateFile                  The optional file.
     * @param keepFilename                Whether to keep the original filename or not.
     * @return The updated attachment unit.
     */
    public AttachmentVideoUnit updateAttachmentVideoUnit(AttachmentVideoUnit existingAttachmentVideoUnit, AttachmentVideoUnit updateUnit, Attachment updateAttachment,
            MultipartFile updateFile, boolean keepFilename) {
        Set<CompetencyLectureUnitLink> existingCompetencyLinks = new HashSet<>(existingAttachmentVideoUnit.getCompetencyLinks());

        existingAttachmentVideoUnit.setDescription(updateUnit.getDescription());
        existingAttachmentVideoUnit.setName(updateUnit.getName());
        existingAttachmentVideoUnit.setReleaseDate(updateUnit.getReleaseDate());
        existingAttachmentVideoUnit.setCompetencyLinks(updateUnit.getCompetencyLinks());
        existingAttachmentVideoUnit.setVideoSource(updateUnit.getVideoSource());

        AttachmentVideoUnit savedAttachmentVideoUnit = lectureUnitService.saveWithCompetencyLinks(existingAttachmentVideoUnit, attachmentVideoUnitRepository::saveAndFlush);

        // Set the original competencies back to the attachment unit so that the competencyProgressService can determine which competencies changed
        existingAttachmentVideoUnit.setCompetencyLinks(existingCompetencyLinks);
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(existingAttachmentVideoUnit, Optional.of(updateUnit)));

        if (updateAttachment == null) {
            deleteSlides(existingAttachmentVideoUnit);

            return existingAttachmentVideoUnit;
        }

        Attachment existingAttachment = existingAttachmentVideoUnit.getAttachment();
        if (existingAttachment == null) {
            createAttachment(updateAttachment, savedAttachmentVideoUnit, updateFile, keepFilename);
        }
        else {
            updateAttachment(existingAttachment, updateAttachment, savedAttachmentVideoUnit);
            handleFile(updateFile, existingAttachment, keepFilename, savedAttachmentVideoUnit.getId());
            final int revision = existingAttachment.getVersion() == null ? 1 : existingAttachment.getVersion() + 1;
            existingAttachment.setVersion(revision);
            Attachment savedAttachment = attachmentRepository.saveAndFlush(existingAttachment);
            savedAttachmentVideoUnit.setAttachment(savedAttachment);
            prepareAttachmentVideoUnitForClient(savedAttachmentVideoUnit);
            evictCache(updateFile, savedAttachmentVideoUnit);

            if (updateFile != null) {
                deleteSlides(existingAttachmentVideoUnit);
                // Split the updated file into single slides only if it is a pdf
                if (Objects.equals(FilenameUtils.getExtension(updateFile.getOriginalFilename()), "pdf")) {
                    slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(savedAttachmentVideoUnit);
                }
            }
        }

        if (pyrisWebhookService.isPresent() && irisSettingsRepository.isPresent()) {
            pyrisWebhookService.get().autoUpdateAttachmentVideoUnitsInPyris(savedAttachmentVideoUnit.getLecture().getCourse().getId(), List.of(savedAttachmentVideoUnit));
        }

        return savedAttachmentVideoUnit;
    }

    private Attachment createAttachment(Attachment attachment, AttachmentVideoUnit attachmentVideoUnit, MultipartFile file, boolean keepFilename) {
        handleFile(file, attachment, keepFilename, attachmentVideoUnit.getId());
        // Default attachment
        attachment.setVersion(1);
        attachment.setAttachmentVideoUnit(attachmentVideoUnit);

        Attachment savedAttachment = attachmentRepository.saveAndFlush(attachment);
        attachmentVideoUnit.setAttachment(savedAttachment);
        evictCache(file, attachmentVideoUnit);
        return savedAttachment;
    }

    private void deleteSlides(AttachmentVideoUnit attachmentVideoUnit) {
        if (attachmentVideoUnit.getSlides() != null && !attachmentVideoUnit.getSlides().isEmpty()) {
            List<Slide> slides = attachmentVideoUnit.getSlides();
            for (Slide slide : slides) {
                fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(URI.create(slide.getSlideImagePath())), 5);
            }
            slideRepository.deleteAll(attachmentVideoUnit.getSlides());
        }
    }

    /**
     * Sets the required parameters for an attachment on update
     *
     * @param existingAttachment  the existing attachment
     * @param updateAttachment    the new attachment containing updated information
     * @param attachmentVideoUnit the attachment unit to update
     */
    private void updateAttachment(Attachment existingAttachment, Attachment updateAttachment, AttachmentVideoUnit attachmentVideoUnit) {
        // Make sure that the original references are preserved.
        existingAttachment.setAttachmentVideoUnit(attachmentVideoUnit);
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
    private void handleFile(MultipartFile file, Attachment attachment, boolean keepFilename, Long attachmentVideoUnitId) {
        if (file != null && !file.isEmpty()) {
            Path basePath = FilePathService.getAttachmentVideoUnitFilePath().resolve(attachmentVideoUnitId.toString());
            Path savePath = fileService.saveFile(file, basePath, keepFilename);
            attachment.setLink(FilePathService.publicPathForActualPathOrThrow(savePath, attachmentVideoUnitId).toString());
            attachment.setUploadDate(ZonedDateTime.now());
        }
    }

    /**
     * If a file was provided the cache for that file gets evicted.
     *
     * @param file                Potential file to evict the cache for.
     * @param attachmentVideoUnit Attachment unit liked to the file.
     */
    private void evictCache(MultipartFile file, AttachmentVideoUnit attachmentVideoUnit) {
        if (file != null && !file.isEmpty()) {
            this.fileService.evictCacheForPath(FilePathService.actualPathForPublicPathOrThrow(URI.create(attachmentVideoUnit.getAttachment().getLink())));
        }
    }

    /**
     * Cleans the attachment unit before sending it to the client and sets the attachment relationship.
     *
     * @param attachmentVideoUnit The attachment unit to clean.
     */
    public void prepareAttachmentVideoUnitForClient(AttachmentVideoUnit attachmentVideoUnit) {
        attachmentVideoUnit.getLecture().setLectureUnits(null);
        attachmentVideoUnit.getLecture().setAttachments(null);
        attachmentVideoUnit.getLecture().setPosts(null);
    }
}
