package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class LectureUnitImportService {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitImportService.class);

    private final LectureUnitRepository lectureUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final SlideSplitterService slideSplitterService;

    private final Optional<IrisLectureApi> irisLectureApi;

    public LectureUnitImportService(LectureUnitRepository lectureUnitRepository, AttachmentRepository attachmentRepository, SlideSplitterService slideSplitterService,
            Optional<IrisLectureApi> irisLectureApi) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.slideSplitterService = slideSplitterService;
        this.irisLectureApi = irisLectureApi;
    }

    /**
     * This function imports the lecture units from the {@code importedLecture} and appends them to the {@code lecture}
     *
     * @param importedLecture The original lecture to be copied
     * @param newLecture      The new lecture to which the lecture units are appended
     */
    public void importLectureUnits(Lecture importedLecture, Lecture newLecture) {
        log.debug("Importing lecture units from lecture with Id {}", importedLecture.getId());
        List<LectureUnit> lectureUnits = new ArrayList<>();
        for (LectureUnit lectureUnit : importedLecture.getLectureUnits()) {
            LectureUnit clonedLectureUnit = importLectureUnit(lectureUnit, newLecture);
            if (clonedLectureUnit != null) {
                clonedLectureUnit.setLecture(newLecture);
                lectureUnits.add(clonedLectureUnit);
            }
        }
        newLecture.setLectureUnits(lectureUnits);
        lectureUnitRepository.saveAll(lectureUnits);

        // Send lectures to pyris
        irisLectureApi
                .ifPresent(lectureApi -> lectureApi.autoUpdateAttachmentVideoUnitsInPyris(lectureUnits.stream().filter(lectureUnit -> lectureUnit instanceof AttachmentVideoUnit)
                        .map(lectureUnit -> (AttachmentVideoUnit) lectureUnit).filter(unit -> unit.getAttachment() != null).toList()));
    }

    /**
     * This function imports the {@code importedLectureUnit} and returns it
     *
     * @param importedLectureUnit The original lecture unit to be copied
     * @return The imported lecture unit
     */
    public LectureUnit importLectureUnit(final LectureUnit importedLectureUnit, Lecture newLecture) {
        log.debug("Creating a new LectureUnit from lecture unit {}", importedLectureUnit);

        switch (importedLectureUnit) {
            case TextUnit importedTextUnit -> {
                TextUnit textUnit = new TextUnit();
                textUnit.setLecture(newLecture);
                textUnit.setName(importedTextUnit.getName());
                textUnit.setReleaseDate(importedTextUnit.getReleaseDate());
                textUnit.setContent(importedTextUnit.getContent());

                return lectureUnitRepository.save(textUnit);
            }
            case AttachmentVideoUnit importedAttachmentVideoUnit -> {
                // Create and save the attachment video unit, then the attachment itself, as the id is needed for file handling
                AttachmentVideoUnit attachmentVideoUnit = new AttachmentVideoUnit();
                attachmentVideoUnit.setLecture(newLecture);
                attachmentVideoUnit.setName(importedAttachmentVideoUnit.getName());
                attachmentVideoUnit.setReleaseDate(importedAttachmentVideoUnit.getReleaseDate());
                attachmentVideoUnit.setDescription(importedAttachmentVideoUnit.getDescription());
                attachmentVideoUnit.setVideoSource(importedAttachmentVideoUnit.getVideoSource());
                attachmentVideoUnit = lectureUnitRepository.save(attachmentVideoUnit);

                if (importedAttachmentVideoUnit.getAttachment() != null) {
                    Attachment attachment = importAttachment(attachmentVideoUnit.getId(), importedAttachmentVideoUnit.getAttachment());
                    attachment.setAttachmentVideoUnit(attachmentVideoUnit);
                    attachmentRepository.save(attachment);
                    if (attachment.getLink().endsWith(".pdf")) {
                        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(attachmentVideoUnit);
                    }
                    attachmentVideoUnit.setAttachment(attachment);
                }

                return attachmentVideoUnit;
            }
            case OnlineUnit importedOnlineUnit -> {
                OnlineUnit onlineUnit = new OnlineUnit();
                onlineUnit.setLecture(newLecture);
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

        Path oldPath;
        Path newPath;
        FilePathType filePathType;
        if (importedAttachment.getLink().contains("/attachment-unit/")) {
            oldPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(importedAttachment.getLink()), FilePathType.ATTACHMENT_UNIT);
            newPath = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(entityId.toString());
            filePathType = FilePathType.ATTACHMENT_UNIT;
        }
        else {
            oldPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(importedAttachment.getLink()), FilePathType.LECTURE_ATTACHMENT);
            newPath = FilePathConverter.getLectureAttachmentFileSystemPath().resolve(entityId.toString());
            filePathType = FilePathType.LECTURE_ATTACHMENT;
        }
        log.debug("Copying attachment file from {} to {}", oldPath, newPath);
        Path savePath = FileUtil.copyExistingFileToTarget(oldPath, newPath, filePathType);
        attachment.setLink(FilePathConverter.externalUriForFileSystemPath(savePath, filePathType, entityId).toString());
        return attachment;
    }
}
