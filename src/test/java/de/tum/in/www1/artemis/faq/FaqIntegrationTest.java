package de.tum.in.www1.artemis.faq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Faq;
import de.tum.in.www1.artemis.domain.FaqState;
import de.tum.in.www1.artemis.repository.FaqRepository;

class FaqIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "faqIntegrationTest";

    @Autowired
    private FaqRepository faqRepository;

    private Course course1;

    private Faq faq;

    @BeforeEach
    void initTestCase() {
        int numberOfTutors = 2;
        long courseId = 2;
        long faqId = 1;
        userUtilService.addUsers(TEST_PREFIX, 1, numberOfTutors, 0, 1);
        this.course1 = courseUtilService.createCourse(courseId);
        this.faq = FaqFactory.generateFaq(faqId, course1);
        faqRepository.save(this.faq);
        this.course1.addFaq(this.faq);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    private void testAllPreAuthorize() throws Exception {
        request.postWithResponseBody("/api/faqs", new Faq(), Faq.class, HttpStatus.FORBIDDEN);
        System.out.println("Test");
        request.putWithResponseBody("/api/faqs/" + faq.getId(), new Faq(), Faq.class, HttpStatus.FORBIDDEN);
        request.getList("/api/courses/" + course1.getId() + "/faqs", HttpStatus.FORBIDDEN, Faq.class);
        request.delete("/api/faqs/" + faq.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFaq_correctRequestBody_shouldCreateFaq() throws Exception {
        Course course = courseRepository.findByIdElseThrow(this.course1.getId());

        Faq faq = new Faq();
        faq.setQuestionTitle("Title");
        faq.setQuestionAnswer("Answer");
        faq.setCategories(FaqFactory.generateFaqCategories());
        faq.setFaqState(FaqState.ACCEPTED);
        faq.setCourse(course);

        Faq returnedFaq = request.postWithResponseBody("/api/faqs", faq, Faq.class, HttpStatus.CREATED);

        assertThat(returnedFaq).isNotNull();
        assertThat(returnedFaq.getId()).isNotNull();
        assertThat(returnedFaq.getQuestionTitle()).isEqualTo(faq.getQuestionTitle());
        assertThat(returnedFaq.getCourse().getId()).isEqualTo(faq.getCourse().getId());
        assertThat(returnedFaq.getQuestionAnswer()).isEqualTo(faq.getQuestionAnswer());
        assertThat(returnedFaq.getCategories()).isEqualTo(faq.getCategories());
        assertThat(returnedFaq.getFaqState()).isEqualTo(faq.getFaqState());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFaq_alreadyId_shouldReturnBadRequest() throws Exception {
        Faq faq = new Faq();
        faq.setId(1L);
        request.postWithResponseBody("/api/faqs", faq, Faq.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFaq_correctRequestBody_shouldUpdateFaq() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setQuestionAnswer("Updated");
        faq.setFaqState(FaqState.PROPOSED);
        Set<String> newCategories = new HashSet<String>();
        newCategories.add("Test");
        faq.setCategories(newCategories);
        Faq updatedFaq = request.putWithResponseBody("/api/faqs/" + faq.getId(), faq, Faq.class, HttpStatus.OK);

        assertThat(updatedFaq.getQuestionTitle()).isEqualTo("Updated");
        assertThat(updatedFaq.getQuestionTitle()).isEqualTo("Updated");
        assertThat(updatedFaq.getFaqState()).isEqualTo(FaqState.REJECTED);
        assertThat(updatedFaq.getCategories()).isEqualTo(newCategories);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqCategoriesByCourseId() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        Set<String> categories = faq.getCategories();
        Set<String> returnedCategories = request.get("/api/courses/" + faq.getCourse().getId() + "/faq-categories", HttpStatus.OK, Set.class);
        assertThat(categories).isEqualTo(returnedCategories);
    }

}
