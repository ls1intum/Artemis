package de.tum.cit.aet.artemis.lecture.service;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.lecture.api.LectureContentProcessingApi;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class LectureUnitImportService {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitImportService.class);

    private final LectureUnitRepository lectureUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final SlideSplitterService slideSplitterService;

    private final Optional<LectureContentProcessingApi> contentProcessingApi;

    public LectureUnitImportService(LectureUnitRepository lectureUnitRepository, AttachmentRepository attachmentRepository, SlideSplitterService slideSplitterService,
            Optional<LectureContentProcessingApi> contentProcessingApi) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.slideSplitterService = slideSplitterService;
        this.contentProcessingApi = contentProcessingApi;
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

        // Trigger full content processing for attachment video units
        // This will check for TUM Live playlist availability, generate transcriptions if possible, and ingest to Pyris
        lectureUnits.stream().filter(lectureUnit -> lectureUnit instanceof AttachmentVideoUnit).map(lectureUnit -> (AttachmentVideoUnit) lectureUnit)
                .forEach(unit -> contentProcessingApi.ifPresent(api -> api.triggerProcessing(unit)));
    }

    /**
     * This function imports the {@code importedLectureUnit} and returns it
     *
     * @param importedLectureUnit The original lecture unit to be copied
     * @param newLecture          The new lecture to which the lecture unit is appended
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
                    attachment = attachmentRepository.saveAndFlush(attachment);
                    attachmentVideoUnit.setAttachment(attachment);
                    if (attachment.getLink().endsWith(".pdf")) {
                        slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnitSlideSplitJob.of(attachmentVideoUnit, null, null));
                    }
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
     * <p>
     * The copy always lands in the directory of the attachment video unit it is created for, whatever directory the
     * original lies in. An attachment video unit created for an attachment that used to hang off a lecture directly
     * keeps that attachment's URI, so the original may still lie under the lecture attachment directory; resolving that
     * URI is the one place the two shapes still differ. Writing the copy there as well would name the new unit's id as
     * a lecture id, and the route that serves those files reads it as one, so the student download would look for the
     * attachment under a lecture that does not have it. Importing therefore also finishes the migration for the copy.
     *
     * @param attachmentVideoUnitId The id of the attachment video unit the attachment is created for
     * @param importedAttachment    The original attachment to be copied
     * @return The imported attachment with the file also duplicated to the temp directory on disk
     */
    private Attachment importAttachment(Long attachmentVideoUnitId, final Attachment importedAttachment) {
        log.debug("Creating a new Attachment from attachment {}", importedAttachment);

        Attachment attachment = new Attachment();
        attachment.setName(importedAttachment.getName());
        attachment.setUploadDate(importedAttachment.getUploadDate());
        attachment.setReleaseDate(importedAttachment.getReleaseDate());
        attachment.setVersion(importedAttachment.getVersion());
        attachment.setAttachmentType(importedAttachment.getAttachmentType());

        // Resolving as an attachment video unit URI covers both shapes: FilePathConverter follows a URI that names the
        // lecture attachment directory to that directory.
        Path oldPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(importedAttachment.getLink()), FilePathType.ATTACHMENT_UNIT);
        Path newPath = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnitId.toString());
        log.debug("Copying attachment file from {} to {}", oldPath, newPath);
        Path savePath = FileUtil.copyExistingFileToTarget(oldPath, newPath, FilePathType.ATTACHMENT_UNIT);
        attachment.setLink(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.ATTACHMENT_UNIT, attachmentVideoUnitId).toString());
        return attachment;
    }
}
