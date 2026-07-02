package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Runtime configuration for the GPU/Docker-gated Hyperion exercise-generation tests, applied by mutating the already-built Spring beans in {@code @BeforeEach} instead of
 * {@code @TestPropertySource}.
 * <p>
 * The shared test {@code application.yml} points every build image at the {@code ~~invalid~~} placeholder so the normal (mocked-build) buckets never pull a real ~1&nbsp;GB image.
 * The real-build tests here need the production execution image for whatever language they exercise, and the GPU agent needs the gpt-oss-120b deployment's real 65536-token context
 * window. Supplying these via {@code @TestPropertySource} is not an option: a per-class property set forks a fresh Spring context (and is forbidden by
 * {@code SpringContextConfigurationArchitectureTest}). Because these tests run single-threaded and only when their {@code HYPERION_E2E_GPU=true} / Docker gate is satisfied,
 * overriding the values directly on the shared beans (the same in-place bean mutation pattern these tests already use via {@link ReflectionTestUtils} for the Docker connection
 * URI) is equivalent at runtime and keeps the context shared.
 *
 * @see de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration
 * @see de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.AgentLoopRunner
 */
final class HyperionGpuTestEnvironment {

    /** The gpt-oss-120b deployment's usable context window — the agent's window-aware compaction must target this, not the 128k default. */
    static final int GPU_CONTEXT_WINDOW_TOKENS = 65536;

    /**
     * The production execution image per language, keyed by the {@link ProgrammingLanguage} whose {@code default} (i.e. {@link ProjectType#PLAIN}) image must be overridden.
     */
    private static final Map<ProgrammingLanguage, String> PRODUCTION_IMAGES = productionImages();

    private HyperionGpuTestEnvironment() {
    }

    private static Map<ProgrammingLanguage, String> productionImages() {
        Map<ProgrammingLanguage, String> images = new LinkedHashMap<>();
        images.put(ProgrammingLanguage.JAVA, "ls1tum/artemis-maven-template:java17-25");
        images.put(ProgrammingLanguage.KOTLIN, "ls1tum/artemis-maven-template:java17-25");
        images.put(ProgrammingLanguage.PYTHON, "ls1tum/artemis-python-docker:v1.1.0");
        images.put(ProgrammingLanguage.C, "ls1tum/artemis-c-minimal-docker:1.0.0");
        images.put(ProgrammingLanguage.GO, "ghcr.io/ls1intum/artemis-go-docker:v1.0.0");
        images.put(ProgrammingLanguage.RUST, "ghcr.io/ls1intum/artemis-rust-docker:v1.2.0");
        images.put(ProgrammingLanguage.C_PLUS_PLUS, "ghcr.io/ls1intum/artemis-cpp-docker:v1.1.2");
        images.put(ProgrammingLanguage.C_SHARP, "ghcr.io/ls1intum/artemis-csharp-docker:v1.0.1");
        images.put(ProgrammingLanguage.DART, "ghcr.io/ls1intum/artemis-dart-docker:v1.1.0");
        images.put(ProgrammingLanguage.SWIFT, "ls1tum/artemis-swift-swiftlint-docker:swift5.9.2");
        images.put(ProgrammingLanguage.HASKELL, "ghcr.io/uni-passau-artemis/artemis-haskell:v22.37.0");
        images.put(ProgrammingLanguage.JAVASCRIPT, "ghcr.io/ls1intum/artemis-javascript-docker:v1.1.0");
        images.put(ProgrammingLanguage.TYPESCRIPT, "ghcr.io/ls1intum/artemis-javascript-docker:v1.1.0");
        images.put(ProgrammingLanguage.RUBY, "ghcr.io/ls1intum/artemis-ruby-docker:v1.0.1");
        images.put(ProgrammingLanguage.R, "ghcr.io/ls1intum/artemis-r-docker:v1.2.0");
        return images;
    }

    /**
     * Points the given languages' {@code default} build image at their real production execution image, in place on the shared {@link ProgrammingLanguageConfiguration} bean. Only
     * the {@link ProjectType#PLAIN} (default) entry per language is overridden — {@code getImage} falls back to it for every project type — so this is byte-for-byte equivalent to
     * the previous {@code artemis.continuous-integration.build.images.<lang>.default} property overrides.
     *
     * @param config    the shared programming-language configuration bean to mutate
     * @param languages the languages whose default image to switch to the production image (pass none to override all known languages)
     */
    @SuppressWarnings("unchecked")
    static void useProductionBuildImages(ProgrammingLanguageConfiguration config, ProgrammingLanguage... languages) {
        Map<ProgrammingLanguage, Map<ProjectType, String>> images = (Map<ProgrammingLanguage, Map<ProjectType, String>>) ReflectionTestUtils.getField(config, "images");
        if (images == null) {
            throw new IllegalStateException("ProgrammingLanguageConfiguration has no images map to override");
        }
        Iterable<ProgrammingLanguage> targets = languages.length == 0 ? PRODUCTION_IMAGES.keySet() : java.util.Arrays.asList(languages);
        for (ProgrammingLanguage language : targets) {
            String image = PRODUCTION_IMAGES.get(language);
            if (image == null) {
                throw new IllegalArgumentException("No production image registered for " + language);
            }
            images.computeIfAbsent(language, key -> new EnumMap<>(ProjectType.class)).put(ProjectType.PLAIN, image);
        }
    }

    /**
     * Overrides the agent loop's context window to the GPU deployment's real size on the shared {@link AgentLoopRunner} bean, so the agent's window-aware compaction matches the
     * live model rather than the 128k default.
     *
     * @param agentLoopRunner the shared agent loop runner bean to mutate
     */
    static void useGpuContextWindow(AgentLoopRunner agentLoopRunner) {
        ReflectionTestUtils.setField(agentLoopRunner, "contextWindowTokens", GPU_CONTEXT_WINDOW_TOKENS);
    }
}
