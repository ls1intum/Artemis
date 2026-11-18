package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.communication.FaqFactory.generateFaq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqCensusDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqCensusEntryDTO;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.service.FaqIngestionReconcileServiceTestAccess;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;

/**
 * FAQ reconciliation against the real database and Iris course settings, with Pyris replaced by an in-memory index that the pushes and
 * deletes update. It starts empty, as after the vector store was replaced, and runs passes until one has nothing left to do.
 */
class PyrisFaqReconcileIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisfaqreconcile";

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private IrisSettingsApi irisSettingsApi;

    /** The fake index: (courseId, faqId) to the stored fingerprint. */
    private final Map<List<Long>, String> index = new ConcurrentHashMap<>();

    private Course course;

    private Course disabledCourse;

    @BeforeEach
    void initTestCase() {
        course = courseUtilService.createCourse();
        disabledCourse = courseUtilService.createCourse();
        enableIrisFor(course);
        disableIrisFor(disabledCourse);
    }

    private PyrisFaqApi fakePyris() {
        PyrisFaqApi pyris = mock(PyrisFaqApi.class);
        when(pyris.getFaqCensus()).thenAnswer(invocation -> new FaqCensusDTO(
                index.entrySet().stream().map(entry -> new FaqCensusEntryDTO(entry.getKey().getFirst(), entry.getKey().getLast(), 1, 0, entry.getValue())).toList()));
        doAnswer(invocation -> {
            Faq faq = invocation.getArgument(0);
            index.put(List.of(faq.getCourse().getId(), faq.getId()), faq.computeContentFingerprint());
            return null;
        }).when(pyris).addFaq(any(Faq.class));
        doAnswer(invocation -> {
            index.remove(List.of((Long) invocation.getArgument(1), (Long) invocation.getArgument(0)));
            return null;
        }).when(pyris).deleteFaq(anyLong(), anyLong());
        return pyris;
    }

    @Test
    void shouldRefillAnEmptyIndexOverSeveralRunsUntilNothingIsLeft() {
        for (int i = 0; i < 25; i++) {
            faqRepository.save(generateFaq(course, FaqState.ACCEPTED, "Question " + i, i % 5 == 0 ? "" : "Answer " + i));
        }
        Faq proposed = faqRepository.save(generateFaq(course, FaqState.PROPOSED, "Proposed", "Not for Iris"));
        Faq disabled = faqRepository.save(generateFaq(disabledCourse, FaqState.ACCEPTED, "Disabled", "Iris off"));
        // Left over from an FAQ that was deleted while Pyris was unreachable
        index.put(List.of(course.getId(), 987654321L), "v1:orphan");

        var reconcile = FaqIngestionReconcileServiceTestAccess.create(faqRepository, Optional.of(fakePyris()), Optional.of(irisSettingsApi), 20);
        int runs = 0;
        while (reconcile.reconcile() > 0) {
            runs++;
            assertThat(runs).as("the reconcile must converge").isLessThan(500);
        }

        List<Faq> acceptedOfCourse = faqRepository.findAllByCourseIdAndFaqStateOrderByCreatedDateDesc(course.getId(), FaqState.ACCEPTED);
        assertThat(acceptedOfCourse).hasSize(25).allSatisfy(faq -> assertThat(index).containsEntry(List.of(course.getId(), faq.getId()), faq.computeContentFingerprint()));
        assertThat(index).doesNotContainKey(List.of(course.getId(), proposed.getId())).doesNotContainKey(List.of(course.getId(), 987654321L))
                .doesNotContainKey(List.of(disabledCourse.getId(), disabled.getId()));
        assertThat(runs).as("25 FAQs and one orphan with a budget of 20 per run need more than one run").isGreaterThan(1);

        // An edited FAQ is pushed again by the next run
        Faq edited = acceptedOfCourse.getFirst();
        edited.setQuestionAnswer("Changed answer");
        faqRepository.save(edited);
        assertThat(reconcile.reconcile()).isEqualTo(1);
        assertThat(index).containsEntry(List.of(course.getId(), edited.getId()), edited.computeContentFingerprint());
    }
}
