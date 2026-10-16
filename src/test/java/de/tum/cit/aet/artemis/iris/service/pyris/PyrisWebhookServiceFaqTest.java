package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSupportLevel;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJobType;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

class PyrisWebhookServiceFaqTest {

    @Test
    void faqIngestionUsesDefaultVariantForAdvancedChatCourse() {
        var connector = mock(PyrisConnectorService.class);
        var jobs = mock(PyrisJobService.class);
        var tokenService = PyrisCallbackTokenTestUtil.tokenService();
        var settings = mock(IrisSettingsService.class);
        var service = new PyrisWebhookService(connector, jobs, tokenService, settings, Optional.empty(), Optional.empty(), mock(VideoSourceResolverService.class));
        var course = new Course();
        course.setId(589L);
        var faq = new Faq();
        faq.setId(42L);
        faq.setCourse(course);
        faq.setQuestionTitle("Question");
        faq.setQuestionAnswer("Answer");
        when(settings.isEnabledForCourse(course)).thenReturn(true);
        when(settings.getSettingsForCourse(course)).thenReturn(IrisCourseSettings.of(true, null, IrisPipelineVariant.ADVANCED, IrisSupportLevel.MODERATE, null));
        when(jobs.addFaqIngestionWebhookJob(589L, 42L)).thenReturn("job-token");
        when(jobs.getJob("job-token")).thenReturn(new FaqIngestionWebhookJob("job-token", 589L, 42L));

        service.addFaq(faq);

        var payload = ArgumentCaptor.forClass(PyrisWebhookFaqIngestionExecutionDTO.class);
        verify(connector).executeFaqAdditionWebhook(any(), payload.capture());
        assertThat(payload.getValue().settings().variant()).isEqualTo("default");
        assertThat(payload.getValue().settings().runId()).isEqualTo("job-token");
        assertThat(tokenService.verify(payload.getValue().settings().authenticationToken())).as("the token is a callback token for this FAQ run")
                .contains(new PyrisCallbackTokenService.VerifiedCallback("job-token", PyrisJobType.FAQ_INGESTION, null));
        assertThat(payload.getValue().pyrisFaqWebhookDTO().contentFingerprint()).isEqualTo(faq.computeContentFingerprint());
    }
}
