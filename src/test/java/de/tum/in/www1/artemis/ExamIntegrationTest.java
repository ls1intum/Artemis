package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

public class ExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final int numberOfStudents = 4;

    private final int numberOfTutors = 5;

    private final int numberOfInstructors = 1;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ExamRepository examRepository;

    @Autowired
    ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    StudentExamRepository studentExamRepository;

    @Autowired
    TextExerciseRepository textExerciseRepository;

    private List<User> users;

    private Course course;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(numberOfStudents, numberOfTutors, numberOfInstructors);
        course = database.addEmptyCourse();
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testSaveExamToDatabase() throws Exception {
        ZonedDateTime currentTime = ZonedDateTime.now();

        // create exercise
        TextExercise savedTextExercise1 = textExerciseRepository.save(new TextExercise());
        TextExercise savedTextExercise2 = textExerciseRepository.save(new TextExercise());
        TextExercise savedTextExercise3 = textExerciseRepository.save(new TextExercise());

        // create ExamGroup
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle("Exercise Group Title");
        exerciseGroup.setIsMandatory(true);
        exerciseGroup.addExercise(savedTextExercise1);
        exerciseGroup.addExercise(savedTextExercise2);
        exerciseGroup.addExercise(savedTextExercise3);

        ExerciseGroup savedExerciseGroup1 = exerciseGroupRepository.save(exerciseGroup);
        ExerciseGroup savedExerciseGroup2 = exerciseGroupRepository.save(exerciseGroup);
        ExerciseGroup savedExerciseGroup3 = exerciseGroupRepository.save(exerciseGroup);

        // assert savedExerciseGroup to equal exerciseGroup
        assertThat(savedExerciseGroup1.getTitle()).isEqualTo(exerciseGroup.getTitle());
        assertThat(savedExerciseGroup1.getIsMandatory()).isEqualTo(exerciseGroup.getIsMandatory());
        assertThat(savedExerciseGroup1.getExam()).isEqualTo(exerciseGroup.getExam());
        assertThat(savedExerciseGroup1.getExercises()).isEqualTo(exerciseGroup.getExercises());

        // create exam
        Exam exam = new Exam();
        exam.setTitle("Test exam 1");
        exam.setVisibleDate(currentTime);
        exam.setStartDate(currentTime);
        exam.setEndDate(currentTime);
        exam.setStartText("Start Text");
        exam.setEndText("End Text");
        exam.setConfirmationStartText("Confirmation Start Text");
        exam.setConfirmationEndText("Confirmation End Text");
        exam.setMaxPoints(90);
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(false);
        exam.setCourse(course);
        exam.addExerciseGroup(savedExerciseGroup1);
        exam.addExerciseGroup(savedExerciseGroup2);
        exam.addExerciseGroup(savedExerciseGroup3);
        Exam savedExam = examRepository.save(exam);
        exerciseGroupRepository.save(savedExerciseGroup1);
        exerciseGroupRepository.save(savedExerciseGroup2);
        exerciseGroupRepository.save(savedExerciseGroup3);

        // assert savedExam equals exam
        assertThat(savedExam.getTitle()).isEqualTo(exam.getTitle());
        assertThat(savedExam.getVisibleDate()).isEqualTo(exam.getVisibleDate());
        assertThat(savedExam.getStartDate()).isEqualTo(exam.getStartDate());
        assertThat(savedExam.getEndDate()).isEqualTo(exam.getEndDate());
        assertThat(savedExam.getStartText()).isEqualTo(exam.getStartText());
        assertThat(savedExam.getEndText()).isEqualTo(exam.getEndText());
        assertThat(savedExam.getConfirmationStartText()).isEqualTo(exam.getConfirmationStartText());
        assertThat(savedExam.getConfirmationEndText()).isEqualTo(exam.getConfirmationEndText());
        assertThat(savedExam.getMaxPoints()).isEqualTo(exam.getMaxPoints());
        assertThat(savedExam.getNumberOfExercisesInExam()).isEqualTo(exam.getNumberOfExercisesInExam());
        assertThat(savedExam.getRandomizeExerciseOrder()).isEqualTo(exam.getRandomizeExerciseOrder());
        assertThat(savedExam.getCourse()).isEqualTo(exam.getCourse());
        assertThat(savedExam.getExerciseGroups()).isEqualTo(exam.getExerciseGroups());

        assertThat(savedExam.getExerciseGroups().get(0)).isEqualTo(exam.getExerciseGroups().get(0));
        assertThat(savedExam.getExerciseGroups().get(1)).isEqualTo(exam.getExerciseGroups().get(1));
        assertThat(savedExam.getExerciseGroups().get(2)).isEqualTo(exam.getExerciseGroups().get(2));

        ExerciseGroup updatedExerciseGroup = exerciseGroupRepository.findByIdWithEagerExam(savedExerciseGroup1.getId()).get();
        assertThat(updatedExerciseGroup.getExam()).isEqualTo(savedExam);

        // create studentExam
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(savedExam);
        studentExam.setUser(users.get(0));
        studentExam.addExercise(savedTextExercise1);
        studentExam.addExercise(savedTextExercise2);
        studentExam.addExercise(savedTextExercise3);

        StudentExam savedStudentExam = studentExamRepository.save(studentExam);

        // assert savedStudentExam to equal studentExam
        assertThat(savedStudentExam.getExam()).isEqualTo(studentExam.getExam());
        assertThat(savedStudentExam.getUser()).isEqualTo(studentExam.getUser());
        assertThat(savedStudentExam.getExercises()).isEqualTo(studentExam.getExercises());

        assertThat(savedStudentExam.getExercises().get(0)).isEqualTo(studentExam.getExercises().get(0));
        assertThat(savedStudentExam.getExercises().get(1)).isEqualTo(studentExam.getExercises().get(1));
        assertThat(savedStudentExam.getExercises().get(2)).isEqualTo(studentExam.getExercises().get(2));
    }
}
