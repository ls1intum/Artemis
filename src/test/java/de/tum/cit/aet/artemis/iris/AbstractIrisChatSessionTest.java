package de.tum.cit.aet.artemis.iris;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Shared setup for Iris chat session tests. Creates a course containing a lecture, a text exercise,
 * and a programming exercise, with Iris activated for each. Four users are created with an LLM
 * opt-in (three students, one tutor, one instructor) so that both resource and service tests can
 * exercise all four chat modes and the typical role variants without rebuilding the fixture.
 */
public abstract class AbstractIrisChatSessionTest extends AbstractIrisIntegrationTest {

    protected static final long NON_EXISTENT_ID = 999_999L;

    @Autowired
    protected CourseUtilService courseUtilService;

    @Autowired
    protected LectureUtilService lectureUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    protected Course course;

    protected Lecture lecture;

    protected TextExercise textExercise;

    protected ProgrammingExercise programmingExercise;

    /**
     * Test-specific prefix used to isolate users and data per test class.
     */
    protected abstract String getTestPrefix();

    @BeforeEach
    void setupChatSessionTestData() throws Exception {
        String prefix = getTestPrefix();

        List<User> users = userUtilService.addUsers(prefix, 3, 1, 0, 1);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(prefix, true, true, 1);
        course = courses.getFirst();

        textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now(), ZonedDateTime.of(2040, 9, 9, 12, 12, 0, 0, ZoneId.of("Europe/Berlin")),
                ZonedDateTime.of(2040, 9, 9, 12, 12, 0, 0, ZoneId.of("Europe/Berlin")));
        StudentParticipation studentParticipation = new StudentParticipation().exercise(textExercise);
        studentParticipation.setParticipant(userUtilService.getUserByLogin(prefix + "student1"));
        studentParticipationRepository.save(studentParticipation);

        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        lecture = lectureUtilService.createLecture(course);

        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(textExercise);
        activateIrisFor(programmingExercise);
    }

    /**
     * Returns the entity ID that the resource expects for the given chat mode: the course ID for
     * COURSE_CHAT, the lecture ID for LECTURE_CHAT, and the respective exercise ID for the two
     * exercise modes.
     */
    protected long entityIdFor(IrisChatMode mode) {
        return switch (mode) {
            case COURSE_CHAT -> course.getId();
            case LECTURE_CHAT -> lecture.getId();
            case TEXT_EXERCISE_CHAT -> textExercise.getId();
            case PROGRAMMING_EXERCISE_CHAT -> programmingExercise.getId();
            case TUTOR_SUGGESTION -> throw new IllegalArgumentException("TUTOR_SUGGESTION is not a chat-session mode");
        };
    }
}
