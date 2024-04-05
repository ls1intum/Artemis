package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.user.UserUtilService;

class LectureUnitServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lecuniservtst";

    @Autowired
    private LectureUnitService lectureUnitService;

    @Autowired
    private LectureUnitCompletionRepository lectureUnitCompletionRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private Lecture lecture;

    private LectureUnit unit1;

    private LectureUnit unit2;

    private User student1;

    @BeforeEach
    void init() {
        lecture = lectureUtilService.createCourseWithLecture(true);
        unit1 = lectureUtilService.createAttachmentUnit(false);
        unit2 = lectureUtilService.createTextUnit();
        lectureUtilService.addLectureUnitsToLecture(lecture, List.of(unit1, unit2));
        student1 = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
    }

    @Test
    void testCompleteAllLectureUnits() {
        lectureUnitService.setCompletedForAllLectureUnits(List.of(unit1, unit2), student1, true);

        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(unit1.getId(), student1.getId())).isPresent();
        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(unit2.getId(), student1.getId())).isPresent();
    }

    @Test
    void testCompleteAllUnitsAlreadyCompleted() {
        LectureUnitCompletion completion = new LectureUnitCompletion();
        completion.setLectureUnit(unit1);
        completion.setUser(student1);
        completion.setCompletedAt(ZonedDateTime.now().minusDays(2));
        lectureUnitCompletionRepository.save(completion);

        lectureUnitService.setCompletedForAllLectureUnits(List.of(unit1, unit2), student1, true);

        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(unit1.getId(), student1.getId())).isPresent();
        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(unit2.getId(), student1.getId())).isPresent();
    }

    @Test
    void testUncompleteAllUnits() {
        LectureUnitCompletion completion = new LectureUnitCompletion();
        completion.setLectureUnit(unit1);
        completion.setUser(student1);
        completion.setCompletedAt(ZonedDateTime.now().minusDays(2));
        lectureUnitCompletionRepository.save(completion);

        lectureUnitService.setCompletedForAllLectureUnits(List.of(unit1, unit2), student1, false);

        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(unit1.getId(), student1.getId())).isEmpty();
        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(unit2.getId(), student1.getId())).isEmpty();
    }
}
