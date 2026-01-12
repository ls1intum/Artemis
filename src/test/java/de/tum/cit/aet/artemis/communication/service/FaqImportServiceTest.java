package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FaqImportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "faqimportservice";

    @Autowired
    private FaqImportService faqImportService;

    @Autowired
    private FaqRepository faqRepository;

    private Course sourceCourse;

    private Course targetCourse;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        sourceCourse = courseUtilService.createCourse();
        sourceCourse.setFaqEnabled(true);
        sourceCourse = courseRepository.save(sourceCourse);

        targetCourse = courseUtilService.createCourse();
        targetCourse.setFaqEnabled(true);
        targetCourse = courseRepository.save(targetCourse);
    }

    @Test
    void importFaqs_emptySourceCourse_shouldReturnEmptyList() {
        List<Faq> imported = faqImportService.importFaqs(sourceCourse.getId(), targetCourse);

        assertThat(imported).isEmpty();
    }

    @Test
    void importFaqs_singleFaq_shouldCopyAllFields() {
        Faq sourceFaq = new Faq();
        sourceFaq.setQuestionTitle("Test Question");
        sourceFaq.setQuestionAnswer("Test Answer");
        sourceFaq.setFaqState(FaqState.ACCEPTED);
        sourceFaq.setCategories(Set.of("Category1", "Category2"));
        sourceFaq.setCourse(sourceCourse);
        faqRepository.save(sourceFaq);

        List<Faq> imported = faqImportService.importFaqs(sourceCourse.getId(), targetCourse);

        assertThat(imported).hasSize(1);
        Faq importedFaq = imported.getFirst();
        assertThat(importedFaq.getId()).isNotNull();
        assertThat(importedFaq.getId()).isNotEqualTo(sourceFaq.getId());
        assertThat(importedFaq.getQuestionTitle()).isEqualTo("Test Question");
        assertThat(importedFaq.getQuestionAnswer()).isEqualTo("Test Answer");
        assertThat(importedFaq.getFaqState()).isEqualTo(FaqState.ACCEPTED);
        assertThat(importedFaq.getCategories()).containsExactlyInAnyOrder("Category1", "Category2");
        assertThat(importedFaq.getCourse().getId()).isEqualTo(targetCourse.getId());
    }

    @Test
    void importFaqs_multipleFaqs_shouldImportAll() {
        createFaq(sourceCourse, "FAQ 1", "Answer 1", FaqState.PROPOSED);
        createFaq(sourceCourse, "FAQ 2", "Answer 2", FaqState.ACCEPTED);
        createFaq(sourceCourse, "FAQ 3", "Answer 3", FaqState.REJECTED);

        List<Faq> imported = faqImportService.importFaqs(sourceCourse.getId(), targetCourse);

        assertThat(imported).hasSize(3);

        // Verify all FAQs were imported with correct states
        assertThat(imported).extracting(Faq::getQuestionTitle).containsExactlyInAnyOrder("FAQ 1", "FAQ 2", "FAQ 3");
        assertThat(imported).allMatch(faq -> faq.getCourse().getId().equals(targetCourse.getId()));
    }

    @Test
    void importFaqs_shouldNotModifySourceFaqs() {
        Faq sourceFaq = createFaq(sourceCourse, "Original Question", "Original Answer", FaqState.ACCEPTED);
        Long originalId = sourceFaq.getId();

        faqImportService.importFaqs(sourceCourse.getId(), targetCourse);

        // Verify source FAQ is unchanged
        Faq reloadedSourceFaq = faqRepository.findById(originalId).orElseThrow();
        assertThat(reloadedSourceFaq.getQuestionTitle()).isEqualTo("Original Question");
        assertThat(reloadedSourceFaq.getCourse().getId()).isEqualTo(sourceCourse.getId());
    }

    @Test
    void importFaqs_withEmptyCategories_shouldHandleCorrectly() {
        Faq sourceFaq = new Faq();
        sourceFaq.setQuestionTitle("No Categories FAQ");
        sourceFaq.setQuestionAnswer("Answer");
        sourceFaq.setFaqState(FaqState.ACCEPTED);
        sourceFaq.setCategories(new HashSet<>());
        sourceFaq.setCourse(sourceCourse);
        faqRepository.save(sourceFaq);

        List<Faq> imported = faqImportService.importFaqs(sourceCourse.getId(), targetCourse);

        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst().getCategories()).isEmpty();
    }

    @Test
    void importFaqs_shouldPreserveFaqState() {
        createFaq(sourceCourse, "Proposed FAQ", "Answer", FaqState.PROPOSED);
        createFaq(sourceCourse, "Accepted FAQ", "Answer", FaqState.ACCEPTED);
        createFaq(sourceCourse, "Rejected FAQ", "Answer", FaqState.REJECTED);

        List<Faq> imported = faqImportService.importFaqs(sourceCourse.getId(), targetCourse);

        assertThat(imported).hasSize(3);
        assertThat(imported).extracting(Faq::getFaqState).containsExactlyInAnyOrder(FaqState.PROPOSED, FaqState.ACCEPTED, FaqState.REJECTED);
    }

    private Faq createFaq(Course course, String title, String answer, FaqState state) {
        Faq faq = new Faq();
        faq.setQuestionTitle(title);
        faq.setQuestionAnswer(answer);
        faq.setFaqState(state);
        faq.setCourse(course);
        return faqRepository.save(faq);
    }
}
