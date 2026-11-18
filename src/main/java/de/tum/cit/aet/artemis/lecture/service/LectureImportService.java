package de.tum.cit.aet.artemis.lecture.service;

import java.util.ArrayList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class LectureImportService {

    private static final Logger log = LoggerFactory.getLogger(LectureImportService.class);

    private final LectureRepository lectureRepository;

    private final LectureUnitImportService lectureUnitImportService;

    private final ChannelService channelService;

    public LectureImportService(LectureRepository lectureRepository, LectureUnitImportService lectureUnitImportService, ChannelService channelService) {
        this.lectureRepository = lectureRepository;
        this.lectureUnitImportService = lectureUnitImportService;
        this.channelService = channelService;
    }

    /**
     * Import the {@code importedLecture} including its lecture units to the {@code course}
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
        newLecture.setCourse(course);

        newLecture = lectureRepository.save(newLecture);

        if (importLectureUnits) {
            lectureUnitImportService.importLectureUnits(importedLecture, newLecture);
        }
        else {
            importedLecture.setLectureUnits(new ArrayList<>());
        }

        // Save again to establish the ordered list relationship
        Lecture savedLecture = lectureRepository.save(newLecture);

        channelService.createLectureChannel(savedLecture, Optional.empty());

        return savedLecture;
    }
}
