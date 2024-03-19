package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.user.UserUtilService;

class ExerciseGroupIntegrationJenkinsGitlabTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "gtsettingtest";

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Course course1;

    private Exam exam1;

    private Exam exam2;

    private ExerciseGroup exerciseGroup1;

    private TextExercise textExercise1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course1 = courseUtilService.addEmptyCourse();
        exam1 = examUtilService.addExamWithExerciseGroup(course1, true);
        exam2 = examUtilService.addExamWithExerciseGroup(course1, true);
        exerciseGroup1 = exam1.getExerciseGroups().get(0);
        var textEx = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        textExercise1 = textExerciseRepository.save(textEx);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.FORBIDDEN);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN, ExerciseGroup.class);
        request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", HttpStatus.FORBIDDEN, ExerciseGroup.class);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN);
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/import-exercise-group", List.of(exerciseGroup), ExerciseGroup.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testCreateExerciseGroup_asEditor() throws Exception {
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam1);
        exerciseGroup.setId(55L);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.BAD_REQUEST);
        exerciseGroup = ExamFactory.generateExerciseGroup(true, exam1);
        exerciseGroup.setExam(null);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.CONFLICT);
        exerciseGroup = ExamFactory.generateExerciseGroup(true, exam2);
        exerciseGroup.setTitle("      ExerciseGroup 123       ");
        URI exerciseGroupUri = request.post("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.CREATED);
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam2.getId());
        ExerciseGroup savedExerciseGroup = request.get(String.valueOf(exerciseGroupUri), HttpStatus.OK, ExerciseGroup.class);
        assertThat(savedExerciseGroup.getTitle()).isEqualTo("ExerciseGroup 123");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateExerciseGroup_asEditor() throws Exception {
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(true, exam1);
        exerciseGroup.setExam(null);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.CONFLICT);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup1, HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseGroup_asEditor() throws Exception {
        ExerciseGroup result = request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.OK,
                ExerciseGroup.class);
        verify(examAccessService).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
        assertThat(result.getExam()).isEqualTo(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExerciseGroupsForExam_asEditor() throws Exception {
        List<ExerciseGroup> result = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", HttpStatus.OK, ExerciseGroup.class);
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExercises()).hasSize(1);
        assertThat(result.get(0).getExercises()).contains(textExercise1);
        assertThat(result.get(0).getExam()).isEqualTo(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseGroup_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        assertThat(textExerciseRepository.findById(textExercise1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDeleteExerciseGroup_asEditor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "A,A,B,C", "A,B,C,C", "A,A,B,B" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExerciseGroup_programmingExerciseSameShortNameOrTitle(String shortName1, String shortName2, String title1, String title2) throws Exception {
        Exam exam = ExamFactory.generateExamWithExerciseGroup(course1, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);
        ProgrammingExercise exercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);
        ProgrammingExercise exercise2 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);

        exercise1.setShortName(shortName1);
        exercise2.setShortName(shortName2);
        exercise1.setTitle(title1);
        exercise2.setTitle(title2);
        examRepository.save(exam);

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/import-exercise-group", List.of(exerciseGroup), ExerciseGroup.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_successfulWithExercisesIntoSameExam() throws Exception {
        Exam targetExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);

        final List<ExerciseGroup> exerciseGroupsBefore = targetExam.getExerciseGroups();

        final List<ExerciseGroup> exerciseGroupsNow = request.postListWithResponseBody(
                "/api/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group", exerciseGroupsBefore, ExerciseGroup.class, HttpStatus.OK);

        assertThat(exerciseGroupsNow).hasSize(9).containsAll(exerciseGroupsBefore).allMatch(element -> element.getId() != null);

        for (var exerciseGroup : exerciseGroupsBefore) {
            assertThat(exerciseGroupsNow).filteredOn(element -> Objects.equals(element.getId(), exerciseGroup.getId())).hasSize(1);

            // empty group did not get imported
            assertThat(exerciseGroupsNow).filteredOn(element -> Objects.equals(element.getTitle(), exerciseGroup.getTitle()))
                    .filteredOn(element -> Objects.equals(element.getIsMandatory(), exerciseGroup.getIsMandatory())).hasSize(exerciseGroup.getExercises().isEmpty() ? 1 : 2);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void importExerciseGroup_successfulIntoDifferentExam() throws Exception {
        Exam targetExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);

        Exam secondExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        final List<ExerciseGroup> listSendToServer = secondExam.getExerciseGroups();

        final List<ExerciseGroup> listReceived = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                listSendToServer, ExerciseGroup.class, HttpStatus.OK);

        final List<ExerciseGroup> listExpected = new ArrayList<>(targetExam.getExerciseGroups());
        listExpected.addAll(listSendToServer);

        assertThat(listReceived).hasSize(9);
        for (int i = 0; i <= 4; i++) {
            assertThat(listReceived.get(i)).isEqualTo(listExpected.get(i));
        }
        for (int i = 5; i < 8; i++) {
            assertThat(listReceived.get(i).getId()).isNotNull();
            assertThat(listReceived.get(i).getId()).isNotEqualTo(listExpected.get(i).getId());
            assertThat(listReceived.get(i).getTitle()).isEqualTo(listExpected.get(i).getTitle());
            assertThat(listReceived.get(i).getIsMandatory()).isEqualTo(listExpected.get(i).getIsMandatory());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_successfulWithImportToOtherCourse() throws Exception {
        Course course2 = courseUtilService.addEmptyCourse();
        Exam targetExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course2);

        Exam secondExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        final List<ExerciseGroup> listSendToServer = secondExam.getExerciseGroups();

        final List<ExerciseGroup> listReceived = request.postListWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                listSendToServer, ExerciseGroup.class, HttpStatus.OK);
        assertThat(listReceived).hasSize(9);

        final List<ExerciseGroup> listExpected = new ArrayList<>(targetExam.getExerciseGroups());
        listExpected.addAll(listSendToServer);

        for (int i = 0; i <= 4; i++) {
            assertThat(listReceived.get(i)).isEqualTo(listExpected.get(i));
        }
        for (int i = 5; i < 8; i++) {
            assertThat(listReceived.get(i).getId()).isNotNull();
            assertThat(listReceived.get(i).getId()).isNotEqualTo(listExpected.get(i).getId());
            assertThat(listReceived.get(i).getTitle()).isEqualTo(listExpected.get(i).getTitle());
            assertThat(listReceived.get(i).getIsMandatory()).isEqualTo(listExpected.get(i).getIsMandatory());

            Exercise expected = listReceived.get(i).getExercises().stream().findFirst().orElseThrow();
            Exercise exerciseReceived = listExpected.get(i).getExercises().stream().findFirst().orElseThrow();
            assertThat(exerciseReceived.getId()).isNotEqualTo(expected.getId());
            assertThat(exerciseReceived.getExerciseGroup()).isNotEqualTo(expected.getExerciseGroup());
            assertThat(exerciseReceived.getTitle()).isEqualTo(expected.getTitle());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_preCheckFailed() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        ExerciseGroup programmingGroup = ExamFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);
        ProgrammingExercise programming = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(programmingGroup, ProgrammingLanguage.JAVA);
        programmingGroup.addExercise(programming);
        exerciseRepository.save(programming);

        doReturn(true).when(versionControlService).checkIfProjectExists(any(), any());
        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(any(), any());

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/import-exercise-group", List.of(programmingGroup), ExerciseGroup.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateOrderOfExerciseGroups() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        ExerciseGroup exerciseGroup1 = ExamFactory.generateExerciseGroupWithTitle(true, exam, "first");
        ExerciseGroup exerciseGroup2 = ExamFactory.generateExerciseGroupWithTitle(true, exam, "second");
        ExerciseGroup exerciseGroup3 = ExamFactory.generateExerciseGroupWithTitle(true, exam, "third");
        examRepository.save(exam);

        TextExercise exercise1_1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup1);
        TextExercise exercise1_2 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup1);
        TextExercise exercise2_1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup2);
        TextExercise exercise3_1 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup3);
        TextExercise exercise3_2 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup3);
        TextExercise exercise3_3 = textExerciseUtilService.createTextExerciseForExam(exerciseGroup3);

        List<ExerciseGroup> orderedExerciseGroups = new ArrayList<>(List.of(exerciseGroup2, exerciseGroup3, exerciseGroup1));
        // Should save new order
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam.getId());

        List<ExerciseGroup> savedExerciseGroups = examRepository.findWithExerciseGroupsById(exam.getId()).orElseThrow().getExerciseGroups();
        assertThat(savedExerciseGroups.get(0).getTitle()).isEqualTo("second");
        assertThat(savedExerciseGroups.get(1).getTitle()).isEqualTo("third");
        assertThat(savedExerciseGroups.get(2).getTitle()).isEqualTo("first");

        // Exercises should be preserved
        Exam savedExam = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        ExerciseGroup savedExerciseGroup1 = savedExam.getExerciseGroups().get(2);
        ExerciseGroup savedExerciseGroup2 = savedExam.getExerciseGroups().get(0);
        ExerciseGroup savedExerciseGroup3 = savedExam.getExerciseGroups().get(1);
        assertThat(savedExerciseGroup1.getExercises()).containsExactlyInAnyOrder(exercise1_1, exercise1_2);
        assertThat(savedExerciseGroup2.getExercises()).containsExactlyInAnyOrder(exercise2_1);
        assertThat(savedExerciseGroup3.getExercises()).containsExactlyInAnyOrder(exercise3_1, exercise3_2, exercise3_3);

        // Should fail with too many exercise groups
        orderedExerciseGroups.add(exerciseGroup1);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.BAD_REQUEST);

        // Should fail with too few exercise groups
        orderedExerciseGroups.remove(3);
        orderedExerciseGroups.remove(2);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.BAD_REQUEST);

        // Should fail with different exercise group
        orderedExerciseGroups = Arrays.asList(exerciseGroup2, exerciseGroup3, ExamFactory.generateExerciseGroup(true, exam));
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", orderedExerciseGroups, HttpStatus.BAD_REQUEST);
    }
}
