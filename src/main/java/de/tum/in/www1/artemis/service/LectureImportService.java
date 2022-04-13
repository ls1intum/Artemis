package de.tum.in.www1.artemis.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private final FileService fileService;

    public LectureImportService(LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository, AttachmentRepository attachmentRepository,
            FileService fileService) {
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
    }

    /**
     * Import the {@code sourceLecture} including its lecture units to the {@code course}
     *
     * @param importedLecture The lecture to be imported
     * @param course          The course to import to
     * @return The lecture in the new course
     */
    @Transactional
    public Lecture importLecture(final Lecture importedLecture, final Course course) {
        log.debug("Creating a new Lecture based on on lecture {}", importedLecture);

        // Copy the lecture itself to the new course
        Lecture lecture = new Lecture();
        lecture.setCourse(importedLecture.getCourse());
        lecture.setTitle(importedLecture.getTitle());
        lecture.setDescription(importedLecture.getDescription());
        lecture.setStartDate(importedLecture.getStartDate());
        lecture.setEndDate(importedLecture.getEndDate());
        lecture.setCourse(course);

        final Lecture result = lectureRepository.save(lecture);

        log.debug("Importing lecture units from lecture");
        // Import the associated lecture units of the lecture
        result.setLectureUnits(importedLecture.getLectureUnits().stream().map(lectureUnit -> cloneLectureUnit(lectureUnit, result)).filter(Objects::nonNull)
                .map(lectureUnit -> lectureUnitRepository.save(lectureUnit.lecture(result))).collect(Collectors.toList()));

        log.debug("Importing attachments from lecture");
        // Import the attachments of the lecture
        result.setAttachments(
                importedLecture.getAttachments().stream().map(attachment -> attachmentRepository.save(cloneAttachment(attachment).lecture(result))).collect(Collectors.toSet()));

        // Save again to establish the ordered list relationship
        return lectureRepository.save(result);
    }

    /**
     * This helper function clones the {@code importedLectureUnit} and returns it
     *
     * @param importedLectureUnit The original lecture unit to be copied
     * @return The cloned lecture unit
     */
    private LectureUnit cloneLectureUnit(final LectureUnit importedLectureUnit, final Lecture newLecture) {
        log.debug("Creating new LectureUnit from lecture unit {}", importedLectureUnit);

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
            AttachmentUnit attachmentUnit = new AttachmentUnit();
            attachmentUnit.setLecture(newLecture);
            attachmentUnit.setDescription(((AttachmentUnit) importedLectureUnit).getDescription());
            lectureUnitRepository.save(attachmentUnit);

            // Duplicate the associated attachment
            Attachment attachment = cloneAttachment(((AttachmentUnit) importedLectureUnit).getAttachment());
            attachment.setAttachmentUnit(attachmentUnit);
            attachmentRepository.save(attachment);

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
        log.debug("Creating new Attachment from attachment {}", importedAttachment);

        Attachment attachment = new Attachment();
        attachment.setName(importedAttachment.getName());
        attachment.setUploadDate(importedAttachment.getUploadDate());
        attachment.setReleaseDate(importedAttachment.getReleaseDate());
        attachment.setVersion(importedAttachment.getVersion());
        attachment.setAttachmentType(importedAttachment.getAttachmentType());

        Path oldPath = Paths.get(fileService.actualPathForPublicPath(importedAttachment.getLink()));
        Path tempPath = Paths.get(FilePathService.getTempFilePath(), oldPath.getFileName().toString());

        try {
            log.debug("Copying attachment file from {} to {}", oldPath, tempPath);
            Files.copy(new FileInputStream(oldPath.toFile()), tempPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            log.debug(e.getMessage());
            return null;
        }

        // File was copied to a temp directory and will be moved when we persist the attachment below
        attachment.setLink(fileService.publicPathForActualPath(tempPath.toString(), null));
        return attachment;
    }
}
