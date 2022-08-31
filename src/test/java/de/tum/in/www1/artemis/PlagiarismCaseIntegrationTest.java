package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

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
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseInfoDTO;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismVerdictDTO;

class PlagiarismCaseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    private static List<PlagiarismCase> plagiarismCases;

    @BeforeEach
    void initTestCase() {
        // We need at least 3 cases
        plagiarismCases = this.createPlagiarismCases(100);
        plagiarismCase1 = plagiarismCases.get(0);
        plagiarismCase2 = plagiarismCases.get(1);
        plagiarismCase3 = plagiarismCases.get(2);
    }

    /***
     * Create a given amount of plagiarism cases
     * @param numberOfPlagiarismCases The required number of cases
     * @return The list of generated plagiarism cases
     */
    private List<PlagiarismCase> createPlagiarismCases(int numberOfPlagiarismCases) {
        var plagiarismCasesList = new ArrayList<PlagiarismCase>();

        // Per case, we have always 2 students
        database.addUsers(numberOfPlagiarismCases * 2, 1, 1, 1);
        course = database.addCourseWithOneFinishedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);

        for (int i = 0; i < numberOfPlagiarismCases; i++) {
            PlagiarismCase plagiarismCase = new PlagiarismCase();
            User student = database.getUserByLogin("student" + (i + 1));
            PlagiarismResult<TextSubmissionElement> textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
            PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();

            PlagiarismSubmission<TextSubmissionElement> plagiarismSubmission1 = new PlagiarismSubmission<>();
            PlagiarismSubmission<TextSubmissionElement> plagiarismSubmission2 = new PlagiarismSubmission<>();

            plagiarismCase.setExercise(textExercise);
            plagiarismCase.setStudent(student);
            plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

            plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
            plagiarismComparison = plagiarismComparisonRepository.save(plagiarismComparison);

            plagiarismSubmission1.setStudentLogin("student" + (i + 1));
            plagiarismSubmission1.setPlagiarismCase(plagiarismCase);
            plagiarismSubmission1.setPlagiarismComparison(plagiarismComparison);
            plagiarismSubmission2.setStudentLogin("student" + (i + 2));
            plagiarismSubmission2.setPlagiarismCase(plagiarismCase);
            plagiarismSubmission2.setPlagiarismComparison(plagiarismComparison);
            plagiarismComparison.setSubmissionA(plagiarismSubmission1);
            plagiarismComparison.setSubmissionB(plagiarismSubmission2);
            plagiarismComparisonRepository.save(plagiarismComparison);
            plagiarismCasesList.add(plagiarismCase);
        }

        return plagiarismCasesList;
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPlagiarismCasesForCourseForInstructor_forbidden_student() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetPlagiarismCasesForCourseForInstructor_forbidden_tutor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);

    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testGetPlagiarismCasesForCourseForInstructor_forbidden_editor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismCasesForCourseForInstructor() throws Exception {
        var plagiarismCasesResponse = request.getList("/api/courses/" + course.getId() + "/plagiarism-cases/for-instructor", HttpStatus.OK, PlagiarismCase.class);
        var plagiarismCasesList = plagiarismCases;
        assertThat(plagiarismCasesResponse).as("should get plagiarism cases for instructor").isEqualTo(plagiarismCasesList);
        for (var submission : plagiarismCasesResponse.get(0).getPlagiarismSubmissions()) {
            assertThat(submission.getPlagiarismComparison().getPlagiarismResult().getExercise()).as("should prepare plagiarism case response entity").isEqualTo(null);
            assertThat(submission.getPlagiarismComparison().getSubmissionA()).as("should prepare plagiarism case response entity").isEqualTo(null);
            assertThat(submission.getPlagiarismComparison().getSubmissionB()).as("should prepare plagiarism case response entity").isEqualTo(null);
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPlagiarismCaseForInstructor_forbidden_student() throws Exception {
        request.get("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetPlagiarismCaseForInstructor_forbidden_tutor() throws Exception {
        request.get("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR ")
    void testGetPlagiarismCaseForInstructor_forbidden_editor() throws Exception {
        request.get("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismCaseForInstructor() throws Exception {
        var plagiarismCase = request.get("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-instructor", HttpStatus.OK,
                PlagiarismCase.class);
        assertThat(plagiarismCase).as("should get plagiarism case for instructor").isEqualTo(plagiarismCase1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSavePlagiarismCaseVerdict_forbidden_student() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(NO_PLAGIARISM, "", 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSavePlagiarismCaseVerdict_forbidden_tutor() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(NO_PLAGIARISM, "", 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testSavePlagiarismCaseVerdict_forbidden_editor() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(NO_PLAGIARISM, "", 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSavePlagiarismCaseVerdict_warning() throws Exception {
        var plagiarismVerdictDTO = new PlagiarismVerdictDTO(WARNING, "This is a warning!", 0);
        request.put("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/verdict", plagiarismVerdictDTO, HttpStatus.OK);
        var updatedPlagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCase1.getId());
        assertThat(updatedPlagiarismCase.getVerdict()).as("should update plagiarism case verdict warning").isEqualTo(PlagiarismVerdict.WARNING);
        assertThat(updatedPlagiarismCase.getVerdictMessage()).as("should update plagiarism case verdict message").isEqualTo("This is a warning!");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSavePlagiarismCaseVerdict_pointDeduction() throws Exception {
        var plagiarismVerdictDTO = new PlagiarismVerdictDTO(POINT_DEDUCTION, "", 90);
        request.put("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/verdict", plagiarismVerdictDTO, HttpStatus.OK);
        var updatedPlagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCase1.getId());
        assertThat(updatedPlagiarismCase.getVerdict()).as("should update plagiarism case verdict point deduction").isEqualTo(PlagiarismVerdict.POINT_DEDUCTION);
        assertThat(updatedPlagiarismCase.getVerdictPointDeduction()).as("should update plagiarism case verdict point deduction").isEqualTo(90);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPlagiarismCaseInfoReturnsEmptyWithoutPostForStudent() throws Exception {
        var plagiarismCaseInfo = request.get("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-case", HttpStatus.OK, String.class);
        assertThat(plagiarismCaseInfo).as("should not get plagiarism case for exercise for student if there is no notification post yet").isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPlagiarismCaseInfoWithoutVerdictForExerciseForStudent() throws Exception {
        Post post = new Post();
        post.setAuthor(userRepository.getUserByLoginElseThrow("instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase1);
        post = postRepository.save(post);
        plagiarismCase1.setPost(post);
        plagiarismCaseRepository.save(plagiarismCase1);

        var plagiarismCaseInfo = request.get("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-case", HttpStatus.OK,
                PlagiarismCaseInfoDTO.class);
        assertThat(plagiarismCaseInfo.id()).as("should get plagiarism case for exercise for student").isEqualTo(plagiarismCase1.getId());
        assertThat(plagiarismCaseInfo.verdict()).as("should get null verdict before it is set").isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPlagiarismCaseInfoWithVerdictForExerciseForStudent() throws Exception {
        Post post = new Post();
        post.setAuthor(userRepository.getUserByLoginElseThrow("instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase1);
        post = postRepository.save(post);
        plagiarismCase1.setPost(post);
        var verdict = NO_PLAGIARISM;
        plagiarismCase1.setVerdict(verdict);
        plagiarismCaseRepository.save(plagiarismCase1);

        var plagiarismCaseInfo = request.get("/api/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-case", HttpStatus.OK,
                PlagiarismCaseInfoDTO.class);
        assertThat(plagiarismCaseInfo.id()).as("should get plagiarism case for exercise for student").isEqualTo(plagiarismCase1.getId());
        assertThat(plagiarismCaseInfo.verdict()).as("should get the verdict after it is set").isEqualTo(verdict);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testGetPlagiarismCaseForStudent_forbidden() throws Exception {
        request.get("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-student", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetPlagiarismCaseForStudent() throws Exception {
        var plagiarismCase = request.get("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-student", HttpStatus.OK, PlagiarismCase.class);
        assertThat(plagiarismCase).as("should get plagiarism case for student").isEqualTo(plagiarismCase1);
        for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
            assertThat(submission.getPlagiarismComparison().getPlagiarismResult().getExercise()).as("should prepare plagiarism case response entity").isEqualTo(null);
            assertThat(submission.getPlagiarismComparison().getSubmissionA()).as("should prepare plagiarism case response entity").isEqualTo(null);
            assertThat(submission.getPlagiarismComparison().getSubmissionB()).as("should prepare plagiarism case response entity").isEqualTo(null);
        }
    }
}
