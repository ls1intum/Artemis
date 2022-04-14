package de.tum.in.www1.artemis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismVerdictDTO;

public class PlagiarismCaseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @BeforeEach
    public void initTestCase() {
        database.addUsers(3, 1, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "User")
    public void testGetPlagiarismCasesForCourseForInstructor_forbidden_student() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetPlagiarismCasesForCourseForInstructor_forbidden_tutor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);

    }

    @Test
    @WithMockUser(username = "editor1", roles = "Editor")
    public void testGetPlagiarismCasesForCourseForInstructor_forbidden_editor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "Instructor")
    public void testGetPlagiarismCasesForCourseForInstructor() {
    }

    @Test
    @WithMockUser(username = "student1", roles = "User")
    public void testGetPlagiarismCaseForInstructor_forbidden_student() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetPlagiarismCaseForInstructor_forbidden_tutor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "Editor")
    public void testGetPlagiarismCaseForInstructor_forbidden_editor() throws Exception {
        request.getList("/api/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "Instructor")
    public void testGetPlagiarismCaseForInstructor() {
    }

    @Test
    @WithMockUser(username = "student1", roles = "User")
    public void testSavePlagiarismCaseVerdict_forbidden_student() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testSavePlagiarismCaseVerdict_forbidden_tutor() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "Editor")
    public void testSavePlagiarismCaseVerdict_forbidden_editor() throws Exception {
        request.put("/api/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "Instructor")
    public void testSavePlagiarismCaseVerdict() {
    }
}
