package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
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

    /** Set to true to rewrite the document after a deliberate taxonomy change. */
    private static final String UPDATE_FLAG = "updateFeatureUsageCatalogue";

    @Test
    void everyRestControllerShouldDeclareItsFeature() {
        Set<String> undeclared = controllers().stream().filter(controller -> labelOf(controller) == null).map(JavaClass::getName).collect(Collectors.toCollection(TreeSet::new));

        assertThat(undeclared).as("""
                These REST controllers carry no @FeatureUsage, so their usage would be reported by raw path under "other" \
                instead of a named feature. Annotate each one with the "area/feature" it belongs to.""").isEmpty();
    }

    @Test
    void everyFeatureLabelShouldBeAnAreaAndAFeatureInKebabCase() {
        Set<String> malformed = controllers().stream().map(FeatureUsageAnnotationTest::labelOf).filter(java.util.Objects::nonNull)
                .filter(label -> !label.matches("[a-z0-9]+(-[a-z0-9]+)*/[a-z0-9]+(-[a-z0-9]+)*")).collect(Collectors.toCollection(TreeSet::new));

        // the admin page splits the label on the slash to build the tree, so exactly one level of nesting is expected
        assertThat(malformed).as("@FeatureUsage values must be \"area/feature\" in kebab-case").isEmpty();
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
            String label = labelOf(controller);
            if (label == null || !label.contains("/")) {
                continue;
            }
            String module = moduleOf(controller);
            String area = label.substring(0, label.indexOf('/'));
            String feature = label.substring(label.indexOf('/') + 1);
            controllersByModuleAreaFeature.computeIfAbsent(module + ' ' + area + ' ' + feature, key -> new TreeSet<>()).add(controller.getSimpleName());
        }
        controllersByModuleAreaFeature.forEach((key, controllerNames) -> {
            String[] parts = key.split(" ");
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

    private static String labelOf(JavaClass controller) {
        return controller.tryGetAnnotationOfType(FeatureUsage.class).map(FeatureUsage::value).orElse(null);
    }

    private Set<JavaClass> controllers() {
        return productionClasses.stream().filter(javaClass -> javaClass.isAnnotatedWith(RestController.class)).collect(Collectors.toSet());
    }
}
