package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.domain.TrackedFeature;
import de.tum.cit.aet.artemis.core.repository.TrackedFeatureRepository;

class FeatureUsageRegistryRecoveryTest {

    private final TrackedFeatureRepository repository = mock(TrackedFeatureRepository.class);

    private final ApplicationContext context = mock(ApplicationContext.class);

    private final FeatureUsageRegistry registry = new FeatureUsageRegistry(repository, new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of())),
            context);

    @Test
    void missingOrUnavailableHandlerMappingDoesNotPreventStartup() {
        @SuppressWarnings("unchecked")
        ObjectProvider<RequestMappingHandlerMapping> provider = mock(ObjectProvider.class);
        when(context.getBeanProvider(RequestMappingHandlerMapping.class)).thenReturn(provider);
        assertThatCode(registry::registerEndpoints).doesNotThrowAnyException();
        verify(repository, never()).findAll();
        when(context.containsBean("requestMappingHandlerMapping")).thenReturn(true);
        when(context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)).thenThrow(new IllegalStateException("MVC unavailable"));
        assertThatCode(registry::registerEndpoints).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "label", "interaction", "resource", "unchanged" })
    void existingEndpointClassificationIsUpdatedOnlyWhenItsMetadataChanged(String difference) throws Exception {
        var handler = new HandlerMethod(new FixtureResource(), FixtureResource.class.getMethod("read"));
        RequestMappingInfo mapping = RequestMappingInfo.paths("/api/core/usage").methods(RequestMethod.GET).build();
        RequestMappingHandlerMapping handlers = mock(RequestMappingHandlerMapping.class);
        when(handlers.getHandlerMethods()).thenReturn(Map.of(mapping, handler));
        String label = difference.equals("label") ? "OLD_LABEL" : UserFeature.IRIS_CHAT.name();
        FeatureInteraction interaction = difference.equals("interaction") ? FeatureInteraction.ACTION : FeatureInteraction.VIEW;
        String resource = difference.equals("resource") ? "OldResource" : "FixtureResource";
        var existing = new TrackedFeature(FeatureKind.REST, "core", "GET api/core/usage", label, interaction, resource, Instant.EPOCH);
        existing.setId(10L);
        var duplicate = new TrackedFeature(FeatureKind.REST, "core", "GET api/core/usage", label, interaction, resource, Instant.EPOCH);
        duplicate.setId(11L);
        var git = new TrackedFeature(FeatureKind.GIT, "localvc", "push", "GIT", FeatureInteraction.ACTION, null, Instant.EPOCH);
        when(repository.findAll()).thenReturn(List.of(existing, duplicate, git));

        registry.registerEndpoints(handlers);

        assertThat(registry.restFeatureId(handler.getMethod())).isEqualTo(10L);
        verify(repository).markStillRegistered(eq(List.of(10L)), any());
        if (difference.equals("unchanged")) {
            verify(repository, never()).updateClassification(anyLong(), any(), any(), any());
        }
        else {
            verify(repository).updateClassification(10L, UserFeature.IRIS_CHAT.name(), FeatureInteraction.VIEW, "FixtureResource");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void concurrentInventoryInsertUsesWinningRowAndLeavesUnknownMethodsUntracked(boolean winnerFound) throws Exception {
        var handler = new HandlerMethod(new FixtureResource(), FixtureResource.class.getMethod("read"));
        RequestMappingHandlerMapping handlers = mock(RequestMappingHandlerMapping.class);
        when(handlers.getHandlerMethods()).thenReturn(Map.of(RequestMappingInfo.paths("/api/core/usage").methods(RequestMethod.GET).build(), handler));
        when(repository.findAll()).thenReturn(List.of());
        when(repository.saveAll(any())).thenThrow(new DataIntegrityViolationException("another node registered"));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("winner exists"));
        var existing = new TrackedFeature(FeatureKind.REST, "core", "GET api/core/usage", UserFeature.IRIS_CHAT.name(), FeatureInteraction.VIEW, "FixtureResource", Instant.EPOCH);
        existing.setId(22L);
        when(repository.findByFeatureKindAndIdentifier(FeatureKind.REST, "GET api/core/usage")).thenReturn(winnerFound ? Optional.of(existing) : Optional.empty());
        registry.registerEndpoints(handlers);
        assertThat(registry.restFeatureId(handler.getMethod())).isEqualTo(winnerFound ? 22L : null);
    }

    @Test
    void firstSightingRetriesMissingWinnerAndTruncatesStoredKeysWithoutPoisoningCache() {
        String identifier = "operation/" + "x".repeat(300);
        when(repository.findByFeatureKindAndIdentifier(any(), any())).thenReturn(Optional.empty());
        var stored = new TrackedFeature(FeatureKind.GIT, "localvc", identifier.substring(0, 255), UserFeature.IRIS_CHAT.name(), FeatureInteraction.ACTION, null, Instant.EPOCH);
        stored.setId(31L);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("concurrent registration rolled back")).thenReturn(stored);
        assertThat(registry.featureId(FeatureKind.GIT, "module".repeat(10), identifier, UserFeature.IRIS_CHAT, FeatureInteraction.ACTION)).isNull();
        assertThat(registry.featureId(FeatureKind.GIT, "module".repeat(10), identifier, UserFeature.IRIS_CHAT, FeatureInteraction.ACTION)).isEqualTo(31L);
        assertThat(registry.featureId(FeatureKind.GIT, "module".repeat(10), identifier, UserFeature.IRIS_CHAT, FeatureInteraction.ACTION)).isEqualTo(31L);
        var saved = org.mockito.ArgumentCaptor.forClass(TrackedFeature.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getIdentifier()).hasSize(255);
        assertThat(saved.getValue().getModule()).hasSize(32);
    }

    @ParameterizedTest
    @ValueSource(strings = { "label", "interaction", "unchanged" })
    void lazyFeaturesReusePersistedIdAndRefreshChangedClassification(String difference) {
        String label = difference.equals("label") ? "old" : UserFeature.IRIS_CHAT.name();
        var interaction = difference.equals("interaction") ? FeatureInteraction.VIEW : FeatureInteraction.ACTION;
        var row = new TrackedFeature(FeatureKind.GIT, "localvc", "push", label, interaction, "GitResource", Instant.EPOCH);
        row.setId(41L);
        when(repository.findByFeatureKindAndIdentifier(FeatureKind.GIT, "push")).thenReturn(Optional.of(row));
        assertThat(registry.featureId(FeatureKind.GIT, "localvc", "push", UserFeature.IRIS_CHAT, FeatureInteraction.ACTION)).isEqualTo(41L);
        if (difference.equals("unchanged")) {
            verify(repository, never()).updateClassification(anyLong(), any(), any(), any());
        }
        else {
            verify(repository).updateClassification(41L, UserFeature.IRIS_CHAT.name(), FeatureInteraction.ACTION, "GitResource");
        }
    }

    @FeatureUsage(UserFeature.IRIS_CHAT)
    @RequestMapping("/canonical/")
    static class FixtureResource {

        public void read() {
        }
    }
}
