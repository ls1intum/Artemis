package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

class IrisLectureSearchIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "lecturesearchit";

    @BeforeEach
    void setupUsers() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        activateIrisGlobally();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnResults() throws Exception {
        var results = List.of(
                new PyrisLectureSearchResultDTO(1L, "Introduction Slide", "/link/1", 10L, "Intro to ML", 5L, "Machine Learning", 3, "supervised learning snippet", "http://base"),
                new PyrisLectureSearchResultDTO(2L, "Neural Networks", "/link/2", 10L, "Intro to ML", 5L, "Machine Learning", 7, "backpropagation snippet", "http://base"));
        irisRequestMockProvider.mockSearchLectures(results);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).lectureUnitId()).isEqualTo(1L);
        assertThat(response.get(0).snippet()).isEqualTo("supervised learning snippet");
        assertThat(response.get(1).lectureUnitId()).isEqualTo(2L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnEmptyList() throws Exception {
        irisRequestMockProvider.mockSearchLectures(List.of());

        var requestDTO = new PyrisLectureSearchRequestDTO("nonexistent topic", 5);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        irisRequestMockProvider.mockSearchLecturesError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void search_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.UNAUTHORIZED);
    }
}
