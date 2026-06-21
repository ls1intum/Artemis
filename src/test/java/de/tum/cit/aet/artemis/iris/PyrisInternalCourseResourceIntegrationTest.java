package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;

class PyrisInternalCourseResourceIntegrationTest extends AbstractIrisIntegrationTest {

    @Autowired
    private PyrisJobService pyrisJobService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Test
    void getCourseNames_withValidToken_shouldReturnTitlesForRequestedIds() throws Exception {
        var course1 = courseUtilService.createCourse();
        var course2 = courseUtilService.createCourse();

        var params = new LinkedMultiValueMap<String, String>();
        params.add("ids", String.valueOf(course1.getId()));
        params.add("ids", String.valueOf(course2.getId()));

        var response = request.get("/api/iris/internal/courses/names", HttpStatus.OK, String.class, params, createJobTokenHeaders());

        assertThat(response).contains(course1.getTitle()).contains(course2.getTitle());
    }

    @Test
    void getCourseNames_withValidToken_andNonExistentId_shouldReturnEmptyMap() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("ids", String.valueOf(Long.MAX_VALUE));

        var response = request.get("/api/iris/internal/courses/names", HttpStatus.OK, String.class, params, createJobTokenHeaders());

        assertThat(response).isEqualTo("{}");
    }

    @Test
    void getCourseNames_withMissingToken_shouldReturnForbidden() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("ids", "1");

        request.get("/api/iris/internal/courses/names", HttpStatus.FORBIDDEN, String.class, params, new HttpHeaders());
    }

    @Test
    void getCourseNames_withInvalidToken_shouldReturnForbidden() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("ids", "1");
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + "unknown-token-that-does-not-exist"))));

        request.get("/api/iris/internal/courses/names", HttpStatus.FORBIDDEN, String.class, params, headers);
    }

    private HttpHeaders createJobTokenHeaders() {
        var runId = UUID.randomUUID().toString();
        pyrisJobService.addGlobalSearchAnswerJob("testuser", runId);
        return new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + runId))));
    }
}
