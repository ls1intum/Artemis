package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.CreateFaqDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqImportCandidateDTO;
import de.tum.cit.aet.artemis.communication.dto.ImportFaqsDTO;
import de.tum.cit.aet.artemis.communication.dto.UpdateFaqDTO;
import de.tum.cit.aet.artemis.communication.test_repository.FaqTestRepository;
import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FaqIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "faqintegrationtest";

    private static final String OTHER_PREFIX = TEST_PREFIX + "other";

    @Autowired
    private FaqTestRepository faqRepository;

    @Autowired
    private IrisRequestMockProvider irisRequestMockProvider;

    @Autowired
    private Validator validator;

    private Course course1;

    private Course course2;

    private Faq faq;

    @BeforeEach
    void initTestCase() throws Exception {
        int numberOfTutors = 2;
        userUtilService.addUsers(TEST_PREFIX, 2, numberOfTutors, 0, 1);
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, numberOfTutors);
        this.course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.course2 = courses.getLast();
        this.faq = FaqFactory.generateFaq(course1, FaqState.PROPOSED, "answer", "title");
        faqRepository.save(this.faq);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(OTHER_PREFIX + "student42");
        userUtilService.createAndSaveUser(OTHER_PREFIX + "instructor42");
        irisRequestMockProvider.enableMockingOfRequests();

    }

    @ParameterizedTest
    @ValueSource(strings = { "student1", "tutor1", "instructor1" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void studentFaqAudienceIsFixedForEveryCourseRole(String login) throws Exception {
        userUtilService.changeUser(TEST_PREFIX + login);
        var accepted = faqRepository.save(FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "accepted", "accepted answer"));
        var rejected = faqRepository.save(FaqFactory.generateFaq(course1, FaqState.REJECTED, "rejected answer", "rejected"));
        accepted.setCategories(Set.of("accepted-category"));
        rejected.setCategories(Set.of("rejected-category"));
        faq.setCategories(Set.of("proposed-category"));
        faqRepository.saveAll(List.of(accepted, rejected, faq));
        String base = "/api/communication/courses/" + course1.getId();
        var result = request.getList(base + "/faqs", HttpStatus.OK, FaqDTO.class);
        assertThat(result).extracting(FaqDTO::id).containsExactly(accepted.getId());
        assertThat(request.get(base + "/faqs/" + accepted.getId(), HttpStatus.OK, FaqDTO.class).questionAnswer()).isEqualTo("accepted answer");
        assertThat(request.getList(base + "/faq-state/ACCEPTED", HttpStatus.OK, FaqDTO.class)).extracting(FaqDTO::id).containsExactly(accepted.getId());
        assertThat(request.get(base + "/faq-categories/ACCEPTED", HttpStatus.OK, Set.class)).containsExactlyInAnyOrderElementsOf(accepted.getCategories());
        // the categories of proposed and rejected faqs would reveal what they are about
        assertThat(request.get(base + "/faq-categories", HttpStatus.OK, Set.class)).containsExactlyInAnyOrderElementsOf(accepted.getCategories());
        request.get(base + "/faqs/" + Long.MAX_VALUE, HttpStatus.NOT_FOUND, FaqDTO.class);
        for (var hidden : List.of(faq, rejected)) {
            request.get(base + "/faqs/" + hidden.getId(), HttpStatus.FORBIDDEN, FaqDTO.class);
            request.getList(base + "/faq-state/" + hidden.getFaqState(), HttpStatus.FORBIDDEN, FaqDTO.class);
            request.get(base + "/faq-categories/" + hidden.getFaqState(), HttpStatus.FORBIDDEN, Set.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void managementFaqAudienceReturnsAllStatesAndPreservesBindings() throws Exception {
        var accepted = faqRepository.save(FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "accepted", "accepted"));
        var rejected = FaqFactory.generateFaq(course1, FaqState.REJECTED, "rejected", "rejected");
        rejected.setCategories(Set.of("rejected-category"));
        rejected = faqRepository.save(rejected);
        String base = "/api/communication/courses/" + course1.getId();
        assertThat(request.getList(base + "/faqs/for-management", HttpStatus.OK, FaqDTO.class)).extracting(FaqDTO::id).containsExactly(rejected.getId(), accepted.getId(),
                faq.getId());
        for (var expected : List.of(faq, accepted, rejected)) {
            assertThat(request.getList(base + "/faqs/for-management?faqState=" + expected.getFaqState(), HttpStatus.OK, FaqDTO.class)).extracting(FaqDTO::id)
                    .containsExactly(expected.getId());
            assertThat(request.get(base + "/faqs/" + expected.getId() + "/for-management", HttpStatus.OK, FaqDTO.class).faqState()).isEqualTo(expected.getFaqState());
            assertThat(request.get(base + "/faq-categories/for-management?faqState=" + expected.getFaqState(), HttpStatus.OK, Set.class))
                    .containsExactlyInAnyOrderElementsOf(expected.getCategories());
        }
        assertThat(request.get(base + "/faq-categories/for-management", HttpStatus.OK, Set.class)).contains("rejected-category");
        request.get("/api/communication/courses/" + course2.getId() + "/faqs/" + faq.getId() + "/for-management", HttpStatus.BAD_REQUEST, FaqDTO.class);
        request.get(base + "/faqs/" + Long.MAX_VALUE + "/for-management", HttpStatus.NOT_FOUND, FaqDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotReadManagementFaqAudience() throws Exception {
        String base = "/api/communication/courses/" + course1.getId();
        request.getList(base + "/faqs/for-management", HttpStatus.FORBIDDEN, FaqDTO.class);
        request.get(base + "/faqs/" + faq.getId() + "/for-management", HttpStatus.FORBIDDEN, FaqDTO.class);
        request.get(base + "/faq-categories/for-management", HttpStatus.FORBIDDEN, Set.class);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor42", roles = "USER")
    void foreignCourseUserCannotReadAnyFaqAudience() throws Exception {
        String base = "/api/communication/courses/" + course1.getId();
        for (String suffix : List.of("/faqs", "/faqs/for-management", "/faqs/" + faq.getId(), "/faqs/" + faq.getId() + "/for-management", "/faq-state/ACCEPTED",
                "/faq-categories/ACCEPTED", "/faq-categories/for-management", "/faq-categories")) {
            request.get(base + suffix, HttpStatus.FORBIDDEN, Object.class);
        }
    }

    private void testAllPreAuthorize() throws Exception {
        CreateFaqDTO create = new CreateFaqDTO(course1.getId(), "t", "a", Set.of(), FaqState.PROPOSED);
        request.postWithResponseBody("/api/communication/courses/" + faq.getCourse().getId() + "/faqs", create, FaqDTO.class, HttpStatus.FORBIDDEN);

        UpdateFaqDTO update = new UpdateFaqDTO(faq.getId(), "t", "a", Set.of("TestForbidden"), FaqState.PROPOSED);
        request.putWithResponseBody("/api/communication/courses/" + faq.getCourse().getId() + "/faqs/" + update.id(), update, FaqDTO.class, HttpStatus.FORBIDDEN);
        request.delete("/api/communication/courses/" + faq.getCourse().getId() + "/faqs/" + this.faq.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void createFaq_correctRequestBody_shouldCreateFaq() throws Exception {
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void createFaq_courseId_noMatch_shouldReturnBadRequest() throws Exception {
        CreateFaqDTO dto = new CreateFaqDTO(course1.getId(), "title", "answer", Set.of(), FaqState.ACCEPTED);
        request.postWithResponseBody("/api/communication/courses/" + course2.getId() + "/faqs", dto, FaqDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void updateFaq_IdsDoNotMatch_shouldNotUpdateFaq() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setFaqState(FaqState.PROPOSED);
        UpdateFaqDTO dto = new UpdateFaqDTO(faq.getId() + 1, faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
        request.putWithResponseBody("/api/communication/courses/" + course1.getId() + "/faqs/" + (dto.id() - 1), dto, FaqDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void updateFaq_Tutor_cannotAcceptFaq() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setFaqState(FaqState.ACCEPTED);
        Long courseId = faq.getCourse().getId();

        UpdateFaqDTO dto = new UpdateFaqDTO(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
        request.putWithResponseBody("/api/communication/courses/" + courseId + "/faqs/" + dto.id(), dto, FaqDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void updateFaq_Instructor_canAcceptFaq() throws Exception {
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow();
        faq.setQuestionTitle("Updated");
        faq.setFaqState(FaqState.ACCEPTED);
        Long courseId = faq.getCourse().getId();

        UpdateFaqDTO dto = new UpdateFaqDTO(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCategories(), faq.getFaqState());
        FaqDTO returnedFaq = request.putWithResponseBody("/api/communication/courses/" + courseId + "/faqs/" + dto.id(), dto, FaqDTO.class, HttpStatus.OK);
        assertThat(returnedFaq.faqState()).isEqualTo(FaqState.ACCEPTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetFaqCategoriesByCourseId() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        Set<String> categories = faq.getCategories();
        // the fixture faq is only proposed, so its categories are part of the staff view, but not of the student one
        Set<String> returnedCategories = request.get("/api/communication/courses/" + faq.getCourse().getId() + "/faq-categories/for-management", HttpStatus.OK, Set.class);
        assertThat(categories).isEqualTo(returnedCategories);
        assertThat(request.get("/api/communication/courses/" + faq.getCourse().getId() + "/faq-categories", HttpStatus.OK, Set.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetFaqByFaqId() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        Faq returnedFaq = request.get("/api/communication/courses/" + faq.getCourse().getId() + "/faqs/" + faq.getId() + "/for-management", HttpStatus.OK, Faq.class);
        assertThat(faq).isEqualTo(returnedFaq);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetFaqByFaqId_shouldNotGet_IdMismatch() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        request.get("/api/communication/courses/" + course2.getId() + "/faqs/" + faq.getId(), HttpStatus.BAD_REQUEST, Faq.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deleteFaq_shouldDeleteFAQ() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        irisRequestMockProvider.mockFaqDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        request.delete("/api/communication/courses/" + faq.getCourse().getId() + "/faqs/" + faq.getId(), HttpStatus.OK);
        Optional<Faq> faqOptional = faqRepository.findById(faq.getId());
        assertThat(faqOptional).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deleteFaq_IdsDoNotMatch_shouldNotDeleteFAQ() throws Exception {
        Faq faq = faqRepository.findById(this.faq.getId()).orElseThrow(EntityNotFoundException::new);
        request.delete("/api/communication/courses/" + course2.getId() + "/faqs/" + faq.getId(), HttpStatus.BAD_REQUEST);
        Optional<Faq> faqOptional = faqRepository.findById(faq.getId());
        assertThat(faqOptional).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getFaq_InstructorsShouldGetAllFaqByCourseId() throws Exception {
        List<Faq> faqs = faqRepository.findAllByCourseIdOrderByCreatedDateDesc(this.course1.getId());
        List<FaqDTO> returnedFaqs = request.getList("/api/communication/courses/" + course1.getId() + "/faqs/for-management", HttpStatus.OK, FaqDTO.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getFaq_StudentsShouldOnlyGetAcceptedFaqByCourseId() throws Exception {
        List<Faq> faqs = faqRepository.findAllByCourseIdAndFaqStateOrderByCreatedDateDesc(this.course1.getId(), FaqState.ACCEPTED);
        List<FaqDTO> returnedFaqs = request.getList("/api/communication/courses/" + course1.getId() + "/faqs", HttpStatus.OK, FaqDTO.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
        assertThat(returnedFaqs).noneMatch(faq -> faq.faqState() == FaqState.PROPOSED);
        assertThat(returnedFaqs).noneMatch(faq -> faq.faqState() == FaqState.REJECTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getFaq_ShouldGetFaqByCourseId() throws Exception {
        List<Faq> faqs = faqRepository.findAllByCourseIdOrderByCreatedDateDesc(this.course1.getId());
        List<FaqDTO> returnedFaqs = request.getList("/api/communication/courses/" + course1.getId() + "/faqs/for-management", HttpStatus.OK, FaqDTO.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getFaq_shouldGetFaqByCourseIdAndState() throws Exception {
        List<Faq> faqs = faqRepository.findAllByCourseIdAndFaqStateOrderByCreatedDateDesc(this.course1.getId(), FaqState.PROPOSED);
        List<FaqDTO> returnedFaqs = request.getList("/api/communication/courses/" + course1.getId() + "/faqs/for-management?faqState=PROPOSED", HttpStatus.OK, FaqDTO.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getFaqs_StudentShouldOnlyGetAcceptedFaqByCourse() throws Exception {
        List<Faq> faqs = faqRepository.findAllByCourseIdAndFaqStateOrderByCreatedDateDesc(course1.getId(), FaqState.ACCEPTED);
        List<FaqDTO> returnedFaqs = request.getList("/api/communication/courses/" + course1.getId() + "/faqs", HttpStatus.OK, FaqDTO.class);
        assertThat(returnedFaqs).hasSize(faqs.size());
        assertThat(returnedFaqs.size()).isEqualTo(faqs.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void getFaqs_shouldReturnFaqsSortedByCreationDateDescending() throws Exception {
        Faq faq1 = FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "SortTest1", "answer1");
        faqRepository.save(faq1);
        Thread.sleep(10);

        Faq faq2 = FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "SortTest2", "answer2");
        faqRepository.save(faq2);
        Thread.sleep(10);

        Faq faq3 = FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "SortTest3", "answer3");
        faqRepository.save(faq3);

        List<FaqDTO> returnedFaqs = request.getList("/api/communication/courses/" + course1.getId() + "/faqs/for-management", HttpStatus.OK, FaqDTO.class);

        List<FaqDTO> createdFaqs = returnedFaqs.stream().filter(faq -> faq.questionTitle().startsWith("SortTest")).toList();

        // Verify they are sorted by creation date descending (newest first)
        assertThat(createdFaqs).hasSize(3);
        assertThat(createdFaqs.get(0).questionTitle()).isEqualTo("SortTest3"); // Newest
        assertThat(createdFaqs.get(1).questionTitle()).isEqualTo("SortTest2");
        assertThat(createdFaqs.get(2).questionTitle()).isEqualTo("SortTest1"); // Oldest
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchFaqsForImport_matchesContentAndExcludesTargetAndInaccessibleCourses() throws Exception {
        Faq matching = faqRepository.save(FaqFactory.generateFaq(course1, FaqState.PROPOSED, "Office hours", "Registration details"));
        faqRepository.save(FaqFactory.generateFaq(course2, FaqState.ACCEPTED, "Target FAQ", "Registration details"));
        Course inaccessible = courseUtilService.createCourse();
        faqRepository.save(FaqFactory.generateFaq(inaccessible, FaqState.ACCEPTED, "Private FAQ", "Registration details"));

        var params = new LinkedMultiValueMap<String, String>();
        params.add("searchTerm", "registration");
        var result = request.getSearchResult("/api/communication/courses/" + course2.getId() + "/faqs/for-import", HttpStatus.OK, FaqDTO.class, params);
        assertThat(result.getResultsOnPage()).extracting(FaqDTO::id).containsExactly(matching.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void importSelectedFaqs_copiesOnlySelectedFaqsAndPreservesSource() throws Exception {
        faq.setQuestionTitle("Original question");
        faq.setQuestionAnswer("Original answer");
        faq.setCategories(Set.of("Category"));
        faqRepository.save(faq);
        Faq second = faqRepository.save(FaqFactory.generateFaq(course1, FaqState.REJECTED, "Second question", "Second answer"));
        faqRepository.save(FaqFactory.generateFaq(course1, FaqState.PROPOSED, "Unselected question", "Unselected answer"));
        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of(faq.getId(), second.getId())),
                HttpStatus.CREATED);

        var imported = faqRepository.findAllByCourseIdOrderByCreatedDateDesc(course2.getId());
        assertThat(imported).extracting(Faq::getQuestionTitle).containsExactlyInAnyOrder("Original question", "Second question");
        assertThat(imported).extracting(Faq::getId).doesNotContain(faq.getId(), second.getId());
        var first = imported.stream().filter(item -> item.getQuestionTitle().equals("Original question")).findFirst().orElseThrow();
        assertThat(first.getQuestionAnswer()).isEqualTo("Original answer");
        assertThat(first.getFaqState()).isEqualTo(FaqState.PROPOSED);
        assertThat(first.getCategories()).containsExactly("Category");
        assertThat(faqRepository.findByIdElseThrow(faq.getId()).getCourse().getId()).isEqualTo(course1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void importSelectedFaqs_rejectsInaccessibleSelectionBeforeSavingAnything() throws Exception {
        Course inaccessible = courseUtilService.createCourse();
        Faq hidden = faqRepository.save(FaqFactory.generateFaq(inaccessible, FaqState.ACCEPTED, "Secret", "Secret"));
        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of(faq.getId(), hidden.getId())),
                HttpStatus.FORBIDDEN);
        assertThat(faqRepository.countByCourseId(course2.getId())).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void importSelectedFaqs_rejectsEmptyAndSameCourseSelections() throws Exception {
        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of()), HttpStatus.BAD_REQUEST);
        request.postWithoutResponseBody("/api/communication/courses/" + course1.getId() + "/faqs/import", Map.of("faqIds", List.of(faq.getId())), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void importSelectedFaqs_limitsSelectionBeforeLoadingOrSaving() throws Exception {
        var maximumSelection = LongStream.rangeClosed(1, 500).boxed().collect(Collectors.toSet());
        assertThat(validator.validate(new ImportFaqsDTO(maximumSelection))).isEmpty();
        var oversizedSelection = LongStream.rangeClosed(1, 501).map(id -> Long.MAX_VALUE - id).boxed().collect(Collectors.toSet());
        assertThat(validator.validate(new ImportFaqsDTO(oversizedSelection))).singleElement()
                .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("faqIds"));

        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", oversizedSelection), HttpStatus.BAD_REQUEST);

        assertThat(faqRepository.countByCourseId(course2.getId())).isZero();
        assertThat(faqRepository.findById(faq.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void importSelectedFaqs_requiresInstructorInTargetCourse() throws Exception {
        request.get("/api/communication/courses/" + course2.getId() + "/faqs/for-import", HttpStatus.FORBIDDEN, Object.class);
        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of(faq.getId())), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchAndImportFaqs_studentAccessOnlyAllowsAcceptedFaqs() throws Exception {
        Course studentCourse = courseUtilService.createCourse();
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.enrollUserInCourse(user, studentCourse, CourseRole.STUDENT);
        Faq accepted = faqRepository.save(FaqFactory.generateFaq(studentCourse, FaqState.ACCEPTED, "Student source question", "Shared answer"));
        Faq proposed = faqRepository.save(FaqFactory.generateFaq(studentCourse, FaqState.PROPOSED, "Student source proposal", "Hidden answer"));
        faqRepository.save(FaqFactory.generateFaq(studentCourse, FaqState.REJECTED, "Student source rejected", "Hidden answer"));

        var params = new LinkedMultiValueMap<String, String>();
        params.add("searchTerm", "Student source");
        var result = request.getSearchResult("/api/communication/courses/" + course2.getId() + "/faqs/for-import", HttpStatus.OK, FaqImportCandidateDTO.class, params);
        assertThat(result.getResultsOnPage()).extracting(FaqImportCandidateDTO::id).containsExactly(accepted.getId());
        var candidate = result.getResultsOnPage().getFirst();
        assertThat(candidate.courseId()).isEqualTo(studentCourse.getId());
        assertThat(candidate.courseTitle()).isEqualTo(studentCourse.getTitle());
        assertThat(candidate.questionAnswer()).isEqualTo("Shared answer");

        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of(accepted.getId(), proposed.getId())),
                HttpStatus.FORBIDDEN);
        assertThat(faqRepository.countByCourseId(course2.getId())).isZero();
        // A FAQ selected while accepted must become inaccessible if its status changes before importing.
        accepted.setFaqState(FaqState.REJECTED);
        faqRepository.save(accepted);
        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of(accepted.getId())), HttpStatus.FORBIDDEN);
        assertThat(faqRepository.countByCourseId(course2.getId())).isZero();
        accepted.setFaqState(FaqState.ACCEPTED);
        faqRepository.save(accepted);
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        var imported = request.postListWithResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import",
                Map.of("faqIds", List.of(accepted.getId(), accepted.getId())), FaqDTO.class, HttpStatus.CREATED, null, null);
        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().id()).isNotEqualTo(accepted.getId());
        assertThat(imported.getFirst().faqState()).isEqualTo(FaqState.ACCEPTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchFaqsForImport_matchesCourseNameAndPaginatesStably() throws Exception {
        course1.setTitle("FAQ source pagination");
        courseRepository.save(course1);
        for (int i = 0; i < 11; i++) {
            faqRepository.save(FaqFactory.generateFaq(course1, FaqState.PROPOSED, "Same question", "Answer"));
        }
        var params = new LinkedMultiValueMap<String, String>();
        params.add("searchTerm", "faq SOURCE pagination");
        var first = request.getSearchResult("/api/communication/courses/" + course2.getId() + "/faqs/for-import", HttpStatus.OK, FaqImportCandidateDTO.class, params);
        assertThat(first.getResultsOnPage()).hasSize(10);
        assertThat(first.getNumberOfPages()).isEqualTo(2);
        params.add("page", "1");
        var second = request.getSearchResult("/api/communication/courses/" + course2.getId() + "/faqs/for-import", HttpStatus.OK, FaqImportCandidateDTO.class, params);
        assertThat(second.getResultsOnPage()).hasSize(2);
        assertThat(second.getResultsOnPage()).extracting(FaqImportCandidateDTO::id)
                .doesNotContainAnyElementsOf(first.getResultsOnPage().stream().map(FaqImportCandidateDTO::id).toList());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void importSelectedFaqs_rejectsDeletedAndInvalidIdsBeforeSaving() throws Exception {
        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of(faq.getId(), Long.MAX_VALUE)),
                HttpStatus.BAD_REQUEST);
        request.postWithoutResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of(-1)), HttpStatus.BAD_REQUEST);
        assertThat(faqRepository.countByCourseId(course2.getId())).isZero();
        request.get("/api/communication/courses/" + course2.getId() + "/faqs/for-import?page=-1", HttpStatus.BAD_REQUEST, Object.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void importSelectedFaqs_irisFailureDoesNotTurnPersistedCopiesIntoFailedImport() throws Exception {
        faq.setFaqState(FaqState.ACCEPTED);
        faqRepository.save(faq);
        irisRequestMockProvider.mockFaqIngestionWebhookError(HttpStatus.SERVICE_UNAVAILABLE);
        var imported = request.postListWithResponseBody("/api/communication/courses/" + course2.getId() + "/faqs/import", Map.of("faqIds", List.of(faq.getId())), FaqDTO.class,
                HttpStatus.CREATED, null, null);
        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().id()).isNotEqualTo(faq.getId());
        assertThat(faqRepository.countByCourseId(course2.getId())).isEqualTo(1);
    }

}
