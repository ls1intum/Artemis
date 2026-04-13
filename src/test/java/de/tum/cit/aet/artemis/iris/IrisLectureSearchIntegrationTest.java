package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.iris.dto.IrisSearchAnswerCallbackDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.IrisSearchAskClientRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

class IrisLectureSearchIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "lecturesearchit";

    @BeforeEach
    void setupUsers() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        activateIrisGlobally();
    }

    // -------------------- /api/iris/lecture-search --------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnResults() throws Exception {
        var results = List.of(
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(1L, "Introduction Slide", "/link/1", 3, null), "supervised learning snippet"),
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(2L, "Neural Networks", "/link/2", 7, null), "backpropagation snippet"));
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

    // -------------------- /api/iris/search-answer --------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_shouldReturn202WithToken() throws Exception {
        irisRequestMockProvider.mockSearchAsk();

        var requestDTO = new IrisSearchAskClientRequestDTO("What is backpropagation?", 5);
        @SuppressWarnings("unchecked")
        var response = (Map<String, String>) request.postWithResponseBody("/api/iris/search-answer", requestDTO, Map.class, HttpStatus.ACCEPTED);

        assertThat(response).containsKey("token");
        assertThat(response.get("token")).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        irisRequestMockProvider.mockSearchAskError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new IrisSearchAskClientRequestDTO("machine learning", 5);
        request.postWithResponseBody("/api/iris/search-answer", requestDTO, Map.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void ask_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new IrisSearchAskClientRequestDTO("machine learning", 5);
        request.postWithResponseBody("/api/iris/search-answer", requestDTO, Map.class, HttpStatus.UNAUTHORIZED);
    }

    // -------------------- api/iris/internal/search/runs/{runId}/status --------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchCallback_withValidToken_shouldReturn200() throws Exception {
        irisRequestMockProvider.mockSearchAsk();

        // 1. Trigger ask to get a valid token
        var askRequest = new IrisSearchAskClientRequestDTO("What is backpropagation?", 5);
        @SuppressWarnings("unchecked")
        var askResponse = (Map<String, String>) request.postWithResponseBody("/api/iris/search-answer", askRequest, Map.class, HttpStatus.ACCEPTED);
        var token = askResponse.get("token");

        // 2. Simulate Pyris posting the first callback (plain answer)
        var source = new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(1L, "ML"), new PyrisLectureSearchResultDTO.LectureDTO(2L, "Intro"),
                new PyrisLectureSearchResultDTO.LectureUnitDTO(3L, "Slide 1", "/link/3", 5, null), null);
        var callbackDTO = new IrisSearchAnswerCallbackDTO(false, "Neural networks learn via backpropagation.", List.of(source));
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + token))));

        request.postWithoutResponseBody("/api/iris/internal/search/runs/" + token + "/status", callbackDTO, HttpStatus.OK, headers);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void searchCallback_withInvalidToken_shouldReturn403() throws Exception {
        var callbackDTO = new IrisSearchAnswerCallbackDTO(false, "Some answer.", List.of());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + "invalid-token"))));

        request.postWithoutResponseBody("/api/iris/internal/search/runs/invalid-token/status", callbackDTO, HttpStatus.FORBIDDEN, headers);
    }
}
