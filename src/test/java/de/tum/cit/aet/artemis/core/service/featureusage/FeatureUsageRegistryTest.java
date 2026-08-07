package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * Tests how an endpoint is turned into an inventory identifier.
 * <p>
 * The case that matters most is the canonical path: 29 controllers map a canonical prefix plus one or more deprecated
 * legacy aliases in a single {@code @RequestMapping}. Picking the wrong one would split a feature across two identifiers,
 * and neither row would then show its real usage.
 */
class FeatureUsageRegistryTest {

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
    void shouldReadTheFeatureLabelFromTheMethod() {
        var descriptor = describe(LabelledResource.class, "labelledOnMethod", RequestMethod.GET, "/api/course/courses");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.label()).isEqualTo("configuration/method-label");
    }

    @Test
    void shouldFallBackToTheFeatureLabelOfTheController() {
        var descriptor = describe(LabelledResource.class, "unlabelled", RequestMethod.GET, "/api/course/courses");

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.label()).isEqualTo("configuration/class-label");
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

    @FeatureUsage("configuration/class-label")
    @RequestMapping("api/course/")
    static class LabelledResource {

        @FeatureUsage("configuration/method-label")
        public void labelledOnMethod() {
            // only its signature and annotations matter
        }

        public void unlabelled() {
            // only its signature and annotations matter
        }
    }
}
