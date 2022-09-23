package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.TextAssessmentKnowledgeService;
import de.tum.in.www1.artemis.util.ModelFactory;

class ExerciseGroupIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextAssessmentKnowledgeService textAssessmentKnowledgeService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    private Course course1;

    private Exam exam1;

    private Exam exam2;

    private ExerciseGroup exerciseGroup1;

    private TextExercise textExercise1;

    @BeforeEach
    void initTestCase() {
        database.addUsers(1, 1, 1, 1);
        course1 = database.addEmptyCourse();
        exam1 = database.addExamWithExerciseGroup(course1, true);
        exam2 = database.addExamWithExerciseGroup(course1, true);
        exerciseGroup1 = exam1.getExerciseGroups().get(0);
        var textEx = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        textEx.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        textExercise1 = textExerciseRepository.save(textEx);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(true, exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.FORBIDDEN);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN, ExerciseGroup.class);
        request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", HttpStatus.FORBIDDEN, ExerciseGroup.class);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN);
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/import-exercise-group", List.of(exerciseGroup), ExerciseGroup.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testCreateExerciseGroup_asEditor() throws Exception {
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(true, exam1);
        exerciseGroup.setId(55L);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.BAD_REQUEST);
        exerciseGroup = ModelFactory.generateExerciseGroup(true, exam1);
        exerciseGroup.setExam(null);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.CONFLICT);
        exerciseGroup = ModelFactory.generateExerciseGroup(true, exam2);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.CREATED);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testUpdateExerciseGroup_asEditor() throws Exception {
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(true, exam1);
        exerciseGroup.setExam(null);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.CONFLICT);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup1, HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testGetExerciseGroup_asEditor() throws Exception {
        ExerciseGroup result = request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.OK,
                ExerciseGroup.class);
        verify(examAccessService, times(1)).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
        assertThat(result.getExam()).isEqualTo(exam1);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testGetExerciseGroupsForExam_asEditor() throws Exception {
        List<ExerciseGroup> result = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", HttpStatus.OK, ExerciseGroup.class);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExercises()).hasSize(1);
        assertThat(result.get(0).getExercises()).contains(textExercise1);
        assertThat(result.get(0).getExam()).isEqualTo(exam1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseGroup_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        assertThat(textExerciseRepository.findById(textExercise1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testDeleteExerciseGroup_asEditor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_successfulWithExercisesIntoSameExam() throws Exception {
        Exam targetExam = database.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);

        final List<ExerciseGroup> listExpected = targetExam.getExerciseGroups();

        final List<ExerciseGroup> listReceived = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                listExpected, ExerciseGroup.class, HttpStatus.OK);
        assertThat(listReceived.size()).isEqualTo(9);

        // We import into the same exam -> double the exercise groups
        listExpected.addAll(listExpected);

        // The first 5 should be the old ones
        for (int i = 0; i <= 4; i++) {
            assertThat(listReceived.get(i)).isEqualTo(listExpected.get(i));
        }
        // The last 4
        for (int i = 5; i <= 8; i++) {
            assertThat(listReceived.get(i).getId()).isNotNull();
            assertThat(listReceived.get(i).getId()).isNotEqualTo(listExpected.get(i).getId());
            assertThat(listReceived.get(i).getTitle()).isEqualTo(listExpected.get(i).getTitle());
            assertThat(listReceived.get(i).getIsMandatory()).isEqualTo(listExpected.get(i).getIsMandatory());
        }
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void importExerciseGroup_successfulIntoDifferentExam() throws Exception {
        Exam targetExam = database.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);

        Exam secondExam = database.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        final List<ExerciseGroup> listSendToServer = secondExam.getExerciseGroups();

        final List<ExerciseGroup> listReceived = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                listSendToServer, ExerciseGroup.class, HttpStatus.OK);

        final List<ExerciseGroup> listExpected = new ArrayList<>(targetExam.getExerciseGroups());
        listExpected.addAll(listSendToServer);

        assertThat(listReceived.size()).isEqualTo(9);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_successfulWithImportToOtherCourse() throws Exception {
        Course course2 = database.addEmptyCourse();
        Exam targetExam = database.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course2);

        Exam secondExam = database.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        final List<ExerciseGroup> listSendToServer = secondExam.getExerciseGroups();

        final List<ExerciseGroup> listReceived = request.postListWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                listSendToServer, ExerciseGroup.class, HttpStatus.OK);
        assertThat(listReceived.size()).isEqualTo(9);

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

            Exercise expected = listReceived.get(i).getExercises().stream().findFirst().get();
            Exercise exerciseReceived = listExpected.get(i).getExercises().stream().findFirst().get();
            assertThat(exerciseReceived.getId()).isNotEqualTo(expected.getId());
            assertThat(exerciseReceived.getExerciseGroup()).isNotEqualTo(expected.getExerciseGroup());
            assertThat(exerciseReceived.getTitle()).isEqualTo(expected.getTitle());
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importExerciseGroup_preCheckFailed() throws Exception {
        Exam exam = ModelFactory.generateExam(course1);
        ExerciseGroup programmingGroup = ModelFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);
        ProgrammingExercise programming = ModelFactory.generateProgrammingExerciseForExam(programmingGroup, ProgrammingLanguage.JAVA);
        programmingGroup.addExercise(programming);
        exerciseRepository.save(programming);

        doReturn(true).when(versionControlService).checkIfProjectExists(any(), any());
        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(any(), any());

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/import-exercise-group", List.of(programmingGroup), ExerciseGroup.class,
                HttpStatus.BAD_REQUEST);
    }
}
