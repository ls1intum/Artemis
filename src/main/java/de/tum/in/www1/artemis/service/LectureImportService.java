package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.OnlineUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;

@Profile(PROFILE_CORE)
@Service
public class LectureImportService {

    private static final Logger log = LoggerFactory.getLogger(LectureImportService.class);

    private final LectureRepository lectureRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final Optional<IrisSettingsRepository> irisSettingsRepository;

    public LectureImportService(LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository, AttachmentRepository attachmentRepository,
            Optional<PyrisWebhookService> pyrisWebhookService, Optional<IrisSettingsRepository> irisSettingsRepository) {
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.pyrisWebhookService = pyrisWebhookService;
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
            Attachment clonedAttachment = cloneAttachment(attachment);
            clonedAttachment.setLecture(lecture);
            attachments.add(clonedAttachment);
        }
        lecture.setAttachments(attachments);
        attachmentRepository.saveAll(attachments);
        // Send lectures to pyris
        if (pyrisWebhookService.isPresent() && irisSettingsRepository.isPresent()) {
            pyrisWebhookService.get().autoUpdateAttachmentUnitsInPyris(irisSettingsRepository.get(), lecture.getCourse().getId(),
                    lectureUnits.stream().map(lectureUnit -> (AttachmentUnit) lectureUnit).toList());
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

            Attachment attachment = cloneAttachment(importedAttachmentUnit.getAttachment());
            attachment.setAttachmentUnit(attachmentUnit);
            attachmentRepository.save(attachment);
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
     * @param importedAttachment The original attachment to be copied
     * @return The cloned attachment with the file also duplicated to the temp directory on disk
     */
    private Attachment cloneAttachment(final Attachment importedAttachment) {
        log.debug("Creating a new Attachment from attachment {}", importedAttachment);

        Attachment attachment = new Attachment();
        attachment.setName(importedAttachment.getName());
        attachment.setUploadDate(importedAttachment.getUploadDate());
        attachment.setReleaseDate(importedAttachment.getReleaseDate());
        attachment.setVersion(importedAttachment.getVersion());
        attachment.setAttachmentType(importedAttachment.getAttachmentType());

        Path oldPath = FilePathService.actualPathForPublicPathOrThrow(URI.create(importedAttachment.getLink()));
        Path tempPath = FilePathService.getTempFilePath().resolve(oldPath.getFileName());

        try {
            log.debug("Copying attachment file from {} to {}", oldPath, tempPath);
            FileUtils.copyFile(oldPath.toFile(), tempPath.toFile(), REPLACE_EXISTING);

            // File was copied to a temp directory and will be moved once we persist the attachment
            attachment.setLink(FilePathService.publicPathForActualPathOrThrow(tempPath, null).toString());
        }
        catch (IOException e) {
            log.error("Error while copying file", e);
        }
        return attachment;
    }
}
