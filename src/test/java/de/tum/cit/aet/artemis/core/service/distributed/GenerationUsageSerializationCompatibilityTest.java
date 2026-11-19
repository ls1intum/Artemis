package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationLiveUsageDTO;

/** Pins JDK map-value compatibility without treating an old currency amount as a price in another currency. */
class GenerationUsageSerializationCompatibilityTest {

    private static final String LIVE_USAGE_CLASS = ExerciseGenerationLiveUsageDTO.class.getName();

    private static final String JOB_USAGE_CLASS = "de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobReplayStore$JobUsage";

    @TempDir
    private static Path historicalClasses;

    private static URLClassLoader historicalLoader;

    @BeforeAll
    static void compileHistoricalRecords() throws Exception {
        // Release 10.7 record components and serial UIDs. Only annotations and unrelated methods are omitted.
        Path liveSource = historicalClasses.resolve("ExerciseGenerationLiveUsageDTO.java");
        FileUtils.writeStringToFile(liveSource.toFile(), """
                package de.tum.cit.aet.artemis.hyperion.dto;
                public record ExerciseGenerationLiveUsageDTO(long inputTokens, long outputTokens, long cachedInputTokens,
                        long billableTokens, long tokenBudget, long modelCalls, Double estimatedCostEur,
                        boolean estimatedCostComplete) implements java.io.Serializable {
                    private static final long serialVersionUID = 1L;
                }
                """, StandardCharsets.UTF_8);
        Path usageSource = historicalClasses.resolve("GenerationJobReplayStore$JobUsage.java");
        // A top-level name with '$' preserves the private nested record's serialized binary name without copying its service.
        FileUtils.writeStringToFile(usageSource.toFile(), """
                package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;
                import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
                public record GenerationJobReplayStore$JobUsage(long modelCalls, long toolCalls, long agentTurns,
                        long attempts, long inputTokens, long outputTokens, long cachedInputTokens,
                        boolean cachedInputTokensComplete, double estimatedCostEur, boolean estimatedCostEurComplete,
                        java.util.List<String> models, java.util.List<String> providerRequestIds,
                        boolean providerRequestIdsComplete, ExerciseGenerationAccountingState accountingState)
                        implements java.io.Serializable {
                    private static final long serialVersionUID = 2L;
                }
                """, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("the server tests run with a JDK").isNotNull();
        String mainClasses = Path.of(ExerciseGenerationAccountingState.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var diagnostics = new ByteArrayOutputStream();
        int result = compiler.run(null, diagnostics, diagnostics, "-proc:none", "-classpath", mainClasses, "-d", historicalClasses.toString(), liveSource.toString(),
                usageSource.toString());
        assertThat(result).as("historical record compilation: %s", diagnostics).isZero();
        Set<String> historicalNames = Set.of(LIVE_USAGE_CLASS, JOB_USAGE_CLASS);
        historicalLoader = new URLClassLoader(new URL[] { historicalClasses.toUri().toURL() }, GenerationUsageSerializationCompatibilityTest.class.getClassLoader()) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (!historicalNames.contains(name)) {
                        return super.loadClass(name, resolve);
                    }
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
    }

    @AfterAll
    static void closeHistoricalLoader() throws Exception {
        if (historicalLoader != null) {
            historicalLoader.close();
        }
    }

    @Test
    void oldLiveUsageRetainsTokenCountsAndDoesNotBecomePricedInUsd() throws Exception {
        Object historical = usage(historicalLoader.loadClass(LIVE_USAGE_CLASS), 0.42);
        Object current = read(write(historical), getClass().getClassLoader());

        assertThat(current).isInstanceOf(ExerciseGenerationLiveUsageDTO.class);
        assertTokenEvidence(current);
        assertThat(component(current, "costUsd")).isNull();
        assertThat(component(current, "costUsdComplete")).isEqualTo(false);
    }

    @Test
    void newLiveUsageRetainsTokenCountsAndDoesNotBecomePricedInEur() throws Exception {
        Object current = usage(ExerciseGenerationLiveUsageDTO.class, 0.84);
        Object historical = read(write(current), historicalLoader);

        assertThat(historical.getClass().getClassLoader()).isSameAs(historicalLoader);
        assertTokenEvidence(historical);
        assertThat(component(historical, "estimatedCostEur")).isNull();
        assertThat(component(historical, "estimatedCostComplete")).isEqualTo(false);
    }

    @Test
    void oldJobUsageRetainsAccountingEvidenceAndDoesNotBecomePricedInUsd() throws Exception {
        Object historical = usage(historicalLoader.loadClass(JOB_USAGE_CLASS), 0.42);
        Object current = read(write(historical), getClass().getClassLoader());

        assertThat(current.getClass()).isSameAs(Class.forName(JOB_USAGE_CLASS));
        assertJobEvidence(current);
        assertThat(component(current, "costUsd")).isEqualTo(0.0);
        assertThat(component(current, "costUsdComplete")).isEqualTo(false);
    }

    @Test
    void newJobUsageRetainsAccountingEvidenceAndDoesNotBecomePricedInEur() throws Exception {
        Object current = usage(Class.forName(JOB_USAGE_CLASS), 0.84);
        Object historical = read(write(current), historicalLoader);

        assertThat(historical.getClass().getClassLoader()).isSameAs(historicalLoader);
        assertJobEvidence(historical);
        assertThat(component(historical, "estimatedCostEur")).isEqualTo(0.0);
        assertThat(component(historical, "estimatedCostEurComplete")).isEqualTo(false);
    }

    private static Object usage(Class<?> type, double cost) throws Exception {
        boolean live = type.getName().equals(LIVE_USAGE_CLASS);
        assertThat(ObjectStreamClass.lookup(type).getSerialVersionUID()).isEqualTo(live ? 1L : 2L);
        Object[] values = live ? new Object[] { 1000L, 200L, 300L, 900L, 10000L, 4L, cost, true }
                : new Object[] { 4L, 6L, 7L, 2L, 1000L, 200L, 300L, true, cost, true, List.of("model"), List.of("request"), true, ExerciseGenerationAccountingState.COMPLETE };
        var constructor = type.getDeclaredConstructor(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new));
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }

    private static void assertTokenEvidence(Object value) throws Exception {
        assertThat(component(value, "inputTokens")).isEqualTo(1000L);
        assertThat(component(value, "outputTokens")).isEqualTo(200L);
        assertThat(component(value, "cachedInputTokens")).isEqualTo(300L);
        assertThat(component(value, "modelCalls")).isEqualTo(4L);
        if (value.getClass().getName().equals(LIVE_USAGE_CLASS)) {
            assertThat(component(value, "billableTokens")).isEqualTo(900L);
            assertThat(component(value, "tokenBudget")).isEqualTo(10000L);
        }
    }

    private static void assertJobEvidence(Object value) throws Exception {
        assertTokenEvidence(value);
        assertThat(component(value, "toolCalls")).isEqualTo(6L);
        assertThat(component(value, "agentTurns")).isEqualTo(7L);
        assertThat(component(value, "attempts")).isEqualTo(2L);
        assertThat(component(value, "cachedInputTokensComplete")).isEqualTo(true);
        assertThat(component(value, "models")).isEqualTo(List.of("model"));
        assertThat(component(value, "providerRequestIds")).isEqualTo(List.of("request"));
        assertThat(component(value, "providerRequestIdsComplete")).isEqualTo(true);
        assertThat(component(value, "accountingState")).isEqualTo(ExerciseGenerationAccountingState.COMPLETE);
    }

    private static Object component(Object value, String name) throws Exception {
        var accessor = value.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(value);
    }

    private static byte[] write(Object value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object read(byte[] bytes, ClassLoader loader) throws Exception {
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes)) {

            @Override
            protected Class<?> resolveClass(ObjectStreamClass descriptor) throws ClassNotFoundException {
                return Class.forName(descriptor.getName(), false, loader);
            }
        }) {
            return input.readObject();
        }
    }
}
