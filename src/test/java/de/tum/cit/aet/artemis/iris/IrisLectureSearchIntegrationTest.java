package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchAskResponseDTO;

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
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(1L, "Introduction Slide", "/link/1", 3), "supervised learning snippet"),
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(2L, "Neural Networks", "/link/2", 7), "backpropagation snippet"));
        irisRequestMockProvider.mockSearchLectures(results);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).lectureUnit().id()).isEqualTo(1L);
        assertThat(response.get(0).snippet()).isEqualTo("supervised learning snippet");
        assertThat(response.get(1).lectureUnit().id()).isEqualTo(2L);
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_shouldReturnAnswerWithSources() throws Exception {
        var source = new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(1L, "Machine Learning"),
                new PyrisLectureSearchResultDTO.LectureDTO(2L, "Intro to ML"), new PyrisLectureSearchResultDTO.LectureUnitDTO(3L, "Neural Networks", "/link/3", 5),
                "backpropagation snippet");
        var mockResponse = new PyrisSearchAskResponseDTO("Neural networks learn via backpropagation.", List.of(source));
        irisRequestMockProvider.mockSearchAsk(mockResponse);

        var requestDTO = new PyrisSearchAskRequestDTO("What is backpropagation?", 5);
        var response = request.postWithResponseBody("/api/iris/search-answer", requestDTO, PyrisSearchAskResponseDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.answer()).isEqualTo("Neural networks learn via backpropagation.");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().lectureUnit().id()).isEqualTo(3L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_shouldReturnAnswerWithNoSources() throws Exception {
        var mockResponse = new PyrisSearchAskResponseDTO("No relevant sources found.", List.of());
        irisRequestMockProvider.mockSearchAsk(mockResponse);

        var requestDTO = new PyrisSearchAskRequestDTO("obscure question", 5);
        var response = request.postWithResponseBody("/api/iris/search-answer", requestDTO, PyrisSearchAskResponseDTO.class, HttpStatus.OK);

        assertThat(response).isNotNull();
        assertThat(response.answer()).isEqualTo("No relevant sources found.");
        assertThat(response.sources()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        irisRequestMockProvider.mockSearchAskError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new PyrisSearchAskRequestDTO("machine learning", 5);
        request.postWithResponseBody("/api/iris/search-answer", requestDTO, PyrisSearchAskResponseDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void ask_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new PyrisSearchAskRequestDTO("machine learning", 5);
        request.postWithResponseBody("/api/iris/search-answer", requestDTO, PyrisSearchAskResponseDTO.class, HttpStatus.UNAUTHORIZED);
    }
}
