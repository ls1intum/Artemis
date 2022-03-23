package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.TextAssessmentKnowledgeService;
import de.tum.in.www1.artemis.util.JmsMessageMockProvider;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ExerciseGroupIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextAssessmentKnowledgeService textAssessmentKnowledgeService;

    @Autowired
    private JmsMessageMockProvider jmsMessageMockProvider;

    private Course course1;

    private Exam exam1;

    private Exam exam2;

    private ExerciseGroup exerciseGroup1;

    private TextExercise textExercise1;

    @BeforeEach
    public void initTestCase() {
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
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(true, exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.FORBIDDEN);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN, ExerciseGroup.class);
        request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", HttpStatus.FORBIDDEN, ExerciseGroup.class);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testCreateExerciseGroup_asEditor() throws Exception {
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
    public void testUpdateExerciseGroup_asEditor() throws Exception {
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(true, exam1);
        exerciseGroup.setExam(null);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup, HttpStatus.CONFLICT);
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", exerciseGroup1, HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testGetExerciseGroup_asEditor() throws Exception {
        ExerciseGroup result = request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.OK,
                ExerciseGroup.class);
        verify(examAccessService, times(1)).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.EDITOR, course1.getId(), exam1.getId(), exerciseGroup1);
        assertThat(result.getExam()).isEqualTo(exam1);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testGetExerciseGroupsForExam_asEditor() throws Exception {
        List<ExerciseGroup> result = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups", HttpStatus.OK, ExerciseGroup.class);
        verify(examAccessService, times(1)).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExercises()).hasSize(1);
        assertThat(result.get(0).getExercises()).contains(textExercise1);
        assertThat(result.get(0).getExam()).isEqualTo(exam1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteExerciseGroup_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.OK);
        verify(examAccessService, times(1)).checkCourseAndExamAndExerciseGroupAccessElseThrow(Role.INSTRUCTOR, course1.getId(), exam1.getId(), exerciseGroup1);
        assertThat(textExerciseRepository.findById(textExercise1.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testDeleteExerciseGroup_asEditor() throws Exception {
        jmsMessageMockProvider.mockRemoveExerciseUnits();
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exerciseGroups/" + exerciseGroup1.getId(), HttpStatus.FORBIDDEN);
    }
}
