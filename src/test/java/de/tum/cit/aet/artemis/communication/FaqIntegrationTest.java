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
import de.tum.cit.aet.artemis.communication.dto.CreateFaqDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqDTO;
import de.tum.cit.aet.artemis.communication.dto.UpdateFaqDTO;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FaqIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "faqintegrationtest";

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private IrisRequestMockProvider irisRequestMockProvider;

    private Course course1;

    private Course course2;

    private Faq faq;

    @BeforeEach
    void initTestCase() throws Exception {
        int numberOfTutors = 2;
        userUtilService.addUsers(TEST_PREFIX, 2, numberOfTutors, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, numberOfTutors);
        this.course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.course2 = courses.getLast();
        this.faq = FaqFactory.generateFaq(course1, FaqState.PROPOSED, "answer", "title");
        faqRepository.save(this.faq);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
        irisRequestMockProvider.enableMockingOfRequests();

    }

    private void testAllPreAuthorize() throws Exception {
        CreateFaqDTO create = new CreateFaqDTO(course1.getId(), "t", "a", Set.of(), FaqState.PROPOSED);
        request.postWithResponseBody("/api/communication/courses/" + faq.getCourse().getId() + "/faqs", create, FaqDTO.class, HttpStatus.FORBIDDEN);

        UpdateFaqDTO update = new UpdateFaqDTO(faq.getId(), "t", "a", Set.of("TestForbidden"), FaqState.PROPOSED);
        request.putWithResponseBody("/api/communication/courses/" + faq.getCourse().getId() + "/faqs/" + update.id(), update, FaqDTO.class, HttpStatus.FORBIDDEN);
        request.delete("/api/communication/courses/" + faq.getCourse().getId() + "/faqs/" + this.faq.getId(), HttpStatus.FORBIDDEN);
        request.put("/api/communication/courses/" + course1.getId() + "/faqs/enable", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFaq_correctRequestBody_shouldCreateFaq() throws Exception {
        Set<String> categories = Set.of("catA", "catB");
        CreateFaqDTO dto = new CreateFaqDTO(course1.getId(), "title", "answer", categories, FaqState.ACCEPTED);
        FaqDTO returnedFaq = request.postWithResponseBody("/api/communication/courses/" + course1.getId() + "/faqs", dto, FaqDTO.class, HttpStatus.CREATED);
        assertThat(returnedFaq).isNotNull();
        assertThat(returnedFaq.id()).isNotNull();
        assertThat(returnedFaq.questionTitle()).isEqualTo(dto.questionTitle());
        assertThat(returnedFaq.questionAnswer()).isEqualTo(dto.questionAnswer());
        assertThat(returnedFaq.categories()).isEqualTo(dto.categories());
        assertThat(returnedFaq.faqState()).isEqualTo(dto.faqState());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFaq_courseId_noMatch_shouldReturnBadRequest() throws Exception {
        CreateFaqDTO dto = new CreateFaqDTO(course1.getId(), "title", "answer", Set.of(), FaqState.ACCEPTED);
        request.postWithResponseBody("/api/communication/courses/" + course2.getId() + "/faqs", dto, FaqDTO.class, HttpStatus.BAD_REQUEST);
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
        Long courseId = faq.getCourse().getId();

        UpdateFaqDTO updated = new UpdateFaqDTO(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
        FaqDTO returnedFaq = request.putWithResponseBody("/api/communication/courses/" + courseId + "/faqs/" + updated.id(), updated, FaqDTO.class, HttpStatus.OK);

        assertThat(returnedFaq.questionTitle()).isEqualTo("Updated");
        assertThat(returnedFaq.questionAnswer()).isEqualTo("Update");
        assertThat(returnedFaq.faqState()).isEqualTo(FaqState.PROPOSED);
        assertThat(returnedFaq.categories()).isEqualTo(newCategories);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFaq_IdsDoNotMatch_shouldNotUpdateFaq() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setFaqState(FaqState.PROPOSED);
        UpdateFaqDTO dto = new UpdateFaqDTO(faq.getId() + 1, faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
        request.putWithResponseBody("/api/communication/courses/" + course1.getId() + "/faqs/" + (dto.id() - 1), dto, FaqDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateFaq_Tutor_cannotAcceptFaq() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setFaqState(FaqState.ACCEPTED);
        Long courseId = faq.getCourse().getId();

        UpdateFaqDTO dto = new UpdateFaqDTO(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
        request.putWithResponseBody("/api/communication/courses/" + courseId + "/faqs/" + dto.id(), dto, FaqDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFaq_Instructor_canAcceptFaq() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setFaqState(FaqState.ACCEPTED);
        Long courseId = faq.getCourse().getId();

        UpdateFaqDTO dto = new UpdateFaqDTO(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
        FaqDTO returnedFaq = request.putWithResponseBody("/api/communication/courses/" + courseId + "/faqs/" + dto.id(), dto, FaqDTO.class, HttpStatus.OK);
        assertThat(returnedFaq.faqState()).isEqualTo(FaqState.ACCEPTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqCategoriesByCourseId() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        Set<String> categories = faq.getCategories();
        Set<String> returnedCategories = request.get("/api/communication/courses/" + faq.getCourse().getId() + "/faq-categories", HttpStatus.OK, Set.class);
        assertThat(categories).isEqualTo(returnedCategories);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqByFaqId() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        Faq returnedFaq = request.get("/api/communication/courses/" + faq.getCourse().getId() + "/faqs/" + faq.getId(), HttpStatus.OK, Faq.class);
        assertThat(faq).isEqualTo(returnedFaq);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFaqByFaqId_shouldNotGet_IdMismatch() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        request.get("/api/communication/courses/" + course2.getId() + "/faqs/" + faq.getId(), HttpStatus.BAD_REQUEST, Faq.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFaq_shouldDeleteFAQ() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        irisRequestMockProvider.mockFaqDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        request.delete("/api/communication/courses/" + faq.getCourse().getId() + "/faqs/" + faq.getId(), HttpStatus.OK);
        Optional<Faq> faqOptional = faqRepository.findById(faq.getId());
        assertThat(faqOptional).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFaq_IdsDoNotMatch_shouldNotDeleteFAQ() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        request.delete("/api/communication/courses/" + course2.getId() + "/faqs/" + faq.getId(), HttpStatus.BAD_REQUEST);
        Optional<Faq> faqOptional = faqRepository.findById(faq.getId());
        assertThat(faqOptional).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getFaq_InstructorsShouldGetAllFaqByCourseId() throws Exception {
        Set<Faq> faqs = faqRepository.findAllByCourseId(this.course1.getId());
        Set<FaqDTO> returnedFaqs = request.get("/api/communication/courses/" + course1.getId() + "/faqs", HttpStatus.OK, Set.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getFaq_StudentsShouldOnlyGetAcceptedFaqByCourseId() throws Exception {
        Set<Faq> faqs = faqRepository.findAllByCourseIdAndFaqState(this.course1.getId(), FaqState.ACCEPTED);
        Set<FaqDTO> returnedFaqs = request.get("/api/communication/courses/" + course1.getId() + "/faqs", HttpStatus.OK, Set.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
        assertThat(returnedFaqs).noneMatch(faq -> faq.faqState() == FaqState.PROPOSED);
        assertThat(returnedFaqs).noneMatch(faq -> faq.faqState() == FaqState.REJECTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getFaq_ShouldGetFaqByCourseId() throws Exception {
        Set<Faq> faqs = faqRepository.findAllByCourseId(this.course1.getId());
        Set<FaqDTO> returnedFaqs = request.get("/api/communication/courses/" + course1.getId() + "/faqs", HttpStatus.OK, Set.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getFaq_shouldGetFaqByCourseIdAndState() throws Exception {
        Set<Faq> faqs = faqRepository.findAllByCourseIdAndFaqState(this.course1.getId(), FaqState.PROPOSED);
        Set<FaqDTO> returnedFaqs = request.get("/api/communication/courses/" + course1.getId() + "/faq-state/" + "PROPOSED", HttpStatus.OK, Set.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getFaqs_StudentShouldOnlyGetAcceptedFaqByCourse() throws Exception {
        Set<Faq> faqs = faqRepository.findAllByCourseIdAndFaqState(course1.getId(), FaqState.ACCEPTED);
        Set<FaqDTO> returnedFaqs = request.get("/api/communication/courses/" + course1.getId() + "/faqs", HttpStatus.OK, Set.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
        assertThat(returnedFaqs.size()).isEqualTo(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEnableFaq() throws Exception {
        disableFaq(course1);
        enableFaqRESTCall(course1);
        Course updatedCourse = courseRepository.findByIdElseThrow(course1.getId());
        assertThat(updatedCourse.isFaqEnabled()).isTrue();
    }

    private void disableFaq(Course course) {
        course = courseRepository.findByIdElseThrow(course.getId());
        course.setFaqEnabled(false);
        courseRepository.save(course);
    }

    private void enableFaqRESTCall(Course course) throws Exception {
        request.put("/api/communication/courses/" + course.getId() + "/faqs/enable", null, HttpStatus.OK);
    }

}
