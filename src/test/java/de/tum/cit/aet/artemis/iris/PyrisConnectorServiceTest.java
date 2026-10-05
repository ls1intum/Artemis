package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.iris.exception.IrisForbiddenException;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitVisibilityWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;

class PyrisConnectorServiceTest extends AbstractIrisIntegrationTest {

    @Autowired
    private PyrisConnectorService pyrisConnectorService;

    private static Stream<Arguments> irisExceptions() {
        // @formatter:off
        return Stream.of(
                Arguments.of(400, IrisInternalPyrisErrorException.class),
                Arguments.of(401, IrisForbiddenException.class),
                Arguments.of(403, IrisForbiddenException.class),
                Arguments.of(404, IrisInternalPyrisErrorException.class), // TODO: Change with more specific exception
                Arguments.of(418, IrisInternalPyrisErrorException.class), // Test default case
                Arguments.of(500, IrisInternalPyrisErrorException.class)
        );
        // @formatter:on
    }

    @Test
    void visibilityWebhookTreatsOnlyPyrisOwnAnswerAsNotIngested() {
        var dto = new PyrisLectureUnitVisibilityWebhookDTO(1L, 2L, 3L, "https://artemis.example.org", null, List.of());

        // Both answers are registered before either request is made, because the mock server refuses further
        // expectations once it has served one. They are matched in the order they are declared.
        irisRequestMockProvider.mockLectureVisibilityWebhookError(404, "{\"detail\":\"Lecture unit has not been ingested\"}");
        irisRequestMockProvider.mockLectureVisibilityWebhookError(404, "{\"detail\":\"Not Found\"}");

        assertThat(pyrisConnectorService.executeLectureVisibilityWebhook(dto)).isFalse();

        // A renamed endpoint or a gateway in front of Pyris answers 404 as well. Reading that as "never ingested"
        // would settle every lecture unit of the installation at once, so it has to stay an error.
        assertThatThrownBy(() -> pyrisConnectorService.executeLectureVisibilityWebhook(dto)).isInstanceOf(IrisInternalPyrisErrorException.class);
    }

    @ParameterizedTest
    @MethodSource("irisExceptions")
    void testExceptionV2(int httpStatus, Class<?> exceptionClass) {
        irisRequestMockProvider.mockRunError(httpStatus);

        assertThatThrownBy(() -> pyrisConnectorService.executePipeline("programming-exercise-chat", null, Optional.empty())).isInstanceOf(exceptionClass);
    }

    @ParameterizedTest
    @MethodSource("irisExceptions")
    void testExceptionIngestionV2(int httpStatus, Class<?> exceptionClass) {
        irisRequestMockProvider.mockIngestionWebhookRunError(httpStatus);
        PyrisLectureUnitWebhookDTO pyrisLectureUnitWebhookDTO = new PyrisLectureUnitWebhookDTO("example.pdf", 1, null, 123L, "Lecture Unit Name", 456L, "Lecture Name", 789L,
                "Course Name", "Course Description", "/example/test.pdf", "", null);
        PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(pyrisLectureUnitWebhookDTO, 123L, null);
        assertThatThrownBy(() -> pyrisConnectorService.executeLectureAdditionWebhook(executionDTO)).isInstanceOf(exceptionClass);
    }

    @ParameterizedTest
    @MethodSource("irisExceptions")
    void testExceptionLectureDeletionV2(int httpStatus, Class<?> exceptionClass) {
        irisRequestMockProvider.mockDeletionWebhookRunError(httpStatus);
        assertThatThrownBy(() -> pyrisConnectorService.executeLectureDeletionWebhook(null)).isInstanceOf(exceptionClass);
    }

}
