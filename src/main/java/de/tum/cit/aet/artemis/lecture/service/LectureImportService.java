package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
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

@Profile(PROFILE_CORE)
@Service
public class LectureImportService {

    private static final Logger log = LoggerFactory.getLogger(LectureImportService.class);

    private final LectureRepository lectureRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final AttachmentRepository attachmentRepository;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final Optional<IrisSettingsRepository> irisSettingsRepository;

    private final LectureUnitImportService lectureUnitImportService;

    public LectureImportService(LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository, AttachmentRepository attachmentRepository,
            Optional<PyrisWebhookService> pyrisWebhookService, Optional<IrisSettingsRepository> irisSettingsRepository, LectureUnitImportService lectureUnitImportService) {
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.attachmentRepository = attachmentRepository;
        this.pyrisWebhookService = pyrisWebhookService;
        this.irisSettingsRepository = irisSettingsRepository;
        this.lectureUnitImportService = lectureUnitImportService;
    }

    /**
     * Import the {@code importedLecture} including its lecture units and attachments to the {@code course}
     *
     * @param importedLecture The lecture to be imported
     * @param course          The course to import to
     * @return The lecture in the new course
     */
    @Transactional // Required to circumvent errors with ordered collection of lecture units
    public Lecture importLecture(final Lecture importedLecture, final Course course, boolean importLectureUnits) {
        log.debug("Creating a new Lecture based on lecture {}", importedLecture);

        // Copy the lecture itself to the new course
        Lecture lecture = new Lecture();
        lecture.setTitle(importedLecture.getTitle());
        lecture.setDescription(importedLecture.getDescription());
        lecture.setStartDate(importedLecture.getStartDate());
        lecture.setEndDate(importedLecture.getEndDate());
        lecture.setVisibleDate(importedLecture.getVisibleDate());
        lecture.setCourse(course);

        lecture = lectureRepository.save(lecture);

        log.debug("Importing attachments from lecture");
        Set<Attachment> attachments = new HashSet<>();
        for (Attachment attachment : importedLecture.getAttachments()) {
            Attachment clonedAttachment = lectureUnitImportService.importAttachment(lecture.getId(), attachment);
            clonedAttachment.setLecture(lecture);
            attachments.add(clonedAttachment);
        }
        lecture.setAttachments(attachments);
        attachmentRepository.saveAll(attachments);

        if (importLectureUnits) {
            log.debug("Importing lecture units from lecture");
            List<LectureUnit> lectureUnits = new ArrayList<>();
            for (LectureUnit lectureUnit : importedLecture.getLectureUnits()) {
                LectureUnit clonedLectureUnit = lectureUnitImportService.importLectureUnit(lectureUnit, lecture);
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

        // Save again to establish the ordered list relationship
        return lectureRepository.save(lecture);
    }
}
