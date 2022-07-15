package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.plagiarism.*;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismVerdictDTO;

public class PlagiarismCaseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    private static Course course;

    private static TextExercise textExercise;

    private static PlagiarismCase plagiarismCase1;

    private static PlagiarismCase plagiarismCase2;

    private static PlagiarismCase plagiarismCase3;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(3, 1, 1, 1);
        course = database.addCourseWithOneFinishedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        plagiarismCase1 = new PlagiarismCase();
        plagiarismCase1.setExercise(textExercise);
        User student1 = database.getUserByLogin("student1");
        plagiarismCase1.setStudent(student1);
        plagiarismCase1 = plagiarismCaseRepository.save(plagiarismCase1);
        PlagiarismResult<TextSubmissionElement> textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison = plagiarismComparisonRepository.save(plagiarismComparison);
        PlagiarismSubmission<TextSubmissionElement> plagiarismSubmission1 = new PlagiarismSubmission<>();
        plagiarismSubmission1.setStudentLogin("student1");
        plagiarismSubmission1.setPlagiarismCase(plagiarismCase1);
        plagiarismSubmission1.setPlagiarismComparison(plagiarismComparison);
        PlagiarismSubmission<TextSubmissionElement> plagiarismSubmission2 = new PlagiarismSubmission<>();
        plagiarismSubmission2.setStudentLogin("student2");
        plagiarismSubmission2.setPlagiarismCase(plagiarismCase1);
        plagiarismSubmission2.setPlagiarismComparison(plagiarismComparison);
        plagiarismComparison.setSubmissionA(plagiarismSubmission1);
        plagiarismComparison.setSubmissionB(plagiarismSubmission2);
        plagiarismComparisonRepository.save(plagiarismComparison);
        plagiarismCase2 = new PlagiarismCase();
        plagiarismCase2.setExercise(textExercise);
        User student2 = database.getUserByLogin("student2");
        plagiarismCase2.setStudent(student2);
        plagiarismCase2 = plagiarismCaseRepository.save(plagiarismCase2);
        plagiarismCase3 = new PlagiarismCase();
        plagiarismCase3.setExercise(textExercise);
        User student3 = database.getUserByLogin("student3");
        plagiarismCase3.setStudent(student3);
        plagiarismCase3 = plagiarismCaseRepository.save(plagiarismCase3);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPlagiarismCasesForCourseForInstructor_forbidden_student() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetPlagiarismCasesForCourseForInstructor_forbidden_tutor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);

    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testGetPlagiarismCasesForCourseForInstructor_forbidden_editor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetPlagiarismCasesForCourseForInstructor() throws Exception {
        var plagiarismCases = request.getList("/api/courses/" + course.getId() + "/plagiarism-cases/for-instructor", HttpStatus.OK, PlagiarismCase.class);
        var plagiarismCasesList = new ArrayList<PlagiarismCase>();
        plagiarismCasesList.add(plagiarismCase1);
        plagiarismCasesList.add(plagiarismCase2);
        plagiarismCasesList.add(plagiarismCase3);
        assertThat(plagiarismCases).as("should get plagiarism cases for instructor").isEqualTo(plagiarismCasesList);
        for (var submission : plagiarismCases.get(0).getPlagiarismSubmissions()) {
            assertThat(submission.getPlagiarismComparison().getPlagiarismResult().getExercise()).as("should prepare plagiarism case response entity").isEqualTo(null);
            assertThat(submission.getPlagiarismComparison().getSubmissionA()).as("should prepare plagiarism case response entity").isEqualTo(null);
            assertThat(submission.getPlagiarismComparison().getSubmissionB()).as("should prepare plagiarism case response entity").isEqualTo(null);
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPlagiarismCaseForInstructor_forbidden_student() throws Exception {
        request.get("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetPlagiarismCaseForInstructor_forbidden_tutor() throws Exception {
        request.get("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR ")
    public void testGetPlagiarismCaseForInstructor_forbidden_editor() throws Exception {
        request.get("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetPlagiarismCaseForInstructor() throws Exception {
        var plagiarismCase = request.get("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-instructor", HttpStatus.OK,
                PlagiarismCase.class);
        assertThat(plagiarismCase).as("should get plagiarism case for instructor").isEqualTo(plagiarismCase1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSavePlagiarismCaseVerdict_forbidden_student() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testSavePlagiarismCaseVerdict_forbidden_tutor() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testSavePlagiarismCaseVerdict_forbidden_editor() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSavePlagiarismCaseVerdict_warning() throws Exception {
        var plagiarismVerdictDTO = new PlagiarismVerdictDTO();
        plagiarismVerdictDTO.setVerdict(PlagiarismVerdict.WARNING);
        plagiarismVerdictDTO.setVerdictMessage("This is a warning!");

        request.put("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/verdict", plagiarismVerdictDTO, HttpStatus.OK);
        var updatedPlagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCase1.getId());
        assertThat(updatedPlagiarismCase.getVerdict()).as("should update plagiarism case verdict warning").isEqualTo(PlagiarismVerdict.WARNING);
        assertThat(updatedPlagiarismCase.getVerdictMessage()).as("should update plagiarism case verdict message").isEqualTo("This is a warning!");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSavePlagiarismCaseVerdict_pointDeduction() throws Exception {
        var plagiarismVerdictDTO = new PlagiarismVerdictDTO();
        plagiarismVerdictDTO.setVerdict(PlagiarismVerdict.POINT_DEDUCTION);
        plagiarismVerdictDTO.setVerdictPointDeduction(90);

        request.put("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/verdict", plagiarismVerdictDTO, HttpStatus.OK);
        var updatedPlagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCase1.getId());
        assertThat(updatedPlagiarismCase.getVerdict()).as("should update plagiarism case verdict point deduction").isEqualTo(PlagiarismVerdict.POINT_DEDUCTION);
        assertThat(updatedPlagiarismCase.getVerdictPointDeduction()).as("should update plagiarism case verdict point deduction").isEqualTo(90);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPlagiarismCaseForExerciseForStudent() throws Exception {
        Post post = new Post();
        post.setAuthor(userRepository.getUserByLoginElseThrow("instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase1);
        post = postRepository.save(post);
        plagiarismCase1.setPost(post);
        plagiarismCaseRepository.save(plagiarismCase1);

        var plagiarismCase = request.get("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-case", HttpStatus.OK, PlagiarismCase.class);
        assertThat(plagiarismCase.getId()).as("should get plagiarism case for exercise for student").isEqualTo(plagiarismCase1.getId());
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void testGetPlagiarismCaseForStudent_forbidden() throws Exception {
        request.get("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-student", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPlagiarismCaseForStudent() throws Exception {
        var plagiarismCase = request.get("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-student", HttpStatus.OK, PlagiarismCase.class);
        assertThat(plagiarismCase).as("should get plagiarism case for student").isEqualTo(plagiarismCase1);
        for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
            assertThat(submission.getPlagiarismComparison().getPlagiarismResult().getExercise()).as("should prepare plagiarism case response entity").isEqualTo(null);
            assertThat(submission.getPlagiarismComparison().getSubmissionA()).as("should prepare plagiarism case response entity").isEqualTo(null);
            assertThat(submission.getPlagiarismComparison().getSubmissionB()).as("should prepare plagiarism case response entity").isEqualTo(null);
        }
    }
}
