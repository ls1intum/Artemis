package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

@ExtendWith(MockitoExtension.class)
class SliceUtilTest {

    @Mock
    private Slice<Object> slice;

    private UriComponentsBuilder uriBuilder;

    @BeforeEach
    void initMocks() {
        uriBuilder = UriComponentsBuilder.fromUriString("http://localhost:8080/api/items");
    }

    @Test
    void generateSliceHttpHeaders_hasNextTrue_shouldAddLinkHeaderWithNext() {
        when(slice.hasNext()).thenReturn(true);
        when(slice.getNumber()).thenReturn(0);
        when(slice.getSize()).thenReturn(10);

        HttpHeaders headers = SliceUtil.generateSliceHttpHeaders(uriBuilder, slice);

        assertThat(headers.get("X-Has-Next")).containsExactly("true");
        assertThat(headers.get(HttpHeaders.LINK)).hasSize(1);
        assertThat(headers.get(HttpHeaders.LINK).getFirst()).contains("rel=\"next\"");
    }

    @Test
    void generateSliceHttpHeaders_hasNextFalse_shouldNotAddLinkHeaderWithNext() {
        when(slice.hasNext()).thenReturn(false);
        when(slice.getNumber()).thenReturn(1);
        when(slice.getSize()).thenReturn(10);

        HttpHeaders headers = SliceUtil.generateSliceHttpHeaders(uriBuilder, slice);

        assertThat(headers.get("X-Has-Next")).containsExactly("false");
        assertThat(headers.get(HttpHeaders.LINK)).hasSize(1);
        assertThat(headers.get(HttpHeaders.LINK).getFirst()).contains("rel=\"prev\"");
    }

    @Test
    void generateSliceHttpHeaders_isFirstPage_shouldNotAddLinkHeaderWithPrev() {
        when(slice.hasNext()).thenReturn(false);
        when(slice.getNumber()).thenReturn(0);
        when(slice.getSize()).thenReturn(10);

        HttpHeaders headers = SliceUtil.generateSliceHttpHeaders(uriBuilder, slice);

        assertThat(headers.get("X-Has-Next")).containsExactly("false");
        assertThat(headers.get(HttpHeaders.LINK)).containsExactly("");
    }

    @Test
    void generateSliceHttpHeaders_notFirstPage_shouldAddLinkHeaderWithPrev() {
        when(slice.hasNext()).thenReturn(false);
        when(slice.getNumber()).thenReturn(1);
        when(slice.getSize()).thenReturn(10);

        HttpHeaders headers = SliceUtil.generateSliceHttpHeaders(uriBuilder, slice);

        assertThat(headers.get("X-Has-Next")).containsExactly("false");
        assertThat(headers.get(HttpHeaders.LINK)).hasSize(1);
        assertThat(headers.get(HttpHeaders.LINK).getFirst()).contains("rel=\"prev\"");
    }

    @Test
    void generateSliceHttpHeaders_hasNextTrueAndNotFirstPage_shouldAddBothLinkHeaders() {
        when(slice.hasNext()).thenReturn(true);
        when(slice.getNumber()).thenReturn(1);
        when(slice.getSize()).thenReturn(10);

        HttpHeaders headers = SliceUtil.generateSliceHttpHeaders(uriBuilder, slice);

        assertThat(headers.get("X-Has-Next")).containsExactly("true");
        assertThat(headers.get(HttpHeaders.LINK)).hasSize(1);
        List<String> links = headers.get(HttpHeaders.LINK);
        assertThat(links.getFirst()).contains("rel=\"next\"");
        assertThat(links.getFirst()).contains("rel=\"prev\"");
    }

    @Test
    void generateSliceHttpHeaders_uriBuilderWithExistingParams_shouldPreserveExistingParams() {
        uriBuilder = UriComponentsBuilder.fromUriString("http://localhost:8080/api/items?sort=name,asc");
        when(slice.hasNext()).thenReturn(true);
        when(slice.getNumber()).thenReturn(0);
        when(slice.getSize()).thenReturn(10);

        HttpHeaders headers = SliceUtil.generateSliceHttpHeaders(uriBuilder, slice);

        assertThat(headers.get(HttpHeaders.LINK)).hasSize(1);
        assertThat(headers.get(HttpHeaders.LINK).getFirst()).contains("sort=name%2Casc");
    }

    @Test
    void generateSliceHttpHeaders_specialCharactersInUri_shouldEncodeSpecialCharacters() {
        uriBuilder = UriComponentsBuilder.fromUriString("http://localhost:8080/api/items?filter=name,test;value");
        when(slice.hasNext()).thenReturn(true);
        when(slice.getNumber()).thenReturn(0);
        when(slice.getSize()).thenReturn(10);

        HttpHeaders headers = SliceUtil.generateSliceHttpHeaders(uriBuilder, slice);

        assertThat(headers.get(HttpHeaders.LINK)).hasSize(1);
        assertThat(headers.get(HttpHeaders.LINK).getFirst()).contains("filter=name%2Ctest%3Bvalue");
    }
}
