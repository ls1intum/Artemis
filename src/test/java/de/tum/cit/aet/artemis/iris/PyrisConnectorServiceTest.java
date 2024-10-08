package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.iris.exception.IrisForbiddenException;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
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

    @ParameterizedTest
    @MethodSource("irisExceptions")
    void testExceptionV2(int httpStatus, Class<?> exceptionClass) {
        irisRequestMockProvider.mockRunError(httpStatus);

        assertThatThrownBy(() -> pyrisConnectorService.executePipeline("tutor-chat", "default", null)).isInstanceOf(exceptionClass);
    }

    @ParameterizedTest
    @MethodSource("irisExceptions")
    void testExceptionIngestionV2(int httpStatus, Class<?> exceptionClass) {
        irisRequestMockProvider.mockIngestionWebhookRunError(httpStatus);
        PyrisLectureUnitWebhookDTO pyrisLectureUnitWebhookDTO = new PyrisLectureUnitWebhookDTO("example.pdf", 123L, "Lecture Unit Name", 456L, "Lecture Name", 789L, "Course Name",
                "Course Description");
        PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(pyrisLectureUnitWebhookDTO, null, List.of());
        assertThatThrownBy(() -> pyrisConnectorService.executeLectureAddtionWebhook("fullIngestion", executionDTO)).isInstanceOf(exceptionClass);
    }

    @ParameterizedTest
    @MethodSource("irisExceptions")
    void testExceptionLectureDeletionV2(int httpStatus, Class<?> exceptionClass) {
        irisRequestMockProvider.mockDeletionWebhookRunError(httpStatus);
        assertThatThrownBy(() -> pyrisConnectorService.executeLectureDeletionWebhook(null)).isInstanceOf(exceptionClass);
    }

    @Test
    void testOfferedModels() throws Exception {
        irisRequestMockProvider.mockModelsResponse();

        var offeredModels = pyrisConnectorService.getOfferedModels();
        assertThat(offeredModels).hasSize(1);
        assertThat(offeredModels.getFirst().id()).isEqualTo("TEST_MODEL");
    }

    @Test
    void testOfferedModelsError() {
        irisRequestMockProvider.mockModelsError();

        assertThatThrownBy(() -> pyrisConnectorService.getOfferedModels()).isInstanceOf(PyrisConnectorException.class);
    }

}
