package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

@Profile(PROFILE_CORE)
@Lazy
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
     * @param importedLecture    The lecture to be imported
     * @param course             The course to import to
     * @param importLectureUnits Whether to import the lecture units of the lecture
     * @return The lecture in the new course
     */
    public Lecture importLecture(final Lecture importedLecture, final Course course, boolean importLectureUnits) {
        log.debug("Creating a new Lecture based on lecture {}", importedLecture);

        // Copy the lecture itself to the new course
        Lecture newLecture = new Lecture();
        newLecture.setTitle(importedLecture.getTitle());
        newLecture.setDescription(importedLecture.getDescription());
        newLecture.setStartDate(importedLecture.getStartDate());
        newLecture.setEndDate(importedLecture.getEndDate());
        /* The visibleDate property of the Lecture entity is deprecated. We’re keeping the related logic temporarily to monitor for user feedback before full removal */
        /* TODO: #11479 - remove the commented out code OR comment back in */
        // lecture.setVisibleDate(importedLecture.getVisibleDate());
        newLecture.setCourse(course);

        newLecture = lectureRepository.save(newLecture);

        if (importLectureUnits) {
            lectureUnitImportService.importLectureUnits(importedLecture, newLecture);
        }
        else {
            importedLecture.setLectureUnits(new ArrayList<>());
        }

        log.debug("Importing attachments from lecture");
        Set<Attachment> attachments = new HashSet<>();
        for (Attachment attachment : importedLecture.getAttachments()) {
            Attachment clonedAttachment = lectureUnitImportService.importAttachment(newLecture.getId(), attachment);
            clonedAttachment.setLecture(newLecture);
            attachments.add(clonedAttachment);
        }
        newLecture.setAttachments(attachments);
        attachmentRepository.saveAll(attachments);

        // Save again to establish the ordered list relationship
        Lecture savedLecture = lectureRepository.save(newLecture);

        channelService.createLectureChannel(savedLecture, Optional.empty());

        return savedLecture;
    }
}
