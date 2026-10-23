package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

class PyrisConnectorFailureTest {

    private RestTemplate rest;

    private PyrisConnectorService service;

    @BeforeEach
    void setUp() {
        rest = mock(RestTemplate.class);
        service = new PyrisConnectorService(rest, mock(RestTemplate.class), JsonObjectMapper.get());
        ReflectionTestUtils.setField(service, "pyrisUrl", "http://pyris.test");
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "http://artemis.test");
    }

    @ParameterizedTest
    @ValueSource(ints = { 204, 302 })
    void unusableSearchResponsesProduceEmptyCollections(int status) {
        when(rest.postForEntity(anyString(), any(), eq(PyrisLectureSearchResultDTO[].class))).thenReturn(ResponseEntity.status(status).build());
        assertThat(service.searchLectures("Java", 5, null, null, null)).isEmpty();
    }

    @Test
    void searchTransportFailuresHaveOperationSpecificMessages() {
        when(rest.postForEntity(anyString(), any(), eq(PyrisLectureSearchResultDTO[].class))).thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> service.searchLectures("Java", 5, null, null, null)).isInstanceOf(PyrisConnectorException.class)
                .hasMessage("Could not fetch lecture search results from Pyris");
    }
}
