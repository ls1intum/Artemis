package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class GenerationLiveSaveResultSynchronizationTest {

    private static final long EXERCISE_ID = 42L;

    private static final String SOLUTION = "solution-head";

    private static final String TESTS = "tests-head";

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void onlyTheExactExpectedPairCanCompleteTheSaveSynchronization(boolean separateCoordinator) {
        var provider = new LocalDataProviderService();
        var writer = new GenerationLiveSaveService(provider, true);
        var resultProcessor = separateCoordinator ? new GenerationLiveSaveService(provider, true) : writer;
        String token = writer.claim(EXERCISE_ID);
        try {
            var calls = new AtomicInteger();
            assertThat(resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                calls.incrementAndGet();
                return true;
            })).isFalse();
            writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);
            assertThat(resultProcessor.synchronizeTestCases(EXERCISE_ID, "old-solution", TESTS, true, () -> {
                calls.incrementAndGet();
                return true;
            })).isFalse();
            assertThat(resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, "old-tests", true, () -> {
                calls.incrementAndGet();
                return true;
            })).isFalse();
            assertThat(calls).hasValue(0);
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isFalse();

            assertThat(resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                calls.incrementAndGet();
                assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isFalse();
                return true;
            })).isTrue();
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isTrue();
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, "old-solution", TESTS)).isFalse();
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, "old-tests")).isFalse();
            assertThat(resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                calls.incrementAndGet();
                return true;
            })).isFalse();
            assertThat(calls).hasValue(1);
        }
        finally {
            writer.release(EXERCISE_ID, token);
        }
        assertThat(writer.getSlotInfo(EXERCISE_ID)).isEmpty();
    }

    @Test
    void publishingAnExpectedPairRequiresTheCurrentWriter() {
        var provider = new LocalDataProviderService();
        var writer = new GenerationLiveSaveService(provider, true);
        var other = new GenerationLiveSaveService(provider, true);
        assertThatThrownBy(() -> writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS)).isInstanceOf(IllegalStateException.class);
        String token = writer.claim(EXERCISE_ID);
        try {
            assertThatThrownBy(() -> other.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS)).isInstanceOf(IllegalStateException.class);
            writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);
        }
        finally {
            writer.release(EXERCISE_ID, token);
        }
    }

    @Test
    void failedOrRejectedDiscoveryDoesNotMarkCompletionAndCanBeRetried() {
        var provider = new LocalDataProviderService();
        var writer = new GenerationLiveSaveService(provider, true);
        var resultProcessor = new GenerationLiveSaveService(provider, true);
        String token = writer.claim(EXERCISE_ID);
        try {
            writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);
            assertThat(resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> false)).isFalse();
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isFalse();
            assertThatThrownBy(() -> resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                throw new IllegalArgumentException("discovery failed");
            })).isInstanceOf(IllegalArgumentException.class).hasMessage("discovery failed");
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isFalse();
            writer.verify(EXERCISE_ID, token);
            assertThat(resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> true)).isTrue();
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isTrue();
        }
        finally {
            writer.release(EXERCISE_ID, token);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void lostReservationDoesNotHideDiscoveryFailure(boolean discoveryFails) {
        var provider = new LocalDataProviderService();
        var writer = new GenerationLiveSaveService(provider, true);
        var resultProcessor = new GenerationLiveSaveService(provider, true);
        String token = writer.claim(EXERCISE_ID);
        writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);
        var discoveryFailure = new IllegalArgumentException("discovery failed");
        try {
            var failure = catchThrowable(() -> resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                assertThat(writer.recoverSlot(EXERCISE_ID, token)).isTrue();
                if (discoveryFails) {
                    throw discoveryFailure;
                }
                return true;
            }));
            if (discoveryFails) {
                assertThat(failure).isSameAs(discoveryFailure);
                assertThat(failure.getSuppressed()).singleElement()
                        .satisfies(cleanup -> assertThat(cleanup).isInstanceOf(IllegalStateException.class).hasMessage("Test synchronization lost its save reservation"));
            }
            else {
                assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage("Test synchronization lost its save reservation");
            }
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isFalse();
        }
        finally {
            writer.retain(EXERCISE_ID, token);
        }
    }

    @Test
    void lostReservationAfterSuccessfulDiscoveryIsReported() {
        var writer = new GenerationLiveSaveService(new LocalDataProviderService(), true);
        String token = writer.claim(EXERCISE_ID);
        writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);

        assertThatThrownBy(() -> writer.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
            assertThat(writer.recoverSlot(EXERCISE_ID, token)).isTrue();
            return true;
        })).isInstanceOf(IllegalStateException.class).hasMessage("Test synchronization lost its save reservation");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void discoveryFailureSurvivesALostReservation(boolean fatalFailure) {
        var writer = new GenerationLiveSaveService(new LocalDataProviderService(), true);
        String token = writer.claim(EXERCISE_ID);
        writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);
        Throwable failure = fatalFailure ? new AssertionError("discovery failed") : new IllegalArgumentException("discovery failed");

        assertThatThrownBy(() -> writer.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
            assertThat(writer.recoverSlot(EXERCISE_ID, token)).isTrue();
            if (failure instanceof Error error) {
                throw error;
            }
            throw (RuntimeException) failure;
        })).isSameAs(failure).satisfies(thrown -> assertThat(thrown.getSuppressed()).singleElement()
                .satisfies(suppressed -> assertThat(suppressed).isInstanceOf(IllegalStateException.class).hasMessage("Test synchronization lost its save reservation")));
    }

    @Test
    void overlappingExpectedResultsRunOnlyOneCallbackAndPreventTheWriterFromMutating() throws Exception {
        var provider = new LocalDataProviderService();
        var writer = new GenerationLiveSaveService(provider, true);
        var resultProcessor = new GenerationLiveSaveService(provider, true);
        String token = writer.claim(EXERCISE_ID);
        writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var duplicateCalls = new AtomicInteger();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                entered.countDown();
                await(finish);
                return true;
            }));
            try {
                await(entered);
                assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isFalse();
                assertThatThrownBy(() -> writer.verify(EXERCISE_ID, token)).isInstanceOf(ConflictException.class);
                assertThatThrownBy(() -> writer.expectTestCaseSynchronization(EXERCISE_ID, "replacement-solution", "replacement-tests")).isInstanceOf(IllegalStateException.class);
                assertThat(resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                    duplicateCalls.incrementAndGet();
                    return true;
                })).isFalse();
                assertThat(duplicateCalls).hasValue(0);
            }
            finally {
                finish.countDown();
            }
            assertThat(result.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(writer.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isTrue();
            writer.verify(EXERCISE_ID, token);
        }
        finally {
            finish.countDown();
            writer.release(EXERCISE_ID, token);
        }
    }

    @Test
    void closingWhileDiscoveryIsRunningRetainsTheExactSlotEvenAfterTheLateCallbackFinishes() throws Exception {
        var provider = new LocalDataProviderService();
        var writer = new GenerationLiveSaveService(provider, true);
        var resultProcessor = new GenerationLiveSaveService(provider, true);
        String token = writer.claim(EXERCISE_ID);
        writer.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                entered.countDown();
                await(finish);
                return true;
            }));
            try {
                await(entered);
                assertThatThrownBy(() -> writer.release(EXERCISE_ID, token)).isInstanceOf(IllegalStateException.class).hasMessageContaining("retained");
                assertThat(writer.getSlotInfo(EXERCISE_ID).orElseThrow().token()).isEqualTo(token);
                assertThatThrownBy(() -> writer.claim(EXERCISE_ID)).isInstanceOf(ConflictException.class);
                assertThatThrownBy(() -> resultProcessor.claim(EXERCISE_ID)).isInstanceOf(ConflictException.class);
            }
            finally {
                finish.countDown();
            }
            assertThat(result.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(writer.getSlotInfo(EXERCISE_ID).orElseThrow().token()).isEqualTo(token);
            assertThatThrownBy(() -> writer.claim(EXERCISE_ID)).isInstanceOf(ConflictException.class);
            assertThat(resultProcessor.recoverSlot(EXERCISE_ID, "stale-token")).isFalse();
            assertThat(resultProcessor.recoverSlot(EXERCISE_ID, token)).isTrue();
            String next = writer.claim(EXERCISE_ID);
            writer.release(EXERCISE_ID, next);
            assertThat(writer.getSlotInfo(EXERCISE_ID)).isEmpty();
        }
        finally {
            finish.countDown();
        }
    }

    @Test
    void ordinaryDiscoveryUsesTheExistingReaderToExcludeAnOverlappingSave() throws Exception {
        var provider = new LocalDataProviderService();
        var writer = new GenerationLiveSaveService(provider, true);
        var resultProcessor = new GenerationLiveSaveService(provider, true);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> resultProcessor.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
                entered.countDown();
                await(finish);
                return true;
            }));
            try {
                await(entered);
                assertThat(writer.getSlotInfo(EXERCISE_ID).orElseThrow().readers()).hasSize(1);
                assertThatThrownBy(() -> writer.claim(EXERCISE_ID)).isInstanceOf(ConflictException.class);
            }
            finally {
                finish.countDown();
            }
            assertThat(result.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(writer.getSlotInfo(EXERCISE_ID)).isEmpty();
            String next = writer.claim(EXERCISE_ID);
            writer.release(EXERCISE_ID, next);
        }
    }

    @Test
    void disabledCoordinationRunsTheCallbackWithoutTouchingDistributedState() {
        var provider = mock(DistributedDataProvider.class);
        DistributedMap<String, GenerationLiveSaveService.LiveSaveSlot> map = mock();
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(map);
        var service = new GenerationLiveSaveService(provider, false);
        var calls = new AtomicInteger();
        service.expectTestCaseSynchronization(EXERCISE_ID, SOLUTION, TESTS);
        assertThat(service.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> {
            calls.incrementAndGet();
            return true;
        })).isTrue();
        assertThat(service.synchronizeTestCases(EXERCISE_ID, SOLUTION, TESTS, true, () -> false)).isFalse();
        assertThat(service.testCaseSynchronizationCompleted(EXERCISE_ID, SOLUTION, TESTS)).isTrue();
        assertThat(calls).hasValue(1);
        verifyNoInteractions(map);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("callback synchronization deadline").isTrue();
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while establishing callback ordering", interrupted);
        }
    }
}
