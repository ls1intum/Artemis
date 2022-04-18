package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismVerdictDTO;

public class PlagiarismCaseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    private static Course course;

    private static TextExercise textExercise;

    private static PlagiarismCase plagiarismCase1;

    private static PlagiarismCase plagiarismCase2;

    private static PlagiarismCase plagiarismCase3;

    /*
     * private List<Post> createBasicPosts(PlagiarismCase plagiarismCaseContext) { List<Post> posts = new ArrayList<>(); Post postToAdd = new Post();
     * postToAdd.setTitle("Title Plagiarism Case Post"); postToAdd.setContent("Content Plagiarism Case Post"); postToAdd.setVisibleForStudents(true);
     * postToAdd.setDisplayPriority(DisplayPriority.NONE); postToAdd.setAuthor(getUserByLoginWithoutAuthorities("instructor1")); postToAdd.setCreationDate(ZonedDateTime.of(2015,
     * 11, dayCount, 23, 45, 59, 1234, ZoneId.of("UTC"))); postToAdd.setPlagiarismCase(plagiarismCaseContext); postRepository.save(postToAdd); posts.add(postToAdd); dayCount =
     * (dayCount % 25) + 1; return posts; }
     */

    @BeforeEach
    public void initTestCase() {
        database.addUsers(4, 1, 1, 1);
        course = database.addCourseWithOneFinishedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
        plagiarismCase1 = new PlagiarismCase();
        plagiarismCase1.setExercise(textExercise);
        User student1 = database.getUserByLogin("student1");
        plagiarismCase1.setStudent(student1);
        plagiarismCase1 = plagiarismCaseRepository.save(plagiarismCase1);
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
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPlagiarismCaseForInstructor_forbidden_student() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetPlagiarismCaseForInstructor_forbidden_tutor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR ")
    public void testGetPlagiarismCaseForInstructor_forbidden_editor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
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
    public void testSavePlagiarismCaseVerdict() throws Exception {
        var plagiarismVerdictDTO = new PlagiarismVerdictDTO();
        plagiarismVerdictDTO.setVerdict(PlagiarismVerdict.WARNING);
        plagiarismVerdictDTO.setVerdictMessage("This is a warning!");

        request.put("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/verdict", plagiarismVerdictDTO, HttpStatus.OK);
        var updatedPlagiarismCase = plagiarismCaseRepository.findByIdWithExerciseAndPlagiarismSubmissionsElseThrow(plagiarismCase1.getId());
        assertThat(updatedPlagiarismCase.getVerdict()).as("should update plagiarism case verdict").isEqualTo(PlagiarismVerdict.WARNING);
        assertThat(updatedPlagiarismCase.getVerdictMessage()).as("should update plagiarism case verdict message").isEqualTo("This is a warning!");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPlagiarismCasesForStudent() throws Exception {
        var plagiarismCases = request.getList("/api/courses/" + course.getId() + "/plagiarism-cases/for-student", HttpStatus.OK, PlagiarismCase.class);

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetPlagiarismCaseForStudent() throws Exception {
        var plagiarismCase = request.get("/api/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-student", HttpStatus.OK, PlagiarismCase.class);
        assertThat(plagiarismCase).as("should get plagiarism case for student").isEqualTo(plagiarismCase1);
    }
}
