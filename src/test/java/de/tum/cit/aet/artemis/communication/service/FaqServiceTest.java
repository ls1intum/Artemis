package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.test_repository.FaqTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

class FaqServiceTest {

    private final FaqTestRepository repository = mock(FaqTestRepository.class);

    private final PyrisConnectorService connector = mock(PyrisConnectorService.class);

    private final Queue<Runnable> tasks = new ArrayDeque<>();

    private final List<PyrisFaqWebhookDTO> ingested = new ArrayList<>();

    private final List<PyrisFaqWebhookDTO> deleted = new ArrayList<>();

    private FaqService service;

    private FaqService target;

    private Faq faq;

    @BeforeEach
    void setUp() {
        var course = new Course();
        course.setId(589L);
        faq = new Faq();
        faq.setId(42L);
        faq.setCourse(course);
        faq.setFaqState(FaqState.ACCEPTED);
        faq.setQuestionTitle("Question");
        faq.setQuestionAnswer("Original answer");
        when(repository.findById(42L)).thenReturn(Optional.of(faq));
        var settings = mock(IrisSettingsService.class);
        when(settings.isEnabledForCourse(any())).thenReturn(true);
        var webhook = new PyrisWebhookService(connector, mock(PyrisJobService.class), settings, Optional.empty(), Optional.empty(), mock(VideoSourceResolverService.class));
        var api = new PyrisFaqApi(webhook, connector);
        target = new FaqService(repository, Optional.of(api));
        var factory = new DefaultListableBeanFactory();
        factory.registerSingleton("faqTaskExecutor", (Executor) tasks::add);
        var processor = new AsyncAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        service = (FaqService) processor.postProcessAfterInitialization(target, "faqService");
        doAnswer(call -> {
            ingested.add(call.getArgument(0));
            return null;
        }).when(connector).executeFaqAdditionWebhook(any(), any());
        doAnswer(call -> {
            PyrisWebhookFaqDeletionExecutionDTO dto = call.getArgument(0);
            deleted.add(dto.pyrisFaqWebhookDTO());
            return null;
        }).when(connector).executeFaqDeletionWebhook(any());
    }

    @Test
    void creationDoesNotContactPyrisOnRequestThread() {
        service.autoIngestFaqIntoPyris(42L);

        verifyNoInteractions(repository, connector);
        assertThat(tasks).hasSize(1);
        tasks.remove().run();
        assertThat(ingested).singleElement().satisfies(dto -> {
            assertThat(dto.faqId()).isEqualTo(42L);
            assertThat(dto.questionAnswer()).isEqualTo("Original answer");
        });
    }

    @Test
    void queuedEditReadsLatestPersistedContent() {
        service.autoIngestFaqIntoPyris(42L);
        faq.setQuestionAnswer("Latest answer");

        assertThat(ingested).isEmpty();
        tasks.remove().run();
        assertThat(ingested).singleElement().satisfies(dto -> assertThat(dto.questionAnswer()).isEqualTo("Latest answer"));
    }

    @Test
    void deletionDoesNotContactPyrisOnRequestThreadOrReloadDeletedEntity() {
        service.deleteFaqInPyris(42L, 589L);

        verifyNoInteractions(repository, connector);
        assertThat(tasks).hasSize(1);
        tasks.remove().run();
        assertThat(deleted).singleElement().satisfies(dto -> {
            assertThat(dto.faqId()).isEqualTo(42L);
            assertThat(dto.courseId()).isEqualTo(589L);
        });
        verifyNoInteractions(repository);
    }

    @Test
    void queuedIngestionSkipsAnFaqDeletedWhileWaiting() {
        service.autoIngestFaqIntoPyris(42L);
        when(repository.findById(42L)).thenReturn(Optional.empty());

        tasks.remove().run();

        assertThat(ingested).isEmpty();
        assertThat(deleted).isEmpty();
    }

    @Test
    void queuedIngestionRemovesAnFaqThatIsNoLongerAccepted() {
        service.autoIngestFaqIntoPyris(42L);
        faq.setFaqState(FaqState.REJECTED);

        tasks.remove().run();

        assertThat(ingested).isEmpty();
        assertThat(deleted).singleElement().satisfies(dto -> assertThat(dto.faqId()).isEqualTo(42L));
    }

    @Test
    void pyrisErrorDoesNotEscapeFaqSynchronization() {
        doThrow(new IrisInternalPyrisErrorException("Unavailable")).when(connector).executeFaqAdditionWebhook(any(), any());

        assertThatCode(() -> target.autoIngestFaqIntoPyris(42L)).doesNotThrowAnyException();
    }

    @Test
    void connectionFailureDoesNotEscapeFaqDeletion() {
        doThrow(new PyrisConnectorException("Connection refused")).when(connector).executeFaqDeletionWebhook(any());

        assertThatCode(() -> target.deleteFaqInPyris(42L, 589L)).doesNotThrowAnyException();
    }

    @Test
    void irisModuleCanBeAbsent() {
        var withoutIris = new FaqService(repository, Optional.empty());

        withoutIris.autoIngestFaqIntoPyris(42L);
        withoutIris.deleteFaqInPyris(42L, 589L);

        verifyNoInteractions(repository, connector);
    }
}
