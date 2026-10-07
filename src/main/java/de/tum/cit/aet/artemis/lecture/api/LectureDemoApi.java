package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.factories.LectureFactory;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;

/**
 * Creates the lectures of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(LectureEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class LectureDemoApi extends AbstractLectureApi {

    /**
     * Title of the demo lecture. Used as the idempotency key of {@link #createDemo(Course)} together with the course, so it must stay stable.
     */
    private static final String DEMO_LECTURE_TITLE = "Demo Lecture";

    /**
     * Name of the demo text unit. Used as the idempotency key of the text unit within the demo lecture, so it must stay stable.
     */
    private static final String DEMO_TEXT_UNIT_NAME = "Demo Text Unit";

    private static final Logger log = LoggerFactory.getLogger(LectureDemoApi.class);

    private final LectureRepository lectureRepository;

    private final TextUnitRepository textUnitRepository;

    private final ChannelService channelService;

    public LectureDemoApi(LectureRepository lectureRepository, TextUnitRepository textUnitRepository, ChannelService channelService) {
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
     * @return the seeded lecture units of the demo lecture, so that dependent modules can link to them. Units that users added to the demo lecture are left out, so that
     *         seeding never links them to anything.
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

        return lectureRepository.findByIdWithLectureUnitsElseThrow(lecture.getId()).getLectureUnits().stream().filter(unit -> DEMO_TEXT_UNIT_NAME.equals(unit.getName())).toList();
    }
}
