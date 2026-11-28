package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.util.MetisTestProperties;

class MetisAiConsentServiceTest {

    private final UserAiPreferenceService userAiPreferenceService = mock(UserAiPreferenceService.class);

    @ParameterizedTest(name = "{0} under {1} hosting allows Metis: {2}")
    @CsvSource({ "NO_AI, LOCAL, false", "NO_AI, CLOUD, false", ", LOCAL, false", ", CLOUD, false", "LOCAL_AI, LOCAL, true", "LOCAL_AI, CLOUD, false", "CLOUD_AI, LOCAL, true",
            "CLOUD_AI, CLOUD, true" })
    void shouldAllowTheEndpointOnlyForASufficientDecision(AiSelectionDecision decision, MetisProperties.Hosting hosting, boolean expected) {
        MetisAiConsentService consentService = consentService(hosting);
        when(userAiPreferenceService.findDecision(42L)).thenReturn(decision);

        assertThat(consentService.allows(decision)).isEqualTo(expected);
        assertThat(consentService.hasConsent(42L)).isEqualTo(expected);
        assertThat(consentService.decisionOf(42L)).isEqualTo(decision);
    }

    @Test
    void shouldLookUpTheConsentOfManyUsersInOneQueryUnderLocalHosting() {
        when(userAiPreferenceService.findDecisions(anyCollection()))
                .thenReturn(Map.of(1L, AiSelectionDecision.CLOUD_AI, 2L, AiSelectionDecision.LOCAL_AI, 3L, AiSelectionDecision.NO_AI));
        List<Long> userIds = List.of(1L, 2L, 3L, 4L);

        Set<Long> consenting = consentService(MetisProperties.Hosting.LOCAL).consentingUsers(userIds);

        assertThat(consenting).containsExactlyInAnyOrder(1L, 2L);
        verify(userAiPreferenceService, times(1)).findDecisions(userIds);
        verifyNoMoreInteractions(userAiPreferenceService);
    }

    @Test
    void shouldLookUpTheConsentOfManyUsersInOneQueryUnderCloudHosting() {
        when(userAiPreferenceService.findDecisions(anyCollection()))
                .thenReturn(Map.of(1L, AiSelectionDecision.CLOUD_AI, 2L, AiSelectionDecision.LOCAL_AI, 3L, AiSelectionDecision.NO_AI));
        List<Long> userIds = List.of(1L, 2L, 3L, 4L);

        Set<Long> consenting = consentService(MetisProperties.Hosting.CLOUD).consentingUsers(userIds);

        assertThat(consenting).containsExactly(1L);
        verify(userAiPreferenceService, times(1)).findDecisions(userIds);
        verifyNoMoreInteractions(userAiPreferenceService);
    }

    @Test
    void shouldFindNoConsentingUserAmongNone() {
        when(userAiPreferenceService.findDecisions(anyCollection())).thenReturn(Map.of());

        assertThat(consentService(MetisProperties.Hosting.LOCAL).consentingUsers(List.of())).isEmpty();
    }

    @Test
    void shouldAskForCloudConsentOnlyFromAStudentWithLocalConsentUnderCloudHosting() {
        assertThat(consentService(MetisProperties.Hosting.CLOUD).skipReasonFor(AiSelectionDecision.LOCAL_AI)).isEqualTo("CLOUD_AI_REQUIRED");
    }

    @ParameterizedTest
    @EnumSource(value = AiSelectionDecision.class, names = { "NO_AI", "CLOUD_AI" })
    void shouldPresentTheGenericConsentReasonUnderCloudHosting(AiSelectionDecision decision) {
        assertThat(consentService(MetisProperties.Hosting.CLOUD).skipReasonFor(decision)).isEqualTo("NO_AI_CONSENT");
    }

    @ParameterizedTest
    @EnumSource(AiSelectionDecision.class)
    void shouldPresentTheGenericConsentReasonUnderLocalHosting(AiSelectionDecision decision) {
        assertThat(consentService(MetisProperties.Hosting.LOCAL).skipReasonFor(decision)).isEqualTo("NO_AI_CONSENT");
    }

    @Test
    void shouldPresentTheGenericConsentReasonWithoutADecision() {
        assertThat(consentService(MetisProperties.Hosting.LOCAL).skipReasonFor(null)).isEqualTo("NO_AI_CONSENT");
        assertThat(consentService(MetisProperties.Hosting.CLOUD).skipReasonFor(null)).isEqualTo("NO_AI_CONSENT");
    }

    private MetisAiConsentService consentService(MetisProperties.Hosting hosting) {
        return new MetisAiConsentService(userAiPreferenceService, MetisTestProperties.with(Map.of("llm.hosting", hosting.name())));
    }
}
