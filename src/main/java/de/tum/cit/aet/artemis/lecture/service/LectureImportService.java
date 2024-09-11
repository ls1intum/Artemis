package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.domain.VideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.SlideSplitterService;

@Profile(PROFILE_CORE)
@Service
public class LectureImportService {

    private static final Logger log = LoggerFactory.getLogger(LectureImportService.class);

    private final LectureRepository lectureRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final Optional<IrisSettingsRepository> irisSettingsRepository;

    public LectureImportService(LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository, AttachmentRepository attachmentRepository,
            Optional<PyrisWebhookService> pyrisWebhookService, FileService fileService, SlideSplitterService slideSplitterService,
            Optional<IrisSettingsRepository> irisSettingsRepository) {
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.pyrisWebhookService = pyrisWebhookService;
        this.fileService = fileService;
        this.slideSplitterService = slideSplitterService;
        this.irisSettingsRepository = irisSettingsRepository;
    }

    /**
     * Import the {@code importedLecture} including its lecture units and attachments to the {@code course}
     *
     * @param importedLecture The lecture to be imported
     * @param course          The course to import to
     * @return The lecture in the new course
     */
    @Transactional // Required to circumvent errors with ordered collection of lecture units
    public Lecture importLecture(final Lecture importedLecture, final Course course) {
        log.debug("Creating a new Lecture based on lecture {}", importedLecture);

        // Copy the lecture itself to the new course
        Lecture lecture = new Lecture();
        lecture.setTitle(importedLecture.getTitle());
        lecture.setDescription(importedLecture.getDescription());
        lecture.setStartDate(importedLecture.getStartDate());
        lecture.setEndDate(importedLecture.getEndDate());
        lecture.setVisibleDate(importedLecture.getVisibleDate());

        lecture = lectureRepository.save(lecture);
        course.addLectures(lecture);

        log.debug("Importing lecture units from lecture");
        List<LectureUnit> lectureUnits = new ArrayList<>();
        for (LectureUnit lectureUnit : importedLecture.getLectureUnits()) {
            LectureUnit clonedLectureUnit = cloneLectureUnit(lectureUnit, lecture);
            if (clonedLectureUnit != null) {
                clonedLectureUnit.setLecture(lecture);
                lectureUnits.add(clonedLectureUnit);
            }
        }
        lecture.setLectureUnits(lectureUnits);
        lectureUnitRepository.saveAll(lectureUnits);

        log.debug("Importing attachments from lecture");
        Set<Attachment> attachments = new HashSet<>();
        for (Attachment attachment : importedLecture.getAttachments()) {
            Attachment clonedAttachment = cloneAttachment(lecture.getId(), attachment);
            clonedAttachment.setLecture(lecture);
            attachments.add(clonedAttachment);
        }
        lecture.setAttachments(attachments);
        attachmentRepository.saveAll(attachments);

        // Send lectures to pyris
        if (pyrisWebhookService.isPresent() && irisSettingsRepository.isPresent()) {
            pyrisWebhookService.get().autoUpdateAttachmentUnitsInPyris(lecture.getCourse().getId(),
                    lectureUnits.stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit).map(lectureUnit -> (AttachmentUnit) lectureUnit).toList());
        }
        // Save again to establish the ordered list relationship
        return lectureRepository.save(lecture);
    }

    /**
     * This helper function clones the {@code importedLectureUnit} and returns it
     *
     * @param importedLectureUnit The original lecture unit to be copied
     * @param newLecture          The new lecture to which the lecture units are appended
     * @return The cloned lecture unit
     */
    private LectureUnit cloneLectureUnit(final LectureUnit importedLectureUnit, final Lecture newLecture) {
        log.debug("Creating a new LectureUnit from lecture unit {}", importedLectureUnit);

        if (importedLectureUnit instanceof TextUnit importedTextUnit) {
            TextUnit textUnit = new TextUnit();
            textUnit.setName(importedTextUnit.getName());
            textUnit.setReleaseDate(importedTextUnit.getReleaseDate());
            textUnit.setContent(importedTextUnit.getContent());
            return textUnit;
        }
        else if (importedLectureUnit instanceof VideoUnit importedVideoUnit) {
            VideoUnit videoUnit = new VideoUnit();
            videoUnit.setName(importedVideoUnit.getName());
            videoUnit.setReleaseDate(importedVideoUnit.getReleaseDate());
            videoUnit.setDescription(importedVideoUnit.getDescription());
            videoUnit.setSource(importedVideoUnit.getSource());
            return videoUnit;
        }
        else if (importedLectureUnit instanceof AttachmentUnit importedAttachmentUnit) {
            // Create and save the attachment unit, then the attachment itself, as the id is needed for file handling
            AttachmentUnit attachmentUnit = new AttachmentUnit();
            attachmentUnit.setDescription(importedAttachmentUnit.getDescription());
            attachmentUnit.setLecture(newLecture);
            lectureUnitRepository.save(attachmentUnit);

            Attachment attachment = cloneAttachment(attachmentUnit.getId(), importedAttachmentUnit.getAttachment());
            attachment.setAttachmentUnit(attachmentUnit);
            attachmentRepository.save(attachment);
            if (attachment.getLink().endsWith(".pdf")) {
                slideSplitterService.splitAttachmentUnitIntoSingleSlides(attachmentUnit);
            }
            attachmentUnit.setAttachment(attachment);
            return attachmentUnit;
        }
        else if (importedLectureUnit instanceof OnlineUnit importedOnlineUnit) {
            OnlineUnit onlineUnit = new OnlineUnit();
            onlineUnit.setName(importedOnlineUnit.getName());
            onlineUnit.setReleaseDate(importedOnlineUnit.getReleaseDate());
            onlineUnit.setDescription(importedOnlineUnit.getDescription());
            onlineUnit.setSource(importedOnlineUnit.getSource());

            return onlineUnit;
        }
        else if (importedLectureUnit instanceof ExerciseUnit) {
            // TODO: Import exercises and link them to the exerciseUnit
            // We have a dedicated exercise import system, so this is left out for now
            return null;
        }
        return null;
    }

    /**
     * This helper function clones the {@code importedAttachment} (and duplicates its file) and returns it
     *
     * @param entityId           The id of the new entity to which the attachment is linked
     * @param importedAttachment The original attachment to be copied
     * @return The cloned attachment with the file also duplicated to the temp directory on disk
     */
    private Attachment cloneAttachment(Long entityId, final Attachment importedAttachment) {
        log.debug("Creating a new Attachment from attachment {}", importedAttachment);

        Attachment attachment = new Attachment();
        attachment.setName(importedAttachment.getName());
        attachment.setUploadDate(importedAttachment.getUploadDate());
        attachment.setReleaseDate(importedAttachment.getReleaseDate());
        attachment.setVersion(importedAttachment.getVersion());
        attachment.setAttachmentType(importedAttachment.getAttachmentType());

        Path oldPath = FilePathService.actualPathForPublicPathOrThrow(URI.create(importedAttachment.getLink()));
        Path newPath;
        if (oldPath.toString().contains("/attachment-unit/")) {
            newPath = FilePathService.getAttachmentUnitFilePath().resolve(entityId.toString());
        }
        else {
            newPath = FilePathService.getLectureAttachmentFilePath().resolve(entityId.toString());
        }
        log.debug("Copying attachment file from {} to {}", oldPath, newPath);
        Path savePath = fileService.copyExistingFileToTarget(oldPath, newPath);
        attachment.setLink(FilePathService.publicPathForActualPathOrThrow(savePath, entityId).toString());
        return attachment;
    }
}
