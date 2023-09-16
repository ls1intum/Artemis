package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorException;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.exception.*;

class IrisConnectorServiceTest extends AbstractIrisIntegrationTest {

    @Autowired
    private IrisConnectorService irisConnectorService;

    private static Stream<Arguments> irisExceptions() {
        return Stream.of(Arguments.of(400, IrisInvalidTemplateException.class), Arguments.of(401, IrisForbiddenException.class), Arguments.of(403, IrisForbiddenException.class),
                Arguments.of(404, IrisModelNotAvailableException.class), Arguments.of(500, IrisInternalPyrisErrorException.class),
                // Test default case
                Arguments.of(418, IrisInternalPyrisErrorException.class));
    }

    @ParameterizedTest
    @MethodSource("irisExceptions")
    void testException(int httpStatus, Class<?> exceptionClass) throws Exception {
        var template = new IrisTemplate("Dummy");

        irisRequestMockProvider.mockMessageError(httpStatus);

        irisConnectorService.sendRequest(template, "TEST_MODEL", Collections.emptyMap()).handle((response, throwable) -> {
            assertThat(throwable.getCause()).isNotNull().isInstanceOf(exceptionClass);
            return null;
        }).get();
    }

    @Test
    void testParseException() throws Exception {
        var template = new IrisTemplate("Dummy");

        irisRequestMockProvider.mockCustomJsonResponse("{\"message\": \"invalid\"}");

        irisConnectorService.sendRequest(template, "TEST_MODEL", Collections.emptyMap()).handle((response, throwable) -> {
            assertThat(throwable.getCause()).isNotNull().isInstanceOf(IrisParseResponseException.class);
            return null;
        }).get();
    }

    @Test
    void testOfferedModels() throws Exception {
        irisRequestMockProvider.mockModelsResponse();

        var offeredModels = irisConnectorService.getOfferedModels();
        assertThat(offeredModels).hasSize(1);
        assertThat(offeredModels.get(0).id()).isEqualTo("TEST_MODEL");
    }

    @Test
    void testOfferedModelsError() throws Exception {
        irisRequestMockProvider.mockModelsError();

        assertThatThrownBy(() -> irisConnectorService.getOfferedModels()).isInstanceOf(IrisConnectorException.class);
    }
}
