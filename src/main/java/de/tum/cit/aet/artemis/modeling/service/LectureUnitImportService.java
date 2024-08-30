package de.tum.cit.det.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.OnlineUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;

@Profile(PROFILE_CORE)
@Service
public class LectureUnitImportService {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitImportService.class);

    private final LectureUnitRepository lectureUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FileService fileService;

    private final SlideSplitterService slideSplitterService;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final Optional<IrisSettingsRepository> irisSettingsRepository;

    public LectureUnitImportService(LectureUnitRepository lectureUnitRepository, AttachmentRepository attachmentRepository, FileService fileService,
                                    SlideSplitterService slideSplitterService, Optional<PyrisWebhookService> pyrisWebhookService, Optional<IrisSettingsRepository> irisSettingsRepository) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.slideSplitterService = slideSplitterService;
        this.pyrisWebhookService = pyrisWebhookService;
        this.irisSettingsRepository = irisSettingsRepository;
    }

    /**
     * This function imports the lecture units from the {@code importedLecture} and appends them to the {@code lecture}
     *
     * @param importedLecture The original lecture to be copied
     * @param lecture         The new lecture to which the lecture units are appended
     */
    public void importLectureUnits(Lecture importedLecture, Lecture lecture) {
        log.debug("Importing lecture units from lecture with Id {}", importedLecture.getId());
        List<LectureUnit> lectureUnits = new ArrayList<>();
        for (LectureUnit lectureUnit : importedLecture.getLectureUnits()) {
            LectureUnit clonedLectureUnit = importLectureUnit(lectureUnit);
            if (clonedLectureUnit != null) {
                clonedLectureUnit.setLecture(lecture);
                lectureUnits.add(clonedLectureUnit);
            }
        }
        lecture.setLectureUnits(lectureUnits);
        lectureUnitRepository.saveAll(lectureUnits);

        // Send lectures to pyris
        if (pyrisWebhookService.isPresent() && irisSettingsRepository.isPresent()) {
            pyrisWebhookService.get().autoUpdateAttachmentUnitsInPyris(lecture.getCourse().getId(),
                    lectureUnits.stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit).map(lectureUnit -> (AttachmentUnit) lectureUnit).toList());
        }
    }

    /**
     * This function imports the {@code importedLectureUnit} and returns it
     *
     * @param importedLectureUnit The original lecture unit to be copied
     * @return The imported lecture unit
     */
    public LectureUnit importLectureUnit(final LectureUnit importedLectureUnit) {
        log.debug("Creating a new LectureUnit from lecture unit {}", importedLectureUnit);

        switch (importedLectureUnit) {
            case TextUnit importedTextUnit -> {
                TextUnit textUnit = new TextUnit();
                textUnit.setName(importedTextUnit.getName());
                textUnit.setReleaseDate(importedTextUnit.getReleaseDate());
                textUnit.setContent(importedTextUnit.getContent());

                return lectureUnitRepository.save(textUnit);
            }
            case VideoUnit importedVideoUnit -> {
                VideoUnit videoUnit = new VideoUnit();
                videoUnit.setName(importedVideoUnit.getName());
                videoUnit.setReleaseDate(importedVideoUnit.getReleaseDate());
                videoUnit.setDescription(importedVideoUnit.getDescription());
                videoUnit.setSource(importedVideoUnit.getSource());

                return lectureUnitRepository.save(videoUnit);
            }
            case AttachmentUnit importedAttachmentUnit -> {
                // Create and save the attachment unit, then the attachment itself, as the id is needed for file handling
                AttachmentUnit attachmentUnit = new AttachmentUnit();
                attachmentUnit.setDescription(importedAttachmentUnit.getDescription());
                attachmentUnit = lectureUnitRepository.save(attachmentUnit);

                Attachment attachment = importAttachment(attachmentUnit.getId(), importedAttachmentUnit.getAttachment());
                attachment.setAttachmentUnit(attachmentUnit);
                attachmentRepository.save(attachment);
                if (attachment.getLink().endsWith(".pdf")) {
                    slideSplitterService.splitAttachmentUnitIntoSingleSlides(attachmentUnit);
                }
                attachmentUnit.setAttachment(attachment);
                return attachmentUnit;
            }
            case OnlineUnit importedOnlineUnit -> {
                OnlineUnit onlineUnit = new OnlineUnit();
                onlineUnit.setName(importedOnlineUnit.getName());
                onlineUnit.setReleaseDate(importedOnlineUnit.getReleaseDate());
                onlineUnit.setDescription(importedOnlineUnit.getDescription());
                onlineUnit.setSource(importedOnlineUnit.getSource());

                return lectureUnitRepository.save(onlineUnit);
            }
            case ExerciseUnit ignored -> {
                // TODO: Import exercises and link them to the exerciseUnit
                // We have a dedicated exercise import system, so this is left out for now
                return null;
            }
            default -> throw new IllegalArgumentException("Unknown lecture unit type: " + importedLectureUnit.getClass());
        }
    }

    /**
     * This function imports the {@code importedAttachment}, and duplicates its file and returns it
     *
     * @param entityId           The id of the new entity to which the attachment is linked
     * @param importedAttachment The original attachment to be copied
     * @return The imported attachment with the file also duplicated to the temp directory on disk
     */
    public Attachment importAttachment(Long entityId, final Attachment importedAttachment) {
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
