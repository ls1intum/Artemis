package de.tum.cit.aet.artemis.course;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

/**
 * Pins what reading a course costs.
 * <p>
 * Every configuration hanging off {@link Course} is a lazy {@code @OneToOne}, so loading a course reads the course row
 * and nothing else. This pins that, because it is easy to lose: a 2000 student benchmark read
 * {@code course_athena_config} a quarter of a million times while the identically annotated course configuration -
 * whose foreign key is set on every course - was read thirty-five times. The mapping was not the cause, which is what
 * this test establishes; the reads came from queries that fetch it and from code that touches the association.
 */
class CourseConfigurationLoadProfileTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseloadprofile";

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUtilService examUtilService;

    private long courseId;

    private long examId;

    private long exerciseId;

    @BeforeEach
    void setup() {
        Course course = courseUtilService.createCourse();
        CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
        athenaConfig.setGradingFeedbackEnabled(true);
        course.setAthenaConfig(athenaConfig);
        courseId = courseRepository.save(course).getId();

        Exam exam = examUtilService.addExamWithExerciseGroup(courseRepository.findByIdElseThrow(courseId), true);
        examId = exam.getId();
        TextExercise examExercise = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().getFirst());
        exerciseId = exerciseRepository.save(examExercise).getId();
    }

    @Test
    void readingACourseDoesNotReadItsConfigurations() throws Exception {
        assertThatDb(() -> courseRepository.findByIdElseThrow(courseId)).hasBeenCalledTimes(1);
    }

    @Test
    void readingAnExamReadsTheExamAndItsCourseOnly() throws Exception {
        // Exam.course is an eager @ManyToOne, so two rows; the course's configurations must stay out of it
        assertThatDb(() -> examRepository.findByIdElseThrow(examId)).hasBeenCalledAtMostTimes(2);
    }

    @Test
    void readingAnExamExerciseReadsTheChainAndNothingElse() throws Exception {
        // Exercise.exerciseGroup, ExerciseGroup.exam and Exam.course are all eager @ManyToOne, so the chain is read
        // whatever we do. What must not be added on top is the Athena configuration of either course.
        assertThatDb(() -> exerciseRepository.findByIdElseThrow(exerciseId)).hasBeenCalledAtMostTimes(4);
    }
}
