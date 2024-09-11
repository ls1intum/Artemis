package de.tum.cit.aet.artemis.exam;

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

import de.tum.cit.aet.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.course.CourseUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.modeling.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseFactory;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlRepositoryPermission;
import de.tum.cit.aet.artemis.repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.user.UserUtilService;
import de.tum.cit.aet.artemis.util.ExamPrepareExercisesTestUtil;

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

        // TODO: why do we remove the student exams here? This is not really necessary
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
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();

        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        exerciseGroup.addExercise(textExercise);
        exerciseGroupRepository.save(exerciseGroup);
        textExercise = exerciseRepo.save(textExercise);

        createStudentExams(textExercise);

        List<Participation> studentParticipations = invokePrepareExerciseStart();

        for (Participation participation : studentParticipations) {
            assertThat(participation.getExercise()).isEqualTo(textExercise);
            assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam.getExerciseGroups().getFirst());
            assertThat(participation.getSubmissions()).hasSize(1);
            var textSubmission = (TextSubmission) participation.getSubmissions().iterator().next();
            assertThat(textSubmission.getText()).isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStartExercisesWithModelingExercise() throws Exception {
        // creating exercise
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exam.getExerciseGroups().getFirst());
        exam.getExerciseGroups().getFirst().addExercise(modelingExercise);
        exerciseGroupRepository.save(exam.getExerciseGroups().getFirst());
        modelingExercise = exerciseRepo.save(modelingExercise);

        createStudentExams(modelingExercise);

        List<Participation> studentParticipations = invokePrepareExerciseStart();

        for (Participation participation : studentParticipations) {
            assertThat(participation.getExercise()).isEqualTo(modelingExercise);
            assertThat(participation.getExercise().getCourseViaExerciseGroupOrCourseMember()).isNotNull();
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam.getExerciseGroups().getFirst());
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
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam.getExerciseGroups().getFirst());
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
            assertThat(participation.getExercise().getExerciseGroup()).isEqualTo(exam.getExerciseGroups().getFirst());
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
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exam.getExerciseGroups().getFirst());
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));
        programmingExercise = exerciseRepo.save(programmingExercise);
        programmingExercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        exam.getExerciseGroups().getFirst().addExercise(programmingExercise);
        exerciseGroupRepository.save(exam.getExerciseGroups().getFirst());
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
