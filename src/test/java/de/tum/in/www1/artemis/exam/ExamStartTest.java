package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ParticipationTestRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlRepositoryPermission;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.ExamPrepareExercisesTestUtil;

// TODO IMPORTANT test more complex exam configurations (mixed exercise type, more variants and more registered students)
class ExamStartTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "examstarttest";

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ParticipationTestRepository participationTestRepository;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course1;

    private Exam exam;

    private static final int NUMBER_OF_STUDENTS = 2;

    private Set<User> registeredUsers;

    private final List<StudentExam> createdStudentExams = new ArrayList<>();

    @BeforeEach
    void initTestCase() throws GitAPIException {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 0, 0, 1);

        course1 = courseUtilService.addEmptyCourse();
        exam = examUtilService.addExamWithExerciseGroup(course1, true);

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        participantScoreScheduleService.activate();

        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        // registering users
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        registeredUsers = Set.of(student1, student2);
        exam.setExamUsers(Set.of(new ExamUser()));
        // setting dates
        exam.setStartDate(ZonedDateTime.now().plusHours(2));
        exam.setEndDate(ZonedDateTime.now().plusHours(3));
        exam.setVisibleDate(ZonedDateTime.now().plusHours(1));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (programmingExerciseTestService.exerciseRepo != null) {
            programmingExerciseTestService.tearDown();
        }

        // Cleanup of Bidirectional Relationships
        for (StudentExam studentExam : createdStudentExams) {
            exam.removeStudentExam(studentExam);
        }
        examRepository.save(exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStartExercisesWithTextExercise() throws Exception {
        // creating exercise
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);

        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        exerciseGroup.addExercise(textExercise);
        exerciseGroupRepository.save(exerciseGroup);
        textExercise = exerciseRepo.save(textExercise);

        createStudentExams(textExercise);

        List<Participation> studentParticipations = invokePrepareExerciseStart();

        for (Participation participation : studentParticipations) {
            assertThat(participation.getExercise()).isEqualTo(textExercise);
            assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam.getExerciseGroups().get(0));
            assertThat(participation.getSubmissions()).hasSize(1);
            var textSubmission = (TextSubmission) participation.getSubmissions().iterator().next();
            assertThat(textSubmission.getText()).isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStartExercisesWithModelingExercise() throws Exception {
        // creating exercise
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exam.getExerciseGroups().get(0));
        exam.getExerciseGroups().get(0).addExercise(modelingExercise);
        exerciseGroupRepository.save(exam.getExerciseGroups().get(0));
        modelingExercise = exerciseRepo.save(modelingExercise);

        createStudentExams(modelingExercise);

        List<Participation> studentParticipations = invokePrepareExerciseStart();

        for (Participation participation : studentParticipations) {
            assertThat(participation.getExercise()).isEqualTo(modelingExercise);
            assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam.getExerciseGroups().get(0));
            assertThat(participation.getSubmissions()).hasSize(1);
            var modelingSubmission = (ModelingSubmission) participation.getSubmissions().iterator().next();
            assertThat(modelingSubmission.getModel()).isNull();
            assertThat(modelingSubmission.getExplanationText()).isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStartExerciseWithProgrammingExercise() throws Exception {
        ProgrammingExercise programmingExercise = createProgrammingExercise();

        participationUtilService.mockCreationOfExerciseParticipation(programmingExercise, versionControlService, continuousIntegrationService);

        createStudentExams(programmingExercise);

        var studentParticipations = invokePrepareExerciseStart();

        for (Participation participation : studentParticipations) {
            assertThat(participation.getExercise()).isEqualTo(programmingExercise);
            assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam.getExerciseGroups().get(0));
            // No initial submissions should be created for programming exercises
            assertThat(participation.getSubmissions()).isEmpty();
            assertThat(((ProgrammingExerciseParticipation) participation).isLocked()).isTrue();
            verify(versionControlService, never()).configureRepository(eq(programmingExercise), (ProgrammingExerciseStudentParticipation) eq(participation), eq(true));
        }
    }

    private static class ExamStartDateSource implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(Arguments.of(ZonedDateTime.now().minusHours(1)), // after exam start
                    Arguments.arguments(ZonedDateTime.now().plusMinutes(3)) // before exam start but after pe unlock date
            );
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @ArgumentsSource(ExamStartDateSource.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStartExerciseWithProgrammingExercise_participationUnlocked(ZonedDateTime startDate) throws Exception {
        exam.setVisibleDate(ZonedDateTime.now().minusHours(2));
        exam.setStartDate(startDate);
        examRepository.save(exam);

        ProgrammingExercise programmingExercise = createProgrammingExercise();

        participationUtilService.mockCreationOfExerciseParticipation(programmingExercise, versionControlService, continuousIntegrationService);

        createStudentExams(programmingExercise);

        var studentParticipations = invokePrepareExerciseStart();

        for (Participation participation : studentParticipations) {
            assertThat(participation.getExercise()).isEqualTo(programmingExercise);
            assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam.getExerciseGroups().get(0));
            // No initial submissions should be created for programming exercises
            assertThat(participation.getSubmissions()).isEmpty();
            ProgrammingExerciseStudentParticipation studentParticipation = (ProgrammingExerciseStudentParticipation) participation;
            // The participation should not get locked if it gets created after the exam already started
            assertThat(studentParticipation.isLocked()).isFalse();
            verify(versionControlService).addMemberToRepository(studentParticipation.getVcsRepositoryUri(), studentParticipation.getStudent().orElseThrow(),
                    VersionControlRepositoryPermission.REPO_WRITE);
        }
    }

    private void createStudentExams(Exercise exercise) {
        // creating student exams
        for (User user : registeredUsers) {
            StudentExam studentExam = new StudentExam();
            studentExam.addExercise(exercise);
            studentExam.setUser(user);
            exam.addStudentExam(studentExam);
            createdStudentExams.add(studentExamRepository.save(studentExam));
        }

        exam = examRepository.save(exam);
    }

    private ProgrammingExercise createProgrammingExercise() {
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exam.getExerciseGroups().get(0));
        programmingExercise = exerciseRepo.save(programmingExercise);
        programmingExercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        exam.getExerciseGroups().get(0).addExercise(programmingExercise);
        exerciseGroupRepository.save(exam.getExerciseGroups().get(0));
        return programmingExercise;
    }

    private List<Participation> invokePrepareExerciseStart() throws Exception {
        // invoke start exercises
        int noGeneratedParticipations = ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course1);
        verify(gitService, times(examUtilService.getNumberOfProgrammingExercises(exam.getId()))).combineAllCommitsOfRepositoryIntoOne(any());
        assertThat(noGeneratedParticipations).isEqualTo(exam.getStudentExams().size());
        return participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
    }

}
