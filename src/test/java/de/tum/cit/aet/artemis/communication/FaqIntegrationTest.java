package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqDTO;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FaqIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "faqintegrationtest";

    @Autowired
    private FaqRepository faqRepository;

    private Course course1;

    private Faq faq;

    @BeforeEach
    void initTestCase() throws Exception {
        int numberOfTutors = 2;
        userUtilService.addUsers(TEST_PREFIX, 1, numberOfTutors, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, numberOfTutors);
        this.course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.faq = FaqFactory.generateFaq(course1, FaqState.PROPOSED, "answer", "title");
        faqRepository.save(this.faq);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

    }

    private void testAllPreAuthorize() throws Exception {
        request.postWithResponseBody("/api/courses/" + faq.getCourse().getId() + "/faqs", new Faq(), Faq.class, HttpStatus.FORBIDDEN);
        request.putWithResponseBody("/api/courses/" + faq.getCourse().getId() + "/faqs/" + this.faq.getId(), this.faq, Faq.class, HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + faq.getCourse().getId() + "/faqs/" + this.faq.getId(), HttpStatus.FORBIDDEN);
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
        Faq newFaq = FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "title", "answer");
        Faq returnedFaq = request.postWithResponseBody("/api/courses/" + course1.getId() + "/faqs", newFaq, Faq.class, HttpStatus.CREATED);
        assertThat(returnedFaq).isNotNull();
        assertThat(returnedFaq.getId()).isNotNull();
        assertThat(returnedFaq.getQuestionTitle()).isEqualTo(newFaq.getQuestionTitle());
        assertThat(returnedFaq.getQuestionAnswer()).isEqualTo(newFaq.getQuestionAnswer());
        assertThat(returnedFaq.getCategories()).isEqualTo(newFaq.getCategories());
        assertThat(returnedFaq.getFaqState()).isEqualTo(newFaq.getFaqState());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFaq_alreadyId_shouldReturnBadRequest() throws Exception {
        Faq newFaq = FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "title", "answer");
        newFaq.setId(this.faq.getId());
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/faqs", newFaq, Faq.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFaq_courseId_noMatch_shouldReturnBadRequest() throws Exception {
        Faq newFaq = FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "title", "answer");
        request.postWithResponseBody("/api/courses/" + course1.getId() + 1 + "/faqs", newFaq, Faq.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFaq_correctRequestBody_shouldUpdateFaq() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setQuestionAnswer("Update");
        faq.setFaqState(FaqState.PROPOSED);
        Set<String> newCategories = new HashSet<>();
        newCategories.add("Test");
        faq.setCategories(newCategories);
        Faq updatedFaq = request.putWithResponseBody("/api/courses/" + faq.getCourse().getId() + "/faqs/" + faq.getId(), faq, Faq.class, HttpStatus.OK);
        assertThat(updatedFaq.getQuestionTitle()).isEqualTo("Updated");
        assertThat(updatedFaq.getQuestionAnswer()).isEqualTo("Update");
        assertThat(updatedFaq.getFaqState()).isEqualTo(FaqState.PROPOSED);
        assertThat(updatedFaq.getCategories()).isEqualTo(newCategories);
        assertThat(updatedFaq.getCreatedDate()).isNotNull();
        assertThat(updatedFaq.getLastModifiedDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFaq_IdsDoNotMatch_shouldNotUpdateFaq() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setFaqState(FaqState.PROPOSED);
        faq.setId(faq.getId() + 1);
        Faq updatedFaq = request.putWithResponseBody("/api/courses/" + course1.getId() + "/faqs/" + (faq.getId() - 1), faq, Faq.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqCategoriesByCourseId() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        Set<String> categories = faq.getCategories();
        Set<String> returnedCategories = request.get("/api/courses/" + faq.getCourse().getId() + "/faq-categories", HttpStatus.OK, Set.class);
        assertThat(categories).isEqualTo(returnedCategories);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqByFaqId() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        Faq returnedFaq = request.get("/api/courses/" + faq.getCourse().getId() + "/faqs/" + faq.getId(), HttpStatus.OK, Faq.class);
        assertThat(faq).isEqualTo(returnedFaq);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqByFaqId_shouldNotGet_IdMismatch() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        Faq returnedFaq = request.get("/api/courses/" + faq.getCourse().getId() + 1 + "/faqs/" + faq.getId(), HttpStatus.BAD_REQUEST, Faq.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFaq_shouldDeleteFAQ() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        request.delete("/api/courses/" + faq.getCourse().getId() + "/faqs/" + faq.getId(), HttpStatus.OK);
        Optional<Faq> faqOptional = faqRepository.findById(faq.getId());
        assertThat(faqOptional).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFaq_IdsDoNotMatch_shouldNotDeleteFAQ() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        request.delete("/api/courses/" + faq.getCourse().getId() + 1 + "/faqs/" + faq.getId(), HttpStatus.BAD_REQUEST);
        Optional<Faq> faqOptional = faqRepository.findById(faq.getId());
        assertThat(faqOptional).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqByCourseId() throws Exception {
        Set<Faq> faqs = faqRepository.findAllByCourseIdAndFaqState(this.course1.getId(), FaqState.PROPOSED);
        Set<FaqDTO> returnedFaqs = request.get("/api/courses/" + course1.getId() + "/faqs", HttpStatus.OK, Set.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqByCourseIdAndState() throws Exception {
        Set<Faq> faqs = faqRepository.findAllByCourseIdAndFaqState(this.course1.getId(), FaqState.PROPOSED);
        Set<FaqDTO> returnedFaqs = request.get("/api/courses/" + course1.getId() + "/faq-state/" + "PROPOSED", HttpStatus.OK, Set.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
    }

}
