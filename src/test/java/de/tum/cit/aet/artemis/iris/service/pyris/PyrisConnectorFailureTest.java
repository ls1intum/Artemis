package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDataDTO;
import de.tum.cit.aet.artemis.iris.exception.IrisForbiddenException;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris.PyrisMemoryWithRelationsDTO;
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
    void unusableListAndSearchResponsesProduceEmptyCollections(int status) {
        when(rest.getForEntity(anyString(), eq(MemirisMemoryDataDTO.class))).thenReturn(ResponseEntity.status(status).build());
        when(rest.postForEntity(anyString(), any(), eq(PyrisLectureSearchResultDTO[].class))).thenReturn(ResponseEntity.status(status).build());
        var memories = service.listMemirisMemoryData(7L);
        assertThat(memories).isEqualTo(new MemirisMemoryDataDTO(List.of(), List.of(), List.of()));
        assertThat(service.searchLectures("Java", 5, null, null, null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 204, 302 })
    void unusableSingleMemoryResponseFailsExplicitly(int status) {
        when(rest.getForEntity(anyString(), eq(PyrisMemoryWithRelationsDTO.class))).thenReturn(ResponseEntity.status(status).build());
        assertThatThrownBy(() -> service.getMemirisMemoryWithRelations(7L, "memory")).isInstanceOf(PyrisConnectorException.class).hasMessage("Could not fetch memory from Pyris");
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 404, 500 })
    void memoryListMapsHttpErrorsToDomainExceptions(int status) {
        var error = HttpClientErrorException.create(HttpStatus.valueOf(status), "error", HttpHeaders.EMPTY, "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(rest.getForEntity(anyString(), eq(MemirisMemoryDataDTO.class))).thenThrow(error);
        Class<? extends RuntimeException> expected = status == 404 ? EntityNotFoundException.class
                : status == 401 ? IrisForbiddenException.class : IrisInternalPyrisErrorException.class;
        assertThatThrownBy(() -> service.listMemirisMemoryData(7L)).isInstanceOf(expected);
    }

    @Test
    void memoryAndSearchTransportFailuresHaveOperationSpecificMessages() {
        when(rest.getForEntity(anyString(), eq(MemirisMemoryDataDTO.class))).thenThrow(new ResourceAccessException("timeout"));
        when(rest.getForEntity(anyString(), eq(PyrisMemoryWithRelationsDTO.class))).thenThrow(new ResourceAccessException("timeout"));
        when(rest.postForEntity(anyString(), any(), eq(PyrisLectureSearchResultDTO[].class))).thenThrow(new ResourceAccessException("timeout"));
        org.mockito.Mockito.doThrow(new ResourceAccessException("timeout")).when(rest).delete(anyString());
        assertThatThrownBy(() -> service.listMemirisMemoryData(7L)).isInstanceOf(PyrisConnectorException.class).hasMessage("Could not fetch memories from Pyris");
        assertThatThrownBy(() -> service.getMemirisMemoryWithRelations(7L, "memory")).isInstanceOf(PyrisConnectorException.class).hasMessage("Could not fetch memory from Pyris");
        assertThatThrownBy(() -> service.deleteMemirisMemory(7L, "memory")).isInstanceOf(PyrisConnectorException.class).hasMessage("Could not delete memory in Pyris");
        assertThatThrownBy(() -> service.searchLectures("Java", 5, null, null, null)).isInstanceOf(PyrisConnectorException.class)
                .hasMessage("Could not fetch lecture search results from Pyris");
    }

    @ParameterizedTest
    @ValueSource(ints = { 403, 404 })
    void deleteMemoryMapsHttpErrorsWithoutLeakingTransportException(int status) {
        org.mockito.Mockito.doThrow(new HttpClientErrorException(HttpStatus.valueOf(status))).when(rest).delete(anyString());
        assertThatThrownBy(() -> service.deleteMemirisMemory(7L, "memory")).isInstanceOf(status == 404 ? EntityNotFoundException.class : IrisForbiddenException.class);
    }
}
