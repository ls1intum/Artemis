package de.tum.cit.aet.artemis.core.connector;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.exception.NebulaException;

@Component
@Conditional(NebulaEnabled.class)
@Lazy
public class NebulaRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Value("${artemis.nebula.url}")
    private String nebulaBaseUrl;

    @Value("${artemis.nebula.secret-token}")
    private String nebulaSecretToken;

    @Autowired
    private ObjectMapper mapper;

    public NebulaRequestMockProvider(@Qualifier("nebulaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    public void verify() {
        if (mockServer != null) {
            mockServer.verify();
        }
    }

    private String buildUrl(String path) {
        return UriComponentsBuilder.fromHttpUrl(nebulaBaseUrl).path(path).toUriString();
    }

    private <Req, Res> void mockPostJson(String path, Class<Req> requestType, Function<Req, Res> responder, HttpStatus status, ExpectedCount count) {
        mockServer.expect(count, requestTo(buildUrl(path))).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", nebulaSecretToken)).andRespond(request -> {
            var mockRequest = (MockClientHttpRequest) request;
            var reqBody = mapper.readValue(mockRequest.getBodyAsString(), requestType);
            var resBody = responder.apply(reqBody);

            var creator = MockRestResponseCreators.withStatus(status).contentType(MediaType.APPLICATION_JSON);

            if (resBody != null) {
                creator = creator.body(mapper.writeValueAsString(resBody));
            }
            return creator.createResponse(request);
        });
    }

    public void mockThrowingNebulaExceptionForUrl(String path, NebulaException exception) {
        mockServer.expect(ExpectedCount.once(), requestTo(buildUrl(path))).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", nebulaSecretToken))
                .andRespond(request -> {
                    throw exception;
                });
    }

    public void mockThrowingNebulaRuntimeExceptionForUrl(String path, RuntimeException exception) {
        mockServer.expect(ExpectedCount.once(), requestTo(buildUrl(path))).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", nebulaSecretToken))
                .andRespond(request -> {
                    throw exception;
                });
    }

    /**
     * Mocks a successful video upload response.
     *
     * @param videoId      the video ID to return
     * @param filename     the filename
     * @param playlistUrl  the HLS playlist URL
     * @param durationSecs the video duration in seconds
     * @param sizeBytes    the file size in bytes
     * @param count        the expected number of calls
     */
    public void mockSuccessfulVideoUpload(String videoId, String filename, String playlistUrl, double durationSecs, long sizeBytes, ExpectedCount count) {
        mockServer.expect(count, requestTo(buildUrl("/video-storage/upload"))).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", nebulaSecretToken))
                .andRespond(request -> {
                    var responseBody = mapper.createObjectNode();
                    responseBody.put("video_id", videoId);
                    responseBody.put("filename", filename);
                    responseBody.put("size_bytes", sizeBytes);
                    responseBody.put("uploaded_at", java.time.ZonedDateTime.now().toString());
                    responseBody.put("playlist_url", playlistUrl);
                    responseBody.put("duration_seconds", durationSecs);
                    responseBody.put("message", "Video uploaded and converted to HLS successfully");

                    return MockRestResponseCreators.withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(responseBody.toString()).createResponse(request);
                });
    }

    /**
     * Mocks a successful video deletion.
     *
     * @param videoId the video ID to delete
     * @param count   the expected number of calls
     */
    public void mockSuccessfulVideoDelete(String videoId, ExpectedCount count) {
        mockServer.expect(count, requestTo(buildUrl("/video-storage/delete/" + videoId))).andExpect(method(HttpMethod.DELETE)).andExpect(header("Authorization", nebulaSecretToken))
                .andRespond(request -> MockRestResponseCreators.withStatus(HttpStatus.NO_CONTENT).createResponse(request));
    }

    /**
     * Mocks a failed video upload response.
     *
     * @param status the HTTP status to return
     * @param count  the expected number of calls
     */
    public void mockFailedVideoUpload(HttpStatus status, ExpectedCount count) {
        mockServer.expect(count, requestTo(buildUrl("/video-storage/upload"))).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", nebulaSecretToken))
                .andRespond(request -> {
                    var errorBody = mapper.createObjectNode();
                    errorBody.put("error", "Video upload failed");
                    return MockRestResponseCreators.withStatus(status).contentType(MediaType.APPLICATION_JSON).body(errorBody.toString()).createResponse(request);
                });
    }
}
