package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;

class PyrisConnectorServiceFaqStateTest extends AbstractIrisIntegrationTest {

    private static final long COURSE_ID = 101L;

    private static final long FAQ_ID = 5L;

    @Autowired
    private PyrisConnectorService pyrisConnectorService;

    @Test
    void returnsFaqStateFromPyris() throws Exception {
        irisRequestMockProvider.mockFaqIngestionState(COURSE_ID, FAQ_ID, IngestionState.PARTIALLY_INGESTED);

        var state = pyrisConnectorService.getFaqIngestionState(COURSE_ID, FAQ_ID);

        assertThat(state).isEqualTo(IngestionState.PARTIALLY_INGESTED);
    }

    @Test
    void throwsOnFaqStateError() {
        irisRequestMockProvider.mockFaqIngestionStateError(COURSE_ID, FAQ_ID, HttpStatus.BAD_GATEWAY);

        assertThatThrownBy(() -> pyrisConnectorService.getFaqIngestionState(COURSE_ID, FAQ_ID)).isInstanceOf(PyrisConnectorException.class);
    }
}
