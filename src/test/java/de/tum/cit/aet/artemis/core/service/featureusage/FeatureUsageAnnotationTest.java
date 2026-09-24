package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Enforces that every REST controller declares which user-facing feature it serves, that the {@link UserFeature}
 * catalogue stays complete and free of dead entries, and keeps the generated catalogue document in the documentation in
 * step with the annotations.
 * <p>
 * The annotation is the single source of truth: it sits next to the controller, so whoever adds or changes one decides the
 * feature, and a rename cannot leave a dangling reference behind. This mirrors how Artemis already handles authorization,
 * where {@code everyRestEndpointMustBeAuthorized} requires an annotation on every endpoint rather than keeping a central
 * list.
 * <p>
 * What an annotation cannot do on its own is show the catalogue as a whole: which features one area has, which modules
 * and controllers implement a feature, and how its calls count. So one test renders all of that into a checked-in
 * document. Reviewing that file is how the mapping stays under control, and the test failing on a stale file is what stops
 * the document quietly becoming fiction.
 * <p>
 * The interaction of every endpoint is derived with {@link FeatureUsageClassification}, the same code the startup
 * inventory uses, so the document shows exactly what the admin page will count.
 * <p>
 * Uses ArchUnit's class scanning to find the controllers, because {@code ArchitectureTest.testNoRestControllersImported}
 * forbids importing a {@code @RestController}; the handler methods are then read reflectively.
 */
class FeatureUsageAnnotationTest extends AbstractArchitectureTest {

    private static final Path CATALOGUE_DOCUMENT = Path.of("documentation", "docs", "developer", "feature-usage-catalogue.mdx");

    private static final Path TRANSLATIONS = Path.of("src", "main", "webapp", "i18n");

    private static final Path MESSAGES = Path.of("src", "main", "resources", "i18n");

    private static final List<String> LANGUAGES = List.of("en", "de");

    /** The path prefixes WebConfigurer registers the feature usage interceptor for. Keep in step with that method. */
    private static final Set<String> INTERCEPTED_PREFIXES = Set.of("/api/", "/.well-known/");

    /** Set to true to rewrite the document after a deliberate catalogue change. */
    private static final String UPDATE_FLAG = "updateFeatureUsageCatalogue";

    @Test
    void everyRestControllerShouldDeclareItsFeature() {
        Set<String> undeclared = controllers().stream().filter(controller -> !controller.isAnnotatedWith(FeatureUsage.class)).map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(undeclared).as("""
                These REST controllers carry no @FeatureUsage, so their usage would be reported by raw path instead of under a \
                feature. Annotate each one with the UserFeature it serves.""").isEmpty();
    }

    /**
     * An annotated controller whose paths the interceptor never sees is worse than an unannotated one: it enters the
     * inventory and then reports zero usage forever, which reads as a dead feature rather than a gap in measurement.
     * <p>
     * That is what happened to the app-site-association resources, which map to {@code .well-known/} deliberately,
     * outside the api prefix, while the interceptor was registered for {@code /api/**} alone. This pins every annotated
     * controller's mapping against the prefixes WebConfigurer actually registers, so the next controller mapped outside
     * them fails here rather than quietly reporting nothing.
     */
    @Test
    void everyAnnotatedControllerShouldBeMappedWhereTheInterceptorObserves() {
        Set<String> unobserved = controllers().stream().filter(controller -> controller.isAnnotatedWith(FeatureUsage.class))
                .filter(controller -> mappingsOf(controller).stream().anyMatch(mapping -> INTERCEPTED_PREFIXES.stream().noneMatch(mapping::startsWith))).map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unobserved).as("These controllers carry @FeatureUsage but are mapped outside the paths the feature usage interceptor is registered "
                + "for in WebConfigurer.addInterceptors, so their usage can never be recorded and they would be reported as permanently unused. "
                + "Either map them under an intercepted prefix or add their prefix there (currently %s).".formatted(INTERCEPTED_PREFIXES)).isEmpty();
    }

    /**
     * A catalogue entry that nothing records is a feature the page reports as unused forever. That is the same lie as an
     * unobserved controller, from the other side, and it would accumulate silently as endpoints move between features.
     */
    @Test
    void everyUserFeatureShouldBeServedOrRecorded() {
        Set<UserFeature> tracked = EnumSet.noneOf(UserFeature.class);
        endpoints().forEach(endpoint -> tracked.add(endpoint.feature()));
        tracked.addAll(recordersByFeature().keySet());

        Set<UserFeature> dead = EnumSet.complementOf(EnumSet.copyOf(tracked));
        assertThat(dead).as("""
                These catalogue entries are neither assigned to an endpoint with @FeatureUsage nor recorded through \
                FeatureUsageCollector.recordUsage, so the admin page would report them as unused forever. Assign an endpoint, \
                record them where they happen, or remove them from UserFeature.""").isEmpty();
    }

    /**
     * The page and the catalogue document show a feature by its translated name, so a constant without one would appear
     * as its raw enum name, and only in the language nobody checked.
     */
    @Test
    void everyUserFeatureAndProductAreaShouldBeTranslated() throws IOException {
        for (String language : LANGUAGES) {
            JsonNode catalogue = catalogueTranslations(language);
            Set<String> missing = new TreeSet<>();
            for (ProductArea area : ProductArea.values()) {
                if (catalogue.path("area").path(area.name()).asString("").isBlank()) {
                    missing.add("area." + area.name());
                }
            }
            for (UserFeature feature : UserFeature.values()) {
                for (String field : List.of("name", "description")) {
                    if (catalogue.path("feature").path(feature.name()).path(field).asString("").isBlank()) {
                        missing.add("feature." + feature.name() + "." + field);
                    }
                }
            }
            assertThat(missing).as("artemisApp.featureUsage.catalog keys missing in %s/featureUsage.json", language).isEmpty();
        }
    }

    /**
     * The weekly email names each product area it lists. The template looks the name up by a key built from the enum
     * constant, which no compiler checks, so a new area without its message would reach an administrator as
     * {@code ??email.featureUsageDigest.area.X??}.
     */
    @Test
    void everyProductAreaShouldHaveAnEmailMessage() throws IOException {
        for (String bundle : List.of("messages.properties", "messages_en.properties", "messages_de.properties")) {
            Properties messages = new Properties();
            try (var reader = Files.newBufferedReader(Path.of(System.getProperty("user.dir")).resolve(MESSAGES).resolve(bundle), StandardCharsets.UTF_8)) {
                messages.load(reader);
            }
            Set<String> missing = Arrays.stream(ProductArea.values()).map(area -> "email.featureUsageDigest.area." + area.name())
                    .filter(key -> messages.getProperty(key, "").isBlank()).collect(Collectors.toCollection(TreeSet::new));
            assertThat(missing).as("product area names missing in %s", bundle).isEmpty();
        }
    }

    /**
     * An override that repeats what would be derived anyway says nothing, and it hides the ones that matter: every
     * {@code @UsageInteraction} should be an exception somebody decided on.
     */
    @Test
    void noUsageInteractionShouldRepeatTheDerivedInteraction() {
        Set<String> redundant = endpoints().stream().filter(endpoint -> endpoint.override() != null && endpoint.override() == endpoint.derivedInteraction()).map(Endpoint::describe)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(redundant).as("These @UsageInteraction overrides repeat the interaction the verb already implies; remove them").isEmpty();
    }

    /**
     * Several handler methods can share one verb and path and differ only in their request parameters. The inventory
     * keys on verb and path, so they share one row, and the classification of whichever handler it sees first wins. If
     * they disagreed, the report would depend on the order Spring enumerates handlers.
     */
    @Test
    void endpointsSharingAPathShouldShareTheirClassification() {
        Map<String, Set<String>> classificationsByIdentifier = new TreeMap<>();
        for (Endpoint endpoint : endpoints()) {
            classificationsByIdentifier.computeIfAbsent(endpoint.identifier(), identifier -> new TreeSet<>()).add(endpoint.feature() + " " + endpoint.interaction());
        }
        Map<String, Set<String>> conflicting = classificationsByIdentifier.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, TreeMap::new));

        assertThat(conflicting).as("handler methods sharing a verb and path must declare the same feature and interaction").isEmpty();
    }

    /**
     * A method level assignment is the only way to split a controller that serves several features, so the catalogue has
     * to show it: that document is the review surface for the mapping.
     */
    @Test
    void theCatalogueShouldResolveMethodLevelAssignments() {
        Map<UserFeature, Set<String>> resourcesByFeature = new EnumMap<>(UserFeature.class);
        endpoints().forEach(endpoint -> resourcesByFeature.computeIfAbsent(endpoint.feature(), feature -> new TreeSet<>()).add(endpoint.resource()));

        assertThat(resourcesByFeature.get(UserFeature.HYPERION_CONSISTENCY_CHECK)).as("a method-level @FeatureUsage splits a controller")
                .contains("HyperionProblemStatementResource");
        assertThat(resourcesByFeature.get(UserFeature.HYPERION_PROBLEM_STATEMENT)).as("the controller keeps its class-level feature").contains("HyperionProblemStatementResource");
    }

    @Test
    void theGeneratedCatalogueDocumentShouldBeUpToDate() throws IOException {
        String expected = renderCatalogue();
        Path path = Path.of(System.getProperty("user.dir")).resolve(CATALOGUE_DOCUMENT);

        if (Boolean.getBoolean(UPDATE_FLAG)) {
            // Files.write* is forbidden by an architecture rule because it does not create missing directories
            FileUtils.writeStringToFile(path.toFile(), expected, StandardCharsets.UTF_8);
        }

        assertThat(Files.exists(path)).as("%s is missing; regenerate it with -D%s=true", CATALOGUE_DOCUMENT, UPDATE_FLAG).isTrue();
        assertThat(Files.readString(path)).as("""
                %s no longer matches the @FeatureUsage annotations. Read the diff first: it is the review surface for the \
                whole catalogue, so an unexpected change there usually means an endpoint went to the wrong feature. Regenerate \
                with ./gradlew test --tests FeatureUsageAnnotationTest -D%s=true""".formatted(CATALOGUE_DOCUMENT, UPDATE_FLAG)).isEqualTo(expected);
    }

    @Test
    void shouldKeepTheCatalogueAtAReadableSize() {
        Map<ProductArea, Long> featuresPerArea = Arrays.stream(UserFeature.values()).collect(Collectors.groupingBy(UserFeature::getArea, Collectors.counting()));

        // Deliberately loose bounds. The point is not an exact number but that the page stays navigable: a handful of
        // features would stop answering which part of an area is used, and one per endpoint would just be the raw list.
        assertThat(UserFeature.values().length).as("features in the catalogue").isBetween(80, 200);
        assertThat(featuresPerArea.keySet()).as("every product area lists at least one feature").containsExactlyInAnyOrder(ProductArea.values());
        assertThat(featuresPerArea.values()).as("features per product area").allMatch(count -> count <= 20);
    }

    /**
     * Renders area, feature, the modules and controllers behind it and how their endpoints count, followed by every
     * interaction override. Grouped features and split controllers are deliberate decisions and have to be reviewable.
     */
    private String renderCatalogue() throws IOException {
        JsonNode names = catalogueTranslations("en");
        List<Endpoint> endpoints = endpoints();
        Map<UserFeature, List<Endpoint>> endpointsByFeature = endpoints.stream()
                .collect(Collectors.groupingBy(Endpoint::feature, () -> new EnumMap<>(UserFeature.class), Collectors.toCollection(ArrayList::new)));
        Map<UserFeature, Set<String>> recorders = recordersByFeature();

        StringBuilder document = new StringBuilder("""
                ---
                id: feature-usage-catalogue
                title: Feature Usage Catalogue
                sidebar_label: Feature Usage Catalogue
                ---

                The features whose usage Artemis tracks, as users know them, and the modules and REST resources that implement
                each one. The catalogue is the `UserFeature` enum; `@FeatureUsage` on each controller or handler method assigns
                an endpoint to one of its entries.

                **This file is generated.** Do not edit it by hand. It is checked in so that the catalogue as a whole can be
                reviewed in one place and so that a change to it shows up in a pull request diff.
                `FeatureUsageAnnotationTest` fails when it drifts from the annotations; regenerate it with:

                ```bash
                ./gradlew test --tests FeatureUsageAnnotationTest -DupdateFeatureUsageCatalogue=true
                ```

                For every resource, the columns count its endpoints by how their calls are reported: **Actions** and **Views**
                are use of the feature, **Automatic** calls are made by the client on its own and **System** calls by another
                system, and neither of the last two counts as use. The interaction follows from the HTTP verb unless
                `@UsageInteraction` overrides it; every override is listed at the end. See
                [Feature Usage Analysis](/developer/feature-usage) for how the tracking works.

                """);
        long modules = endpoints.stream().map(Endpoint::module).distinct().count();
        document.append("Currently %d product areas, %d features, %d REST endpoints in %d modules, and %d interaction overrides.%n%n".formatted(ProductArea.values().length,
                UserFeature.values().length, endpoints.size(), modules, endpoints.stream().filter(endpoint -> endpoint.override() != null).count()));

        for (ProductArea area : ProductArea.values()) {
            document.append("## %s%n%n".formatted(names.path("area").path(area.name()).asString(area.name())));
            for (UserFeature feature : UserFeature.values()) {
                if (feature.getArea() != area) {
                    continue;
                }
                JsonNode featureNames = names.path("feature").path(feature.name());
                document.append("### %s%n%n".formatted(featureNames.path("name").asString(feature.name())));
                document.append("`%s`: %s%n%n".formatted(feature.name(), featureNames.path("description").asString("")));
                List<Endpoint> featureEndpoints = endpointsByFeature.getOrDefault(feature, List.of());
                if (!featureEndpoints.isEmpty()) {
                    document.append("| Module | Resource | Actions | Views | Automatic | System |\n");
                    document.append("|--------|----------|--------:|------:|----------:|-------:|\n");
                    Map<String, List<Endpoint>> byResource = featureEndpoints.stream()
                            .collect(Collectors.groupingBy(endpoint -> endpoint.module() + " " + endpoint.resource(), TreeMap::new, Collectors.toCollection(ArrayList::new)));
                    byResource.forEach((key,
                            resourceEndpoints) -> document.append("| %s | `%s` | %d | %d | %d | %d |%n".formatted(resourceEndpoints.getFirst().module(),
                                    resourceEndpoints.getFirst().resource(), count(resourceEndpoints, FeatureInteraction.ACTION), count(resourceEndpoints, FeatureInteraction.VIEW),
                                    count(resourceEndpoints, FeatureInteraction.AUTOMATIC), count(resourceEndpoints, FeatureInteraction.SYSTEM))));
                    document.append('\n');
                }
                Set<String> recordedBy = recorders.getOrDefault(feature, Set.of());
                if (!recordedBy.isEmpty()) {
                    document.append("Also recorded outside REST by %s.%n%n".formatted(recordedBy.stream().map(name -> '`' + name + '`').collect(Collectors.joining(", "))));
                }
            }
        }

        document.append("## Interaction overrides\n\n");
        document.append("The endpoints whose calls do not count the way their HTTP verb suggests, grouped by how they count instead.\n\n");
        for (FeatureInteraction interaction : FeatureInteraction.values()) {
            List<Endpoint> overridden = endpoints.stream().filter(endpoint -> endpoint.override() == interaction).sorted(Comparator.comparing(Endpoint::identifier)).toList();
            if (overridden.isEmpty()) {
                continue;
            }
            document.append("### %s%n%n".formatted(switch (interaction) {
                case ACTION -> "Counted as actions";
                case VIEW -> "Counted as views";
                case AUTOMATIC -> "Counted as automatic calls";
                case SYSTEM -> "Counted as system calls";
            }));
            overridden.forEach(endpoint -> document.append("* `%s` (`%s`)%n".formatted(endpoint.identifier(), endpoint.feature().name())));
            document.append('\n');
        }
        return document.toString().stripTrailing() + "\n";
    }

    private static long count(List<Endpoint> endpoints, FeatureInteraction interaction) {
        return endpoints.stream().filter(endpoint -> endpoint.interaction() == interaction).count();
    }

    /**
     * The features recorded explicitly, by the classes that record them: the ones that pass a {@link UserFeature}
     * constant to {@link FeatureUsageCollector}. Restricted to classes that call the collector, because referencing a
     * constant for another reason does not make a feature measured.
     */
    private Map<UserFeature, Set<String>> recordersByFeature() {
        JavaClass catalogue = productionClasses.get(UserFeature.class);
        Map<UserFeature, Set<String>> recorders = new EnumMap<>(UserFeature.class);
        for (JavaFieldAccess access : catalogue.getFieldAccessesToSelf()) {
            JavaClass origin = access.getOriginOwner();
            if (origin.isEquivalentTo(UserFeature.class) || !callsTheCollector(origin)) {
                continue;
            }
            UserFeature feature = Arrays.stream(UserFeature.values()).filter(constant -> constant.name().equals(access.getName())).findFirst().orElse(null);
            if (feature != null) {
                recorders.computeIfAbsent(feature, key -> new TreeSet<>()).add(topLevelSimpleName(origin));
            }
        }
        return recorders;
    }

    private static boolean callsTheCollector(JavaClass origin) {
        return origin.getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTargetOwner().isEquivalentTo(FeatureUsageCollector.class) && "recordUsage".equals(call.getTarget().getName()));
    }

    private static String topLevelSimpleName(JavaClass javaClass) {
        String name = javaClass.getName();
        String simple = name.substring(name.lastIndexOf('.') + 1);
        int nested = simple.indexOf('$');
        return nested < 0 ? simple : simple.substring(0, nested);
    }

    private static JsonNode catalogueTranslations(String language) throws IOException {
        Path file = Path.of(System.getProperty("user.dir")).resolve(TRANSLATIONS).resolve(language).resolve("featureUsage.json");
        return JsonObjectMapper.get().readTree(Files.readString(file)).path("artemisApp").path("featureUsage").path("catalog");
    }

    /**
     * Every endpoint of every controller, classified the way the startup inventory classifies it.
     */
    private List<Endpoint> endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (JavaClass controller : controllers()) {
            Class<?> beanType = controller.reflect();
            String prefix = mappingsOf(controller).stream().findFirst().orElse("/");
            for (Method method : beanType.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                UserFeature feature = FeatureUsageClassification.featureOf(method, beanType);
                if (mapping == null || feature == null) {
                    continue;
                }
                Set<RequestMethod> verbs = EnumSet.noneOf(RequestMethod.class);
                verbs.addAll(Arrays.asList(mapping.method()));
                String path = mapping.path().length > 0 ? mapping.path()[0] : "";
                String identifier = verbs.stream().map(RequestMethod::name).sorted().collect(Collectors.joining(",")) + " " + joinPath(prefix, path);
                UsageInteraction override = method.getAnnotation(UsageInteraction.class);
                endpoints.add(new Endpoint(identifier, moduleOf(controller), controller.getSimpleName(), method.getName(), feature,
                        FeatureUsageClassification.interactionOf(method, beanType, verbs), FeatureUsageClassification.derivedInteractionOf(method, beanType, verbs),
                        override == null ? null : override.value()));
            }
        }
        endpoints.sort(Comparator.comparing(Endpoint::identifier).thenComparing(Endpoint::handler));
        return endpoints;
    }

    private static String joinPath(String prefix, String path) {
        String joined = (prefix.endsWith("/") ? prefix : prefix + "/") + (path.startsWith("/") ? path.substring(1) : path);
        return joined.startsWith("/") ? joined.substring(1) : joined;
    }

    private static String moduleOf(JavaClass controller) {
        String remainder = controller.getPackageName().substring("de.tum.cit.aet.artemis.".length());
        int separator = remainder.indexOf('.');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }

    /**
     * The class-level request mappings of a controller, normalised to a leading slash the way Spring resolves them, so
     * a mapping declared without one compares alike to one declared with it.
     */
    private static Set<String> mappingsOf(JavaClass controller) {
        return controller.tryGetAnnotationOfType(RequestMapping.class).<Set<String>>map(
                mapping -> Arrays.stream(mapping.value()).map(value -> value.startsWith("/") ? value : "/" + value).collect(Collectors.toCollection(LinkedHashSet<String>::new)))
                .orElseGet(Set::of);
    }

    private Set<JavaClass> controllers() {
        return productionClasses.stream().filter(javaClass -> javaClass.isAnnotatedWith(RestController.class)).collect(Collectors.toSet());
    }

    /**
     * One endpoint as the startup inventory sees it.
     *
     * @param identifier         the verb and path, as the inventory keys it
     * @param module             the module derived from the controller's package
     * @param resource           the controller's simple name
     * @param handler            the handler method's name
     * @param feature            the feature the endpoint serves
     * @param interaction        how its calls count
     * @param derivedInteraction how its calls would count without an override
     * @param override           the explicit {@link UsageInteraction}, if any
     */
    private record Endpoint(String identifier, String module, String resource, String handler, UserFeature feature, FeatureInteraction interaction,
            FeatureInteraction derivedInteraction, @Nullable FeatureInteraction override) {

        String describe() {
            return "%s (%s.%s)".formatted(identifier, resource, handler);
        }
    }
}
