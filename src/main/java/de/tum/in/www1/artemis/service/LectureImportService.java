package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;

@Profile(PROFILE_CORE)
@Service
public class LectureImportService {

    private static final Logger log = LoggerFactory.getLogger(LectureImportService.class);

    private final LectureRepository lectureRepository;

    private final AttachmentRepository attachmentRepository;

    private final LectureUnitImportService lectureUnitImportService;

    private final ChannelService channelService;

    public LectureImportService(LectureRepository lectureRepository, AttachmentRepository attachmentRepository, LectureUnitImportService lectureUnitImportService,
            ChannelService channelService) {
        this.lectureRepository = lectureRepository;
        this.attachmentRepository = attachmentRepository;
        this.lectureUnitImportService = lectureUnitImportService;
        this.channelService = channelService;
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

        if (importLectureUnits) {
            lectureUnitImportService.importLectureUnits(importedLecture, lecture);
        }
        else {
            importedLecture.setLectureUnits(new ArrayList<>());
        }

        log.debug("Importing attachments from lecture");
        Set<Attachment> attachments = new HashSet<>();
        for (Attachment attachment : importedLecture.getAttachments()) {
            Attachment clonedAttachment = lectureUnitImportService.importAttachment(lecture.getId(), attachment);
            clonedAttachment.setLecture(lecture);
            attachments.add(clonedAttachment);
        }
        lecture.setAttachments(attachments);
        attachmentRepository.saveAll(attachments);

        // Save again to establish the ordered list relationship
        Lecture savedLecture = lectureRepository.save(lecture);

        channelService.createLectureChannel(savedLecture, Optional.empty());

        return savedLecture;
    }
}
