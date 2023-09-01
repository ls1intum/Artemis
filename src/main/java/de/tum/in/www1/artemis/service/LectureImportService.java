package de.tum.in.www1.artemis.service;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.*;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;

@Service
public class LectureImportService {

    private final Logger log = LoggerFactory.getLogger(LectureImportService.class);

    private final LectureRepository lectureRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final FilePathService filePathService;

    public LectureImportService(LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository, AttachmentRepository attachmentRepository,
            FilePathService filePathService) {
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.filePathService = filePathService;
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

        if (importedLectureUnit instanceof TextUnit) {
            TextUnit textUnit = new TextUnit();
            textUnit.setName(importedLectureUnit.getName());
            textUnit.setReleaseDate(importedLectureUnit.getReleaseDate());
            textUnit.setContent(((TextUnit) importedLectureUnit).getContent());
            return textUnit;
        }
        else if (importedLectureUnit instanceof VideoUnit) {
            VideoUnit videoUnit = new VideoUnit();
            videoUnit.setName(importedLectureUnit.getName());
            videoUnit.setReleaseDate(importedLectureUnit.getReleaseDate());
            videoUnit.setDescription(((VideoUnit) importedLectureUnit).getDescription());
            videoUnit.setSource(((VideoUnit) importedLectureUnit).getSource());
            return videoUnit;
        }
        else if (importedLectureUnit instanceof AttachmentUnit) {
            // Create and save the attachment unit, then the attachment itself, as the id is needed for file handling
            AttachmentUnit attachmentUnit = new AttachmentUnit();
            attachmentUnit.setDescription(((AttachmentUnit) importedLectureUnit).getDescription());
            attachmentUnit.setLecture(newLecture);
            lectureUnitRepository.save(attachmentUnit);

            Attachment attachment = cloneAttachment(((AttachmentUnit) importedLectureUnit).getAttachment());
            attachment.setAttachmentUnit(attachmentUnit);
            attachmentRepository.save(attachment);
            attachmentUnit.setAttachment(attachment);

            return attachmentUnit;
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

        Path oldPath = filePathService.actualPathForPublicPath(URI.create(importedAttachment.getLink()));
        Path tempPath = FilePathService.getTempFilePath().resolve(oldPath.getFileName());

        try {
            log.debug("Copying attachment file from {} to {}", oldPath, tempPath);
            FileUtils.copyFile(oldPath.toFile(), tempPath.toFile(), REPLACE_EXISTING);

            // File was copied to a temp directory and will be moved once we persist the attachment
            attachment.setLink(filePathService.publicPathForActualPath(tempPath, null).toString());
        }
        catch (IOException e) {
            log.error("Error while copying file", e);
        }
        return attachment;
    }
}
