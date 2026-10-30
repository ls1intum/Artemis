package de.tum.cit.aet.artemis.videosource.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;
import de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException;
import de.tum.cit.aet.artemis.videosource.service.GocastRecordingService;

class GocastBindingApiTest {

    private final GocastBindingService bindingService = mock(GocastBindingService.class);

    private final GocastBindingApi api = new GocastBindingApi(bindingService, mock(GocastRecordingService.class));

    @Test
    void disconnectsTheCourseBeforeItIsDeleted() {
        api.disconnectBeforeCourseDeletion(42);

        verify(bindingService).unlink(42);
    }

    @Test
    void unavailableTumLiveDoesNotStopTheDeletion() {
        doThrow(new GocastIntegrationException("unavailable", HttpStatus.SERVICE_UNAVAILABLE)).when(bindingService).unlink(42);

        assertThatCode(() -> api.disconnectBeforeCourseDeletion(42)).doesNotThrowAnyException();
        verify(bindingService).unlink(42);
    }
}
