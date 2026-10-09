package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
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
import de.tum.cit.aet.artemis.core.security.annotations.Internal;

/**
 * Tests how an endpoint is turned into an inventory identifier.
 * <p>
 * The case that matters most is the canonical path: some controllers map a canonical prefix plus one or more deprecated
 * legacy aliases in a single {@code @RequestMapping}. Picking the wrong one would split a feature across two identifiers,
 * and neither row would then show its real usage.
 */
class FeatureUsageRegistryTest {

    /**
     * Git and background features cannot be enumerated at startup, so their inventory row is created the first time they are
     * seen. Two threads seeing the same feature together used to insert it twice, and the loser's insert was rejected by the
     * unique key: recovered from, but it left a database error in the log for normal operation.
     */
    @Test
    void shouldRegisterAFirstSightingOnlyOnceWhenTwoThreadsSeeItTogether() throws Exception {
        var repository = mock(TrackedFeatureRepository.class);
        when(repository.findByFeatureKindAndIdentifier(any(), any())).thenReturn(Optional.empty());
        var stored = new TrackedFeature(FeatureKind.GIT, "localvc", "push/assignment", UserFeature.PROGRAMMING_LOCAL_IDE.name(), FeatureInteraction.ACTION, null, Instant.now());
        stored.setId(7L);
        when(repository.save(any())).thenReturn(stored);
        var registry = new FeatureUsageRegistry(repository, enabledProperties(), mock(ApplicationContext.class));

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int thread = 0; thread < 2; thread++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return registry.featureId(FeatureKind.GIT, "localvc", "push/assignment", UserFeature.PROGRAMMING_LOCAL_IDE, FeatureInteraction.ACTION);
                }));
            }
            start.countDown();
            for (Future<Long> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(7L);
            }
        }
        finally {
            executor.shutdownNow();
        }

        verify(repository).save(any(TrackedFeature.class));
    }

    /**
     * A running Artemis has two beans of type {@link RequestMappingHandlerMapping}: MVC's own and Actuator's
     * {@code controllerEndpointHandlerMapping}. Resolving by type therefore fails as ambiguous, and because registration
     * deliberately swallows its failures, the only symptom was an admin page that stayed empty forever. The mapping has to
     * be addressed by its name.
     */
    @Test
    void shouldResolveTheMvcMappingByNameWhenASecondMappingBeanExists() {
        var repository = mock(TrackedFeatureRepository.class);
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.containsBean("requestMappingHandlerMapping")).thenReturn(true);
        when(applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)).thenReturn(new RequestMappingHandlerMapping());
        when(applicationContext.getBeanProvider(RequestMappingHandlerMapping.class))
                .thenThrow(new NoUniqueBeanDefinitionException(RequestMappingHandlerMapping.class, List.of("requestMappingHandlerMapping", "controllerEndpointHandlerMapping")));
        var registry = new FeatureUsageRegistry(repository, enabledProperties(), applicationContext);

        registry.registerEndpoints();

        verify(applicationContext).getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        verify(applicationContext, never()).getBeanProvider(RequestMappingHandlerMapping.class);
        // the scan ran rather than aborting on the ambiguity, so it consulted the stored inventory
        verify(repository).findAll();
    }

    @Test
    void shouldNotTouchTheInventoryWhenTrackingIsDisabled() {
        var repository = mock(TrackedFeatureRepository.class);
        var applicationContext = mock(ApplicationContext.class);
        var properties = new FeatureUsageProperties(false, 400, new FeatureUsageProperties.Digest(false, List.of()));

        new FeatureUsageRegistry(repository, properties, applicationContext).registerEndpoints();

        verify(repository, never()).findAll();
        verify(applicationContext, never()).getBean(any(String.class), any(Class.class));
    }

    private static FeatureUsageProperties enabledProperties() {
        return new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of()));
    }

    @Test
    void shouldUseTheCanonicalPrefixOfAControllerThatAlsoMapsALegacyAlias() {
        var descriptor = describe(LegacyAliasResource.class, "list", RequestMethod.GET, "/api/course/courses", "/api/core/course/courses");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.identifier()).isEqualTo("GET api/course/courses");
    }

    @Test
    void shouldFallBackToADeterministicPatternWhenNoneSitsUnderTheClassPrefix() {
        // A controller without a class level @RequestMapping declares full paths per method; there is no canonical
        // prefix to prefer, so the choice has to at least be stable across restarts.
        var descriptor = describe(NoClassPrefixResource.class, "list", RequestMethod.POST, "/api/zzz/last", "/api/aaa/first");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.identifier()).isEqualTo("POST api/aaa/first");
    }

    @Test
    void shouldDeriveTheModuleFromTheControllerPackage() {
        var descriptor = describe(LegacyAliasResource.class, "list", RequestMethod.GET, "/api/course/courses");

        assertThat(descriptor).isNotNull();
        // the test controllers live in ..core.service.featureusage, so the module is the segment after the base package
        assertThat(descriptor.module()).isEqualTo("core");
    }

    @Test
    void shouldReadTheFeatureFromTheMethod() {
        var descriptor = describe(LabelledResource.class, "labelledOnMethod", RequestMethod.GET, "/api/course/courses");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.label()).isEqualTo(UserFeature.HYPERION_CONSISTENCY_CHECK.name());
    }

    @Test
    void shouldFallBackToTheFeatureOfTheController() {
        var descriptor = describe(LabelledResource.class, "unlabelled", RequestMethod.GET, "/api/course/courses");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.label()).isEqualTo(UserFeature.HYPERION_PROBLEM_STATEMENT.name());
    }

    @Test
    void shouldRecordTheControllerAsTheResource() {
        var descriptor = describe(LabelledResource.class, "unlabelled", RequestMethod.GET, "/api/course/courses");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.resource()).isEqualTo("LabelledResource");
    }

    @Test
    void shouldCountAReadingVerbAsAView() {
        assertThat(describe(LabelledResource.class, "unlabelled", RequestMethod.GET, "/api/course/courses").interaction()).isEqualTo(FeatureInteraction.VIEW);
        assertThat(describe(LabelledResource.class, "unlabelled", RequestMethod.HEAD, "/api/course/courses").interaction()).isEqualTo(FeatureInteraction.VIEW);
    }

    @Test
    void shouldCountEveryOtherVerbAsAnAction() {
        for (RequestMethod verb : List.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE)) {
            assertThat(describe(LabelledResource.class, "unlabelled", verb, "/api/course/courses").interaction()).as(verb.name()).isEqualTo(FeatureInteraction.ACTION);
        }
    }

    @Test
    void shouldCountAMappingWithoutAVerbAsAnAction() {
        // a mapping without a verb accepts every verb, including the ones that change something
        var mappingInfo = RequestMappingInfo.paths("/api/course/courses").build();

        var descriptor = FeatureUsageRegistry.describe(mappingInfo, handlerMethod(LabelledResource.class, "unlabelled"));

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.interaction()).isEqualTo(FeatureInteraction.ACTION);
    }

    @Test
    void shouldCountAnInternalEndpointAsASystemCall() {
        assertThat(describe(InternalResource.class, "callback", RequestMethod.POST, "/api/iris/internal/callback").interaction()).isEqualTo(FeatureInteraction.SYSTEM);
    }

    @Test
    void shouldLetAnExplicitInteractionWinOverVerbAndInternal() {
        assertThat(describe(LabelledResource.class, "polled", RequestMethod.GET, "/api/course/courses").interaction()).isEqualTo(FeatureInteraction.AUTOMATIC);
        assertThat(describe(InternalResource.class, "overridden", RequestMethod.GET, "/api/iris/internal/status").interaction()).isEqualTo(FeatureInteraction.VIEW);
    }

    /**
     * A row written by an earlier version, before git and background features carried a feature and an interaction, must
     * regroup under the classification the caller reports now, like an endpoint does on every startup. Otherwise its
     * history would stay invisible on a page grouped by feature.
     */
    @Test
    void shouldReclassifyAnExistingLazyRowOnItsFirstSighting() {
        var repository = mock(TrackedFeatureRepository.class);
        var stored = new TrackedFeature(FeatureKind.GIT, "localvc", "fetch/tests", null, FeatureInteraction.ACTION, null, Instant.now());
        stored.setId(9L);
        when(repository.findByFeatureKindAndIdentifier(FeatureKind.GIT, "fetch/tests")).thenReturn(Optional.of(stored));
        var registry = new FeatureUsageRegistry(repository, enabledProperties(), mock(ApplicationContext.class));

        Long first = registry.featureId(FeatureKind.GIT, "localvc", "fetch/tests", UserFeature.PROGRAMMING_REPOSITORY_EDITING, FeatureInteraction.VIEW);
        Long second = registry.featureId(FeatureKind.GIT, "localvc", "fetch/tests", UserFeature.PROGRAMMING_REPOSITORY_EDITING, FeatureInteraction.VIEW);

        assertThat(first).isEqualTo(9L);
        assertThat(second).isEqualTo(9L);
        verify(repository).updateClassification(9L, UserFeature.PROGRAMMING_REPOSITORY_EDITING.name(), FeatureInteraction.VIEW, null);
        verify(repository, never()).save(any(TrackedFeature.class));
    }

    @Test
    void shouldReportNoLabelForAnUnannotatedEndpoint() {
        var descriptor = describe(LegacyAliasResource.class, "list", RequestMethod.GET, "/api/course/courses");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.label()).isNull();
    }

    @Test
    void shouldIgnoreAMappingWithoutAPath() {
        var mappingInfo = RequestMappingInfo.paths().methods(RequestMethod.GET).build();

        assertThat(FeatureUsageRegistry.describe(mappingInfo, handlerMethod(LegacyAliasResource.class, "list"))).isNull();
    }

    private static FeatureUsageRegistry.EndpointDescriptor describe(Class<?> controller, String methodName, RequestMethod verb, String... patterns) {
        return FeatureUsageRegistry.describe(RequestMappingInfo.paths(patterns).methods(verb).build(), handlerMethod(controller, methodName));
    }

    private static HandlerMethod handlerMethod(Class<?> controller, String methodName) {
        Method method = findMethod(controller, methodName);
        try {
            return new HandlerMethod(controller.getDeclaredConstructor().newInstance(), method);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method findMethod(Class<?> controller, String methodName) {
        for (Method method : controller.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new IllegalStateException("No method " + methodName + " on " + controller);
    }

    @RequestMapping({ "api/course/", "api/core/course/" })
    static class LegacyAliasResource {

        public void list() {
            // only its signature and annotations matter
        }
    }

    static class NoClassPrefixResource {

        @GetMapping({ "api/zzz/last", "api/aaa/first" })
        public void list() {
            // only its signature and annotations matter
        }
    }

    @FeatureUsage(UserFeature.HYPERION_PROBLEM_STATEMENT)
    @RequestMapping("api/course/")
    static class LabelledResource {

        @FeatureUsage(UserFeature.HYPERION_CONSISTENCY_CHECK)
        public void labelledOnMethod() {
            // only its signature and annotations matter
        }

        public void unlabelled() {
            // only its signature and annotations matter
        }

        @UsageInteraction(FeatureInteraction.AUTOMATIC)
        public void polled() {
            // only its signature and annotations matter
        }
    }

    @Internal
    @FeatureUsage(UserFeature.IRIS_CHAT)
    @RequestMapping("api/iris/internal/")
    static class InternalResource {

        public void callback() {
            // only its signature and annotations matter
        }

        @UsageInteraction(FeatureInteraction.VIEW)
        public void overridden() {
            // only its signature and annotations matter
        }
    }
}
