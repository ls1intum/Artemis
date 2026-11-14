package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqCensusDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqCensusEntryDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqFingerprintInputDTO;
import de.tum.cit.aet.artemis.communication.test_repository.FaqTestRepository;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;

class FaqIngestionReconcileServiceTest {

    private static final long COURSE = 1L;

    private static final long DISABLED_COURSE = 2L;

    private FaqTestRepository faqRepository;

    private PyrisFaqApi pyrisFaqApi;

    private IrisSettingsApi irisSettingsApi;

    private FaqIngestionReconcileService service;

    private final List<FaqFingerprintInputDTO> accepted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        faqRepository = mock(FaqTestRepository.class);
        pyrisFaqApi = mock(PyrisFaqApi.class);
        irisSettingsApi = mock(IrisSettingsApi.class);
        when(faqRepository.findFingerprintInputsByFaqState(FaqState.ACCEPTED)).thenReturn(accepted);
        when(irisSettingsApi.filterCourseIdsWithIrisEnabled(anyCollection()))
                .thenAnswer(invocation -> ((Collection<Long>) invocation.getArgument(0)).stream().filter(id -> id != DISABLED_COURSE).toList());
        when(faqRepository.findById(anyLong())).thenAnswer(invocation -> Optional.of(faq(invocation.getArgument(0), FaqState.ACCEPTED)));
        service = new FaqIngestionReconcileService(faqRepository, Optional.of(pyrisFaqApi), Optional.of(irisSettingsApi), 20);
    }

    private static Faq faq(long id, FaqState state) {
        Faq faq = new Faq();
        faq.setId(id);
        faq.setFaqState(state);
        faq.setQuestionTitle("Q" + id);
        faq.setQuestionAnswer("A" + id);
        return faq;
    }

    private void accept(long courseId, long faqId) {
        accepted.add(new FaqFingerprintInputDTO(faqId, courseId, "Q" + faqId, "A" + faqId));
    }

    private static String fingerprint(long faqId) {
        return Faq.contentFingerprint("Q" + faqId, "A" + faqId);
    }

    private void census(FaqCensusEntryDTO... entries) {
        when(pyrisFaqApi.getFaqCensus()).thenReturn(new FaqCensusDTO(List.of(entries)));
    }

    private List<Long> pushedFaqIds() {
        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);
        verify(pyrisFaqApi, atLeast(0)).addFaq(captor.capture());
        return captor.getAllValues().stream().map(Faq::getId).toList();
    }

    @Test
    void shouldPushMissingChangedDuplicatedAndLegacyFaqs() {
        accept(COURSE, 10);
        accept(COURSE, 11);
        accept(COURSE, 12);
        accept(COURSE, 13);
        accept(COURSE, 14);
        census(new FaqCensusEntryDTO(COURSE, 11, 1, 0, "v1:stale"), new FaqCensusEntryDTO(COURSE, 12, 2, 0, fingerprint(12)), new FaqCensusEntryDTO(COURSE, 13, 0, 1, null),
                new FaqCensusEntryDTO(COURSE, 14, 1, 0, fingerprint(14)));

        assertThat(service.reconcile()).isEqualTo(4);

        assertThat(pushedFaqIds()).containsExactly(10L, 11L, 12L, 13L);
        verify(pyrisFaqApi, never()).deleteFaq(anyLong(), anyLong());
    }

    @Test
    void shouldDeleteOwnRowsOfFaqsThatAreGoneButKeepLegacyRows() {
        accept(COURSE, 10);
        census(new FaqCensusEntryDTO(COURSE, 10, 1, 0, fingerprint(10)), new FaqCensusEntryDTO(COURSE, 20, 1, 0, "v1:x"), new FaqCensusEntryDTO(COURSE, 21, 0, 1, null),
                new FaqCensusEntryDTO(99, 30, 1, 0, "v1:y"));

        assertThat(service.reconcile()).isEqualTo(2);

        verify(pyrisFaqApi).deleteFaq(20, COURSE);
        verify(pyrisFaqApi).deleteFaq(30, 99);
        verify(pyrisFaqApi, never()).deleteFaq(21, COURSE);
        verify(pyrisFaqApi, never()).addFaq(any());
    }

    @Test
    void shouldLeaveCoursesWithIrisDisabledAlone() {
        accept(DISABLED_COURSE, 10);
        census(new FaqCensusEntryDTO(DISABLED_COURSE, 20, 1, 0, "v1:x"));

        assertThat(service.reconcile()).isZero();

        verify(pyrisFaqApi, never()).addFaq(any());
        verify(pyrisFaqApi, never()).deleteFaq(anyLong(), anyLong());
    }

    @Test
    void shouldDoNothingWithoutACensus() {
        accept(COURSE, 10);
        when(pyrisFaqApi.getFaqCensus()).thenReturn(null);

        assertThat(service.reconcile()).isZero();

        verify(faqRepository, never()).findFingerprintInputsByFaqState(any());
        verify(pyrisFaqApi, never()).addFaq(any());
    }

    @Test
    void shouldSkipAFaqThatIsNotAcceptedAnymoreWhenItIsPushed() {
        accept(COURSE, 10);
        census();
        when(faqRepository.findById(10L)).thenReturn(Optional.of(faq(10, FaqState.REJECTED)));

        assertThat(service.reconcile()).isEqualTo(1);

        verify(pyrisFaqApi, never()).addFaq(any());
    }

    @Test
    void shouldSpendTheBudgetAndContinueAfterTheLastAttemptedActionInTheNextRun() {
        for (long faqId = 1; faqId <= 25; faqId++) {
            accept(COURSE, faqId);
        }
        census();

        assertThat(service.reconcile()).isEqualTo(20);
        assertThat(pushedFaqIds()).hasSize(20).last().isEqualTo(20L);

        clearInvocations(pyrisFaqApi);
        assertThat(service.reconcile()).isEqualTo(20);
        // The second run starts after FAQ 20 and wraps around
        assertThat(pushedFaqIds()).startsWith(21L, 22L, 23L, 24L, 25L, 1L);
    }

    @Test
    void shouldNotLetAFailingFaqBlockTheOthers() {
        accept(COURSE, 10);
        accept(COURSE, 11);
        census();
        Faq failing = faq(10, FaqState.ACCEPTED);
        when(faqRepository.findById(10L)).thenReturn(Optional.of(failing));
        doThrow(new RuntimeException("Pyris down")).when(pyrisFaqApi).addFaq(failing);

        assertThat(service.reconcile()).isEqualTo(2);

        verify(pyrisFaqApi, times(2)).addFaq(any());
        assertThat(pushedFaqIds()).containsExactly(10L, 11L);
    }

    @Test
    void shouldLeaveFaqsThatMatchAlone() {
        accept(COURSE, 10);
        census(new FaqCensusEntryDTO(COURSE, 10, 1, 0, fingerprint(10)));

        assertThat(service.reconcile()).isZero();
        verify(pyrisFaqApi, never()).addFaq(any());
    }
}
