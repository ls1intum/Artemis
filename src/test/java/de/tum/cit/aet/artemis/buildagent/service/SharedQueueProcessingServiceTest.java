package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentCapacityAdjustmentDTO;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;

class SharedQueueProcessingServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @BeforeEach
    void setUp() {
        // Remove listener to control test execution
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();

        when(buildAgentConfiguration.adjustConcurrentBuildSize(anyInt())).thenReturn(true);

        ThreadPoolExecutor mockExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
        when(buildAgentConfiguration.getBuildExecutor()).thenReturn(mockExecutor);
    }

    @AfterEach
    void tearDown() {
        // Reinitialize the service for other tests
        sharedQueueProcessingService.init();
    }

    @Test
    void testTriggerBuildJobProcessing_notPaused() {
        // Ensure the agent is not paused
        ReflectionTestUtils.setField(sharedQueueProcessingService, "isPaused", new java.util.concurrent.atomic.AtomicBoolean(false));

        assertThatCode(() -> sharedQueueProcessingService.triggerBuildJobProcessing()).doesNotThrowAnyException();
    }

    @Test
    void testTriggerBuildJobProcessing_paused() {
        // Set the agent as paused
        ReflectionTestUtils.setField(sharedQueueProcessingService, "isPaused", new java.util.concurrent.atomic.AtomicBoolean(true));

        assertThatCode(() -> sharedQueueProcessingService.triggerBuildJobProcessing()).doesNotThrowAnyException();
    }

    @Test
    void testRemoveListenerAndCancelScheduledFuture() {
        sharedQueueProcessingService.init();

        assertThatCode(() -> sharedQueueProcessingService.removeListenerAndCancelScheduledFuture()).doesNotThrowAnyException();
    }

    @Test
    void testCapacityAdjustmentListener_validAdjustment() {
        sharedQueueProcessingService.init();

        BuildAgentCapacityAdjustmentDTO adjustment = new BuildAgentCapacityAdjustmentDTO("test-agent", 5);

        // Mock successful adjustment
        when(buildAgentConfiguration.adjustConcurrentBuildSize(5)).thenReturn(true);

        // Publish the message to trigger the listener - verify this doesn't throw
        assertThatCode(() -> {
            var topic = hazelcastInstance.getTopic("adjustBuildAgentCapacityTopic");
            topic.publish(adjustment);
        }).doesNotThrowAnyException();
    }

    @Test
    void testCapacityAdjustmentListener_failedAdjustment() {
        // Initialize the service to set up listeners
        sharedQueueProcessingService.init();

        BuildAgentCapacityAdjustmentDTO adjustment = new BuildAgentCapacityAdjustmentDTO("test-agent", 3);

        // Mock failed adjustment
        when(buildAgentConfiguration.adjustConcurrentBuildSize(3)).thenReturn(false);

        // Publish the message to trigger the listener - verify this doesn't throw even when adjustment fails
        assertThatCode(() -> {
            var topic = hazelcastInstance.getTopic("adjustBuildAgentCapacityTopic");
            topic.publish(adjustment);
        }).doesNotThrowAnyException();
    }
}
