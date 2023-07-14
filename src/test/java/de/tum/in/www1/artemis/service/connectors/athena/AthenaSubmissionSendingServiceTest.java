package de.tum.in.www1.artemis.service.connectors.athena;

import static org.mockito.Mockito.*;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

@ExtendWith(MockitoExtension.class)
class AthenaFeedbackSendingServiceTest {

    @Mock
    private AthenaConnector<AthenaFeedbackSendingService.RequestDTO, AthenaFeedbackSendingService.ResponseDTO> athenaConnector;

    @Mock
    private TextBlockRepository textBlockRepository;

    @InjectMocks
    private AthenaFeedbackSendingService athenaFeedbackSendingService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private Feedback feedback;

    @BeforeEach
    void setUp() {
        textExercise = new TextExercise();
        textExercise.setId(1L);
        textExercise.setTitle("Test Exercise");

        textSubmission = new TextSubmission();
        textSubmission.setId(2L);

        feedback = new Feedback();
        feedback.setId(3L);
        feedback.setText("Feedback");
    }

    @Test
    void sendFeedback_Success() throws NetworkingError {
        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, Collections.singletonList(feedback));
        verify(athenaConnector, times(1)).invokeWithRetry(anyString(), any(), anyInt());
    }

    @Test
    void sendFeedback_WithMaxRetries_Success() throws NetworkingError {
        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, Collections.singletonList(feedback), 2);
        verify(athenaConnector, times(1)).invokeWithRetry(anyString(), any(), eq(2));
    }

    @Test
    void sendFeedback_WhenNetworkingErrorOccurs() throws NetworkingError {
        doThrow(NetworkingError.class).when(athenaConnector).invokeWithRetry(anyString(), any(), anyInt());

        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, Collections.singletonList(feedback));

        verify(athenaConnector, times(1)).invokeWithRetry(anyString(), any(), anyInt());
    }

    @Test
    void sendFeedback_WithMaxRetries_WhenNetworkingErrorOccurs() throws NetworkingError {
        doThrow(NetworkingError.class).when(athenaConnector).invokeWithRetry(anyString(), any(), anyInt());

        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, Collections.singletonList(feedback), 2);

        verify(athenaConnector, times(2)).invokeWithRetry(anyString(), any(), eq(2));
    }
}
