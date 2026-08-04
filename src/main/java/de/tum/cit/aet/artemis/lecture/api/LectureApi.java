package de.tum.cit.aet.artemis.lecture.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.factories.LectureFactory;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureImportService;
import de.tum.cit.aet.artemis.lecture.service.LectureService;

/**
 * API for managing lectures.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
public class LectureApi extends AbstractLectureApi {

    /**
     * Title of the demo lecture. Used as the idempotency key of {@link #createDemo(Course)} together with the course, so it must stay stable.
     */
    private static final String DEMO_LECTURE_TITLE = "Demo Lecture";

    /**
     * Name of the demo text unit. Used as the idempotency key of the text unit within the demo lecture, so it must stay stable.
     */
    private static final String DEMO_TEXT_UNIT_NAME = "Demo Text Unit";

    private static final Logger log = LoggerFactory.getLogger(LectureApi.class);

    private final LectureService lectureService;

    private final LectureImportService lectureImportService;

    private final LectureRepository lectureRepository;

    private final TextUnitRepository textUnitRepository;

    private final ChannelService channelService;

    public LectureApi(LectureService lectureService, LectureImportService lectureImportService, LectureRepository lectureRepository, TextUnitRepository textUnitRepository,
            ChannelService channelService) {
        this.lectureService = lectureService;
        this.lectureImportService = lectureImportService;
        this.lectureRepository = lectureRepository;
        this.textUnitRepository = textUnitRepository;
        this.channelService = channelService;
    }

    /**
     * Creates the demo lecture with a single text unit in the given course, if they do not exist yet.
     * <p>
     * Lecture and text unit are checked independently, so a deleted text unit is recreated on the next startup without touching the lecture. This mirrors the production creation
     * path (lecture channel, unit order derived from the lecture) rather than saving the entities directly.
     *
     * @param course the demo course the lecture belongs to.
     * @return the lecture units of the demo lecture, so that dependent modules can link to them.
     */
    public List<LectureUnit> createDemo(Course course) {
        Lecture lecture = lectureRepository.findAllByTitleAndCourseIdWithLectureUnits(DEMO_LECTURE_TITLE, course.getId()).stream().findFirst().orElseGet(() -> {
            Lecture newLecture = LectureFactory.generateLecture(DEMO_LECTURE_TITLE, "Demo lecture seeded on startup by the 'demo' profile.", null, null, course);
            Lecture savedLecture = lectureRepository.save(newLecture);
            channelService.createLectureChannel(savedLecture, Optional.empty());
            log.info("Created demo lecture '{}' with id {}", DEMO_LECTURE_TITLE, savedLecture.getId());
            return savedLecture;
        });

        if (lecture.getLectureUnits().stream().noneMatch(unit -> DEMO_TEXT_UNIT_NAME.equals(unit.getName()))) {
            TextUnit textUnit = LectureFactory.generateTextUnit(DEMO_TEXT_UNIT_NAME, "Demo text unit seeded on startup by the 'demo' profile.");
            // The unit order is implicit by position in the lecture's unit list, so the unit has to be persisted through the lecture, see TextUnitResource#createTextUnit.
            lecture.addLectureUnit(textUnit);
            Lecture updatedLecture = lectureRepository.saveAndFlush(lecture);
            TextUnit persistedUnit = (TextUnit) updatedLecture.getLectureUnits().getLast();
            textUnitRepository.save(persistedUnit);
            log.info("Created demo text unit '{}' with id {}", DEMO_TEXT_UNIT_NAME, persistedUnit.getId());
        }

        return lectureRepository.findByIdWithLectureUnitsElseThrow(lecture.getId()).getLectureUnits();
    }

    public Set<Lecture> filterLecturesWithActiveAttachments(Course course, Set<Lecture> lecturesWithAttachments, User user) {
        return lectureService.filterLecturesWithActiveAttachments(course, lecturesWithAttachments, user);
    }

    public Lecture importLecture(final Lecture importedLecture, final Course course, boolean importLectureUnits) {
        return lectureImportService.importLecture(importedLecture, course, importLectureUnits);
    }

    public void delete(Lecture lecture, boolean updateCompetencyProgress) {
        lectureService.delete(lecture, updateCompetencyProgress);
    }

    /**
     * Deletes a lecture by its ID.
     *
     * @param lectureId                the ID of the lecture to delete
     * @param updateCompetencyProgress whether to update competency progress after deletion
     */
    public void deleteById(long lectureId, boolean updateCompetencyProgress) {
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        lectureService.delete(lecture, updateCompetencyProgress);
    }

    /**
     * Finds all lecture IDs for a given course.
     *
     * @param courseId the ID of the course
     * @return set of lecture IDs
     */
    public Set<Long> findLectureIdsByCourseId(long courseId) {
        return lectureRepository.findLectureIdsByCourseId(courseId);
    }

    public Set<CalendarEventDTO> getCalendarEventDTOsFromLectures(long courseId, boolean userIsStudent, Language language) {
        return lectureService.getCalendarEventDTOsFromLectures(courseId, language);
    }
}
