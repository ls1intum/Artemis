package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClass;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

/**
 * Enforces that every REST controller declares which feature it belongs to, and keeps the generated catalogue in the
 * documentation in step with the annotations.
 * <p>
 * The annotation is the single source of truth: it sits next to the controller, so whoever adds or changes one decides the
 * label, and a rename cannot leave a dangling reference behind. This mirrors how Artemis already handles authorization,
 * where {@code everyRestEndpointMustBeAuthorized} requires an annotation on every endpoint rather than keeping a central
 * list.
 * <p>
 * What an annotation cannot do on its own is show the taxonomy as a whole: whether one module has twenty features and
 * another has two, or whether two modules named the same area differently. So the second test renders the taxonomy from the
 * annotations into a checked-in document. Reviewing that file is how the shape of the taxonomy stays under control, and the
 * test failing on a stale file is what stops the document quietly becoming fiction.
 * <p>
 * Uses ArchUnit's class scanning rather than reflection, because
 * {@code ArchitectureTest.testNoRestControllersImported} forbids importing a {@code @RestController}.
 */
class FeatureUsageAnnotationTest extends AbstractArchitectureTest {

    private static final Path CATALOGUE_DOCUMENT = Path.of("documentation", "docs", "developer", "feature-usage-catalogue.mdx");

    /**
     * Separator for the flattened {@code module/area/feature} map key. A control character rather than a space or a
     * slash, because all three parts may contain either, and it is written as an escape so it stays visible in source.
     */
    private static final String KEY_SEPARATOR = "\u001F";

    /** The path prefixes WebConfigurer registers the feature usage interceptor for. Keep in step with that method. */
    private static final Set<String> INTERCEPTED_PREFIXES = Set.of("/api/", "/.well-known/");

    /** Set to true to rewrite the document after a deliberate taxonomy change. */
    private static final String UPDATE_FLAG = "updateFeatureUsageCatalogue";

    @Test
    void everyRestControllerShouldDeclareItsFeature() {
        Set<String> undeclared = controllers().stream().filter(controller -> labelOf(controller) == null).map(JavaClass::getName).collect(Collectors.toCollection(TreeSet::new));

        assertThat(undeclared).as("""
                These REST controllers carry no @FeatureUsage, so their usage would be reported by raw path under "other" \
                instead of a named feature. Annotate each one with the "area/feature" it belongs to.""").isEmpty();
    }

    /**
     * Covers method-level overrides as well as controller-level labels. The interceptor resolves the annotation on the
     * handler method before falling back to the class, so a malformed override would reach the inventory; validating only
     * the class level would let it through.
     */
    @Test
    void everyFeatureLabelShouldBeAnAreaAndAFeatureInKebabCase() {
        Set<String> malformed = effectiveLabels().stream().filter(label -> !label.matches("[a-z0-9]+(-[a-z0-9]+)*/[a-z0-9]+(-[a-z0-9]+)*"))
                .collect(Collectors.toCollection(TreeSet::new));

        // the admin page splits the label on the slash to build the tree, so exactly one level of nesting is expected
        assertThat(malformed).as("@FeatureUsage values must be \"area/feature\" in kebab-case").isEmpty();
    }

    /**
     * A method-level override names a feature of its own, so it belongs in the catalogue: that document is the review
     * surface for the taxonomy, and a feature tracked at runtime but missing from it cannot be reviewed.
     */
    @Test
    void theCatalogueShouldContainMethodLevelOverrides() {
        assertThat(effectiveLabels()).as("method-level @FeatureUsage overrides are part of the taxonomy").contains("configuration/re-evaluate-results");

        Set<String> configurationFeatures = taxonomy().getOrDefault("programming", Map.of()).getOrDefault("configuration", Set.of());
        assertThat(configurationFeatures).as("the catalogue lists the method-level override configuration/re-evaluate-results")
                .anyMatch(entry -> entry.contains("re-evaluate-results"));
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
        Set<String> unobserved = controllers().stream().filter(controller -> labelOf(controller) != null)
                .filter(controller -> mappingsOf(controller).stream().anyMatch(mapping -> INTERCEPTED_PREFIXES.stream().noneMatch(mapping::startsWith))).map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unobserved).as("These controllers carry @FeatureUsage but are mapped outside the paths the feature usage interceptor is registered "
                + "for in WebConfigurer.addInterceptors, so their usage can never be recorded and they would be reported as permanently unused. "
                + "Either map them under an intercepted prefix or add their prefix there (currently %s).".formatted(INTERCEPTED_PREFIXES)).isEmpty();
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
                whole taxonomy, so an unexpected change there usually means a label went to the wrong area. Regenerate with \
                ./gradlew test --tests FeatureUsageAnnotationTest -D%s=true""".formatted(CATALOGUE_DOCUMENT, UPDATE_FLAG)).isEqualTo(expected);
    }

    @Test
    void shouldKeepTheTaxonomyAtAReadableSize() {
        Map<String, Map<String, Set<String>>> taxonomy = taxonomy();
        long features = taxonomy.values().stream().flatMap(areas -> areas.values().stream()).mapToLong(Set::size).sum();

        // Deliberately loose bounds. The point is not an exact number but that the tree stays navigable: a handful of
        // features would stop answering the sub-feature question, and one per endpoint would just be the raw endpoint list.
        assertThat(features).as("features across the whole taxonomy").isBetween(100L, 260L);
        assertThat(taxonomy.keySet()).as("modules").hasSizeGreaterThan(20);
    }

    /**
     * Renders module, area and feature with the controllers behind each, which is what makes grouped features visible: five
     * programming exercise controllers collapsing into one feature is a deliberate decision and has to be reviewable.
     */
    private String renderCatalogue() {
        StringBuilder document = new StringBuilder("""
                ---
                id: feature-usage-catalogue
                title: Feature Usage Catalogue
                sidebar_label: Feature Usage Catalogue
                ---

                # Feature Usage Catalogue

                The features whose usage Artemis tracks, as declared by `@FeatureUsage` on each REST controller.

                **This file is generated.** Do not edit it by hand. It is checked in so that the taxonomy as a whole can be
                reviewed in one place and so that a change to it shows up in a pull request diff.
                `FeatureUsageAnnotationTest` fails when it drifts from the annotations; regenerate it with:

                ```bash
                ./gradlew test --tests FeatureUsageAnnotationTest -DupdateFeatureUsageCatalogue=true
                ```

                A feature is usually one controller. Where several controllers are one thing to a user they share a label,
                which is why the controllers are listed. See
                [Feature Usage Analysis](/developer/feature-usage) for how the tracking works.

                """);

        Map<String, Map<String, Set<String>>> taxonomy = taxonomy();
        long features = taxonomy.values().stream().flatMap(areas -> areas.values().stream()).mapToLong(Set::size).sum();
        long areas = taxonomy.values().stream().mapToLong(Map::size).sum();
        document.append("Currently %d modules, %d areas and %d features.%n%n".formatted(taxonomy.size(), areas, features));

        taxonomy.forEach((module, areasOfModule) -> {
            document.append("## %s%n%n".formatted(module));
            areasOfModule.forEach((area, featuresOfArea) -> {
                document.append("### %s%n%n".formatted(area));
                featuresOfArea.forEach(feature -> document.append("* %s%n".formatted(feature)));
                document.append('\n');
            });
        });
        return document.toString();
    }

    /** Module to area to the {@code feature (Controller, Controller)} lines below it, all sorted for a stable document. */
    private Map<String, Map<String, Set<String>>> taxonomy() {
        Map<String, Map<String, Set<String>>> taxonomy = new TreeMap<>();
        Map<String, Set<String>> controllersByModuleAreaFeature = new TreeMap<>();
        for (JavaClass controller : controllers()) {
            String module = moduleOf(controller);
            // A method-level override is a separate feature at runtime, so it is listed beside the controller's own label
            // rather than folded into it.
            for (String label : labelsOf(controller)) {
                if (!label.contains("/")) {
                    continue;
                }
                String area = label.substring(0, label.indexOf('/'));
                String feature = label.substring(label.indexOf('/') + 1);
                controllersByModuleAreaFeature.computeIfAbsent(module + KEY_SEPARATOR + area + KEY_SEPARATOR + feature, key -> new TreeSet<>()).add(controller.getSimpleName());
            }
        }
        controllersByModuleAreaFeature.forEach((key, controllerNames) -> {
            String[] parts = key.split(KEY_SEPARATOR);
            taxonomy.computeIfAbsent(parts[0], module -> new TreeMap<>()).computeIfAbsent(parts[1], area -> new TreeSet<>())
                    .add("**%s** (%s)".formatted(parts[2], controllerNames.stream().map(name -> '`' + name + '`').collect(Collectors.joining(", "))));
        });
        return taxonomy;
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
        return controller.tryGetAnnotationOfType(RequestMapping.class)
                .<Set<String>>map(
                        mapping -> Arrays.stream(mapping.value()).map(value -> value.startsWith("/") ? value : "/" + value).collect(Collectors.toCollection(TreeSet<String>::new)))
                .orElseGet(Set::of);
    }

    private static String labelOf(JavaClass controller) {
        return controller.tryGetAnnotationOfType(FeatureUsage.class).map(FeatureUsage::value).orElse(null);
    }

    /**
     * The {@code @FeatureUsage} values declared on a controller's handler methods. The interceptor prefers the method
     * annotation over the class one, so each of these is a feature in its own right rather than a variant of the
     * controller's label.
     */
    private static Set<String> methodLabelsOf(JavaClass controller) {
        return controller.getMethods().stream().map(method -> method.tryGetAnnotationOfType(FeatureUsage.class).map(FeatureUsage::value).orElse(null)).filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Every label the interceptor can record: the controller labels plus the method-level overrides. */
    private Set<String> effectiveLabels() {
        return controllers().stream().flatMap(controller -> labelsOf(controller).stream()).collect(Collectors.toCollection(TreeSet::new));
    }

    /** The labels one controller contributes: its own, when annotated, and every method-level override it declares. */
    private static Set<String> labelsOf(JavaClass controller) {
        Set<String> labels = new TreeSet<>(methodLabelsOf(controller));
        String classLabel = labelOf(controller);
        if (classLabel != null) {
            labels.add(classLabel);
        }
        return labels;
    }

    private Set<JavaClass> controllers() {
        return productionClasses.stream().filter(javaClass -> javaClass.isAnnotatedWith(RestController.class)).collect(Collectors.toSet());
    }
}
