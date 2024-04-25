package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import jakarta.annotation.Nullable;
import jakarta.servlet.ServletException;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

@Service
public class RequestUtilService {

    @Value("${jhipster.clientApp.name}")
    private String APPLICATION_NAME;

    private final MockMvc mvc;

    private final ObjectMapper mapper;

    private final RequestPostProcessor requestPostProcessor;

    public RequestUtilService(MockMvc mvc, ObjectMapper mapper, @Autowired(required = false) FixMissingServletPathProcessor fixMissingServletPathProcessor)
            throws ServletException {
        this.mvc = mvc;
        this.mapper = mapper;
        this.requestPostProcessor = fixMissingServletPathProcessor;
    }

    /**
     * Executes a built MVC request on the {@link #mvc} instance. We don't allow direct access because we need to ensure that the post processor gets applied if needed. See
     * {@link FixMissingServletPathProcessor} for more information.
     *
     * @param requestBuilder the request to execute
     * @return the result actions
     */
    public ResultActions performMvcRequest(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mvc.perform(addRequestPostProcessorIfAvailable(requestBuilder));
    }

    public ObjectMapper getObjectMapper() {
        return mapper;
    }

    private MockHttpServletRequestBuilder addRequestPostProcessorIfAvailable(MockHttpServletRequestBuilder request) {
        if (requestPostProcessor != null) {
            return request.with(requestPostProcessor);
        }
        return request;
    }

    /**
     * Sends a multipart post request with a mandatory json file and an optional file.
     *
     * @param path           the path to send the request to
     * @param paramValue     the main object to be sent as json
     * @param paramName      the name of the json file
     * @param file           the optional file to be sent
     * @param responseType   the expected response type as class
     * @param expectedStatus the expected status
     * @param <T>            the type of the main object to send
     * @param <R>            the type of the response object
     * @return the response as object of the given type or null if the status is not 2xx
     * @throws Exception if the request fails
     */
    public <T, R> R postWithMultipartFile(String path, T paramValue, String paramName, MockMultipartFile file, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return postWithMultipartFiles(path, paramValue, paramName, file != null ? List.of(file) : null, responseType, expectedStatus);
    }

    /**
     * Sends a multipart post request with a mandatory json file and futher optional files.
     *
     * @param path           the path to send the request to
     * @param paramValue     the main object to be sent as json
     * @param paramName      the name of the json file
     * @param files          the optional files to be sent
     * @param responseType   the expected response type as class
     * @param expectedStatus the expected status
     * @param <T>            the type of the main object to send
     * @param <R>            the type of the response object
     * @return the response as object of the given type or null if the status is not 2xx
     * @throws Exception if the request fails
     */
    public <T, R> R postWithMultipartFiles(String path, T paramValue, String paramName, List<MockMultipartFile> files, Class<R> responseType, HttpStatus expectedStatus)
            throws Exception {
        String jsonBody = mapper.writeValueAsString(paramValue);
        MockMultipartFile json = new MockMultipartFile(paramName, "", MediaType.APPLICATION_JSON_VALUE, jsonBody.getBytes());
        var builder = MockMvcRequestBuilders.multipart(new URI(path));
        if (files != null) {
            for (MockMultipartFile file : files) {
                builder = builder.file(file);
            }
        }
        builder = builder.file(json);
        MvcResult res = performMvcRequest(builder).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public <T> URI post(String path, T body, HttpStatus expectedStatus) throws Exception {
        return post(path, body, expectedStatus, MediaType.APPLICATION_JSON, true);
    }

    public URI post(String path, Object body, HttpStatus expectedStatus, MediaType contentType, boolean withLocation) throws Exception {
        return post(path, body, expectedStatus, contentType, withLocation, null);
    }

    public URI post(String path, Object body, HttpStatus expectedStatus, MediaType contentType, boolean withLocation, @Nullable HttpHeaders httpHeaders) throws Exception {
        String jsonBody = body != null ? mapper.writeValueAsString(body) : null;
        var requestBuilder = MockMvcRequestBuilders.post(new URI(path)).contentType(contentType);
        if (jsonBody != null) {
            requestBuilder = requestBuilder.content(jsonBody);
        }
        if (httpHeaders != null) {
            requestBuilder = requestBuilder.headers(httpHeaders);
        }
        MvcResult res = performMvcRequest(requestBuilder).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (withLocation && !expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        assertThat(res.getResponse().containsHeader("location")).isTrue();
        return new URI(res.getResponse().getHeader("location"));
    }

    public URI postForm(String path, Object body, HttpStatus expectedStatus) throws Exception {
        final var mapper = new ObjectMapper();
        final var jsonMap = mapper.convertValue(body, new TypeReference<Map<String, String>>() {
        });
        final var content = new LinkedMultiValueMap<String, String>();
        content.setAll(jsonMap);
        MvcResult result = performMvcRequest(MockMvcRequestBuilders.post(new URI(path)).params(content)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        return new URI(result.getResponse().getHeader("location"));
    }

    public void postFormWithoutLocation(String path, Object body, HttpStatus expectedStatus) throws Exception {
        final var mapper = new ObjectMapper();
        final var jsonMap = mapper.convertValue(body, new TypeReference<Map<String, String>>() {
        });
        final var content = new LinkedMultiValueMap<String, String>();
        content.setAll(jsonMap);
        performMvcRequest(MockMvcRequestBuilders.post(new URI(path)).params(content)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
    }

    public void postStringWithoutLocation(String path, String body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders) throws Exception {
        postWithoutLocation(path, request -> request.content(body), expectedStatus, httpHeaders, MediaType.APPLICATION_JSON_VALUE);
    }

    public <T> void postWithoutLocation(String path, T body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders) throws Exception {
        final String jsonBody = mapper.writeValueAsString(body);
        postWithoutLocation(path, request -> request.content(jsonBody), expectedStatus, httpHeaders, MediaType.APPLICATION_JSON_VALUE);
    }

    public void postWithoutLocation(String path, byte[] body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders, String contentType) throws Exception {
        postWithoutLocation(path, request -> request.content(body), expectedStatus, httpHeaders, contentType);
    }

    private void postWithoutLocation(String path, Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder> contentCompletion, HttpStatus expectedStatus,
            @Nullable HttpHeaders httpHeaders, String contentType) throws Exception {
        var request = MockMvcRequestBuilders.post(new URI(path)).contentType(contentType);
        contentCompletion.apply(request);
        if (httpHeaders != null) {
            request = request.headers(httpHeaders);
        }
        performMvcRequest(request).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
    }

    public MockHttpServletResponse postWithoutResponseBody(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.post(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        return res.getResponse();
    }

    public void postWithoutResponseBody(String path, Object body, HttpStatus expectedStatus) throws Exception {
        postWithoutResponseBody(path, body, expectedStatus, null, null);
    }

    public MockHttpServletResponse postWithoutResponseBody(String path, Object body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders) throws Exception {
        return postWithoutResponseBody(path, body, expectedStatus, httpHeaders, null);
    }

    public MockHttpServletResponse postWithoutResponseBody(String path, Object body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        var request = MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        if (httpHeaders != null) {
            request.headers(httpHeaders);
        }
        MvcResult res = performMvcRequest(request).andExpect(status().is(expectedStatus.value())).andReturn();
        verifyExpectedResponseHeaders(expectedResponseHeaders, res);
        restoreSecurityContext();
        return res.getResponse();
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders) throws Exception {
        return postWithResponseBody(path, body, responseType, expectedStatus, httpHeaders, null);
    }

    public <T, R> List<R> postListWithResponseBody(String path, T body, Class<R> listElementType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        var request = MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        if (httpHeaders != null) {
            request = request.headers(httpHeaders);
        }
        MvcResult res = performMvcRequest(request).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        verifyExpectedResponseHeaders(expectedResponseHeaders, res);
        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders, @Nullable LinkedMultiValueMap<String, String> params) throws Exception {
        return postWithResponseBody(path, body, false, responseType, expectedStatus, httpHeaders, expectedResponseHeaders, params);
    }

    public <T, R> R postWithResponseBody(String path, T body, boolean plainString, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders, @Nullable LinkedMultiValueMap<String, String> params) throws Exception {
        if (!plainString) {
            String res = postWithResponseBodyString(path, body, expectedStatus, httpHeaders, expectedResponseHeaders, params);
            if (res == null || res.isEmpty() || res.trim().isEmpty()) {
                return null;
            }
            return mapper.readValue(res, responseType);
        }
        String res = postWithResponseBodyString(path, body, true, expectedStatus, httpHeaders, expectedResponseHeaders, params);
        if (res == null || res.isEmpty() || res.trim().isEmpty()) {
            return null;
        }
        return mapper.readValue(res, responseType);
    }

    /**
     * Mocks sending a request and returns response content as string
     *
     * @param path                    the url to send request to
     * @param body                    the body of the request
     * @param expectedStatus          the status that the request will return
     * @param httpHeaders             headers of request
     * @param expectedResponseHeaders headers of response
     * @param params                  parameters for multi value
     * @param <T>                     Request type
     * @return Request content as string
     * @throws Exception if the request fails
     */
    public <T> String postWithResponseBodyString(String path, T body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders, @Nullable LinkedMultiValueMap<String, String> params) throws Exception {
        return postWithResponseBodyString(path, body, false, expectedStatus, httpHeaders, expectedResponseHeaders, params);
    }

    /**
     * Mocks sending a request and returns response content as string
     *
     * @param path                    the url to send request to
     * @param body                    the body of the request
     * @param plainStringBody         if true, the body is not converted to json
     * @param expectedStatus          the status that the request will return
     * @param httpHeaders             headers of request
     * @param expectedResponseHeaders headers of response
     * @param params                  parameters for multi value
     * @param <T>                     Request type
     * @return Request content as string
     * @throws Exception if the request fails
     */
    public <T> String postWithResponseBodyString(String path, T body, boolean plainStringBody, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders, @Nullable LinkedMultiValueMap<String, String> params) throws Exception {
        String jsonBody;
        if (!plainStringBody) {
            jsonBody = mapper.writeValueAsString(body);
        }
        else {
            jsonBody = (String) body;
        }
        var request = MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        if (httpHeaders != null) {
            request = request.headers(httpHeaders);
        }
        if (params != null) {
            request = request.params(params);
        }
        MvcResult res = performMvcRequest(request).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        verifyExpectedResponseHeaders(expectedResponseHeaders, res);
        return res.getResponse().getContentAsString();
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders) throws Exception {
        return postWithResponseBody(path, body, false, responseType, expectedStatus, httpHeaders, expectedResponseHeaders, new LinkedMultiValueMap<>());
    }

    public <T, R> R postWithResponseBody(String path, T body, boolean plainString, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders) throws Exception {
        if (!plainString) {
            return postWithResponseBody(path, body, responseType, expectedStatus, httpHeaders, expectedResponseHeaders, new LinkedMultiValueMap<>());
        }
        return postWithResponseBody(path, body, true, responseType, expectedStatus, httpHeaders, expectedResponseHeaders, new LinkedMultiValueMap<>());
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return postWithResponseBody(path, body, responseType, expectedStatus, null, null);
    }

    public <T, R> R postWithPlainStringResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return postWithResponseBody(path, body, true, responseType, expectedStatus, null, null);
    }

    public <T, R> String postWithResponseBodyString(String path, T body, HttpStatus expectedStatus) throws Exception {
        return postWithResponseBodyString(path, body, expectedStatus, null, null, new LinkedMultiValueMap<>());
    }

    public <T, R> List<R> postListWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return postListWithResponseBody(path, body, responseType, expectedStatus, null, null);
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, @Nullable LinkedMultiValueMap<String, String> params, HttpStatus expectedStatus)
            throws Exception {
        return postWithResponseBody(path, body, responseType, expectedStatus, null, null, params);
    }

    public File postWithResponseBodyFile(String path, Object body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc
                .perform(addRequestPostProcessorIfAvailable(
                        MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).accept(MediaType.APPLICATION_OCTET_STREAM)))
                .andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        final var tmpFile = File.createTempFile(res.getResponse().getHeader("filename"), null);
        FileUtils.writeByteArrayToFile(tmpFile, res.getResponse().getContentAsByteArray());

        return tmpFile;
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody)).andReturn();
        restoreSecurityContext();
        if (res.getResponse().getStatus() >= 299) {
            return null;
        }
        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    /**
     * Sends a multipart put request with a mandatory json file and an optional file.
     *
     * @param path           the path to send the request to
     * @param paramValue     the main object to be sent as json
     * @param paramName      the name of the json file
     * @param file           the optional file to be sent
     * @param responseType   the expected response type as class
     * @param expectedStatus the expected status
     * @param params         the optional parameters for the request
     * @param <T>            the type of the main object to send
     * @param <R>            the type of the response object
     * @return the response as object of the given type or null if the status is not 2xx
     * @throws Exception if the request fails
     */
    public <T, R> R putWithMultipartFile(String path, T paramValue, String paramName, MockMultipartFile file, Class<R> responseType, HttpStatus expectedStatus,
            LinkedMultiValueMap<String, String> params) throws Exception {
        return putWithMultipartFiles(path, paramValue, paramName, file != null ? List.of(file) : null, responseType, expectedStatus, params);
    }

    /**
     * Sends a multipart put request with a mandatory json file and optional files.
     *
     * @param path           the path to send the request to
     * @param paramValue     the main object to be sent as json
     * @param paramName      the name of the json file
     * @param files          the optional files to be sent
     * @param responseType   the expected response type as class
     * @param expectedStatus the expected status
     * @param params         the optional parameters for the request
     * @param <T>            the type of the main object to send
     * @param <R>            the type of the response object
     * @return the response as object of the given type or null if the status is not 2xx
     * @throws Exception if the request fails
     */
    public <T, R> R putWithMultipartFiles(String path, T paramValue, String paramName, List<MockMultipartFile> files, Class<R> responseType, HttpStatus expectedStatus,
            LinkedMultiValueMap<String, String> params) throws Exception {
        String jsonBody = mapper.writeValueAsString(paramValue);
        MockMultipartFile json = new MockMultipartFile(paramName, "", MediaType.APPLICATION_JSON_VALUE, jsonBody.getBytes());
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(new URI(path)).file(json);
        builder.with(request -> {
            request.setMethod(HttpMethod.PUT.toString());
            return request;
        });
        if (files != null) {
            for (MockMultipartFile file : files) {
                builder.file(file);
            }
        }
        if (params != null) {
            builder.params(params);
        }
        MvcResult res = performMvcRequest(builder).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public <T, R> R putWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus, Map<String, String> expectedResponseHeaders) throws Exception {
        return putWithResponseBodyAndParams(path, body, responseType, expectedStatus, new LinkedMultiValueMap<>(), expectedResponseHeaders);
    }

    public <T, R> R putWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return putWithResponseBodyAndParams(path, body, responseType, expectedStatus, new LinkedMultiValueMap<>(), new HashMap<>());
    }

    public <T, R> R putWithResponseBodyAndParams(String path, T body, Class<R> responseType, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        return putWithResponseBodyAndParams(path, body, responseType, expectedStatus, params, new HashMap<>());
    }

    public <T, R> R putWithResponseBodyAndParams(String path, T body, Class<R> responseType, HttpStatus expectedStatus, @Nullable MultiValueMap<String, String> params,
            Map<String, String> expectedResponseHeaders) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc
                .perform(addRequestPostProcessorIfAvailable(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).params(params)))
                .andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        verifyExpectedResponseHeaders(expectedResponseHeaders, res);

        if (res.getResponse().getStatus() >= 299) {
            return null;
        }

        if (responseType == String.class) {
            return (R) res.getResponse().getContentAsString();
        }
        // default encoding is iso-8859-1 since v5.2.0, but we want utf-8
        return mapper.readValue(res.getResponse().getContentAsString(StandardCharsets.UTF_8), responseType);
    }

    public <R> R patchWithResponseBody(String path, String body, Class<R> responseType, HttpStatus expectedStatus, MediaType mediaType) throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.patch(new URI(path)).contentType(mediaType).content(body)).andExpect(status().is(expectedStatus.value()))
                .andReturn();
        restoreSecurityContext();

        if (res.getResponse().getStatus() >= 299) {
            return null;
        }

        final var resString = res.getResponse().getContentAsString();
        return responseType != String.class ? mapper.readValue(resString, responseType) : (R) resString;
    }

    public <R> R patchWithResponseBody(String path, Object body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return patchWithResponseBody(path, mapper.writeValueAsString(body), responseType, expectedStatus, MediaType.APPLICATION_JSON);
    }

    public <T> T patchWithResponseBody(String path, Object body, TypeReference<T> responseType, HttpStatus expectedStatus) throws Exception {
        final var stringResponse = patchWithResponseBody(path, body, String.class, expectedStatus);

        return mapper.readValue(stringResponse, responseType);
    }

    public <T, R> List<R> patchWithResponseBodyList(String path, T body, Class<R> listElementType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.patch(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }

    public void patch(String path, Object body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = body != null ? mapper.writeValueAsString(body) : null;
        var requestBuilder = MockMvcRequestBuilders.patch(new URI(path)).contentType(MediaType.APPLICATION_JSON);
        if (jsonBody != null) {
            requestBuilder = requestBuilder.content(jsonBody);
        }

        performMvcRequest(requestBuilder).andExpect(status().is(expectedStatus.value()));
        restoreSecurityContext();

    }

    public <T, R> List<R> putWithResponseBodyList(String path, T body, Class<R> listElementType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }

    public void put(String path, Object body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = body != null ? mapper.writeValueAsString(body) : null;
        var requestBuilder = MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON);
        if (jsonBody != null) {
            requestBuilder = requestBuilder.content(jsonBody);
        }

        performMvcRequest(requestBuilder).andExpect(status().is(expectedStatus.value()));
        restoreSecurityContext();
    }

    public void putAndExpectError(String path, Object body, HttpStatus expectedStatus, String expectedErrorKey) throws Exception {
        final var jsonBody = mapper.writeValueAsString(body);
        final var response = performMvcRequest(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().is(expectedStatus.value())).andReturn().getResponse();
        restoreSecurityContext();

        final var fullErrorKey = "error." + expectedErrorKey;
        final var errorHeader = "X-" + APPLICATION_NAME + "-error";
        assertThat(response.getHeader(errorHeader)).isEqualTo(fullErrorKey);
    }

    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType) throws Exception {
        return get(path, expectedStatus, responseType, new LinkedMultiValueMap<>());
    }

    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType, HttpHeaders httpHeaders) throws Exception {
        return get(path, expectedStatus, responseType, new LinkedMultiValueMap<>(), httpHeaders);
    }

    public <T> T getNullable(String path, HttpStatus expectedStatus, Class<T> responseType) throws Exception {
        final var res = get(path, expectedStatus, String.class, new LinkedMultiValueMap<>());
        if (res == null || res.isEmpty()) {
            return null;
        }

        return mapper.readValue(res, responseType);
    }

    public <T> T get(String path, HttpStatus expectedStatus, TypeReference<T> responseType) throws Exception {
        var stringResponse = get(path, expectedStatus, String.class, new LinkedMultiValueMap<>(), new HttpHeaders());
        return mapper.readValue(stringResponse, responseType);
    }

    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType, MultiValueMap<String, String> params) throws Exception {
        return get(path, expectedStatus, responseType, params, new HttpHeaders());
    }

    public File getFile(String path, HttpStatus expectedStatus) throws Exception {
        return getFile(path, expectedStatus, new LinkedMultiValueMap<>());
    }

    public File getFile(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        return getFile(path, expectedStatus, params, null);
    }

    public File getFile(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params, @Nullable Map<String, String> expectedResponseHeaders) throws Exception {
        return getFile(path, expectedStatus, params, new HttpHeaders(), expectedResponseHeaders);
    }

    public File getFile(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params, HttpHeaders headers, @Nullable Map<String, String> expectedResponseHeaders)
            throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.get(new URI(path)).params(params).headers(headers)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        verifyExpectedResponseHeaders(expectedResponseHeaders, res);

        String tmpDirectory = System.getProperty("java.io.tmpdir");
        var filename = res.getResponse().getHeader("filename");
        var tmpFile = Files.createFile(Path.of(tmpDirectory, filename));
        FileUtils.writeByteArrayToFile(tmpFile.toFile(), res.getResponse().getContentAsByteArray());
        return tmpFile.toFile();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType, MultiValueMap<String, String> params, HttpHeaders httpHeaders) throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.get(new URI(path)).params(params).headers(httpHeaders)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        // default charset is iso-8859-1 since v5.2.0 but we want utf-8
        final var contentAsString = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")
                    && !res.getResponse().getContentType().equals("application/octet-stream")) {
                assertThat(contentAsString).isNullOrEmpty();
            }
            return null;
        }
        if (responseType == String.class) {
            return (T) contentAsString;
        }
        if (responseType == Boolean.class) {
            return (T) Boolean.valueOf(contentAsString);
        }
        if (responseType == byte[].class) {
            return (T) res.getResponse().getContentAsByteArray();
        }
        if (responseType == Void.class && contentAsString.isEmpty()) {
            return (T) "";
        }
        if (contentAsString.isEmpty() || res.getResponse().getContentType() == null) {
            return null;
        }
        return mapper.readValue(contentAsString, responseType);
    }

    public byte[] getPng(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        final var res = performMvcRequest(MockMvcRequestBuilders.get(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        return res.getResponse().getContentAsByteArray();
    }

    public <T> List<T> getList(String path, HttpStatus expectedStatus, Class<T> listElementType) throws Exception {
        return getList(path, expectedStatus, listElementType, new LinkedMultiValueMap<>());
    }

    public <T> Set<T> getSet(String path, HttpStatus expectedStatus, Class<T> setElementType) throws Exception {
        return getSet(path, expectedStatus, setElementType, new LinkedMultiValueMap<>());
    }

    public <T> SearchResultPageDTO<T> getSearchResult(String path, HttpStatus expectedStatus, Class<T> searchElementType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.get(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")) {
                assertThat(res.getResponse().getContentAsString()).isNullOrEmpty();
            }
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructParametricType(SearchResultPageDTO.class, searchElementType));
    }

    public <T> List<T> getList(String path, HttpStatus expectedStatus, Class<T> listElementType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.get(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")) {
                assertThat(res.getResponse().getContentAsString()).isNullOrEmpty();
            }
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }

    public <T> Set<T> getSet(String path, HttpStatus expectedStatus, Class<T> setElementType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.get(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")) {
                assertThat(res.getResponse().getContentAsString()).isNullOrEmpty();
            }
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(Set.class, setElementType));
    }

    public <K, V> Map<K, V> getMap(String path, HttpStatus expectedStatus, Class<K> keyType, Class<V> valueType) throws Exception {
        return getMap(path, expectedStatus, keyType, valueType, new LinkedMultiValueMap<>());
    }

    public <K, V> Map<K, V> getMap(String path, HttpStatus expectedStatus, Class<K> keyType, Class<V> valueType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.get(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")) {
                assertThat(res.getResponse().getContentAsString()).isNullOrEmpty();
            }
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructMapType(Map.class, keyType, valueType));
    }

    public void getWithForwardedUrl(String path, HttpStatus expectedStatus, String expectedRedirectedUrl) throws Exception {
        performMvcRequest(MockMvcRequestBuilders.get(new URI(path))).andExpect(status().is(expectedStatus.value())).andExpect(forwardedUrl(expectedRedirectedUrl)).andReturn();
        restoreSecurityContext();
    }

    public String getRedirectTarget(String path, HttpStatus expectedStatus) throws Exception {
        MvcResult res = performMvcRequest(MockMvcRequestBuilders.get(new URI(path))).andExpect(status().is(expectedStatus.value())).andReturn();
        return res.getResponse().getRedirectedUrl();
    }

    public void delete(String path, HttpStatus expectedStatus) throws Exception {
        performMvcRequest(MockMvcRequestBuilders.delete(new URI(path))).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
    }

    public void delete(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        performMvcRequest(MockMvcRequestBuilders.delete(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
    }

    public <T> void delete(String path, HttpStatus expectedStatus, T body) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        performMvcRequest(MockMvcRequestBuilders.delete(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody)).andExpect(status().is(expectedStatus.value()))
                .andReturn();
        restoreSecurityContext();
    }

    /**
     * The Security Context gets cleared by {@link org.springframework.security.web.context.SecurityContextPersistenceFilter} after a REST call.
     * To prevent issues with further queries and rest calls in a test we restore the security context from the test security context holder
     */
    public void restoreSecurityContext() {
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
    }

    /**
     * Converts a {@link Map} to a {@link MultiValueMap} that can be used to specify request parameters.
     *
     * @param <V> the type of the values, will be converted to String using {@link Object#toString()}.
     * @param map the normal Java map to convert. Use e.g. one of the {@link Map#of()} methods to get one. Must not contain null.
     * @return a {@link MultiValueMap} that can be passed to requests as parameters
     */
    public static <V> MultiValueMap<String, String> parameters(Map<String, V> map) {
        MultiValueMap<String, String> multiMap = new LinkedMultiValueMap<>();
        map.forEach((key, value) -> {
            Objects.requireNonNull(key, "paremeter key must not be null");
            Objects.requireNonNull(value, "paremeter value must not be null");
            multiMap.add(key, value.toString());
        });
        return multiMap;
    }

    /**
     * Verifies the expected response headers against the actual response headers.
     *
     * @param expectedResponseHeaders a map containing the expected response headers
     * @param res                     the {@link MvcResult} containing the actual response headers
     */
    private static void verifyExpectedResponseHeaders(Map<String, String> expectedResponseHeaders, MvcResult res) {
        if (expectedResponseHeaders != null) {
            for (Map.Entry<String, String> responseHeader : expectedResponseHeaders.entrySet()) {
                assertThat(res.getResponse().getHeaderValues(responseHeader.getKey()).get(0)).isEqualTo(responseHeader.getValue());
            }
        }
    }
}
