package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RequestUtilService {

    @Value("${jhipster.clientApp.name}")
    private String APPLICATION_NAME;

    private final MockMvc mvc;

    private final ObjectMapper mapper;

    public RequestUtilService(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    public MockMvc getMvc() {
        return mvc;
    }

    public ObjectMapper getObjectMapper() {
        return mapper;
    }

    public <T, R> R postWithMultipartFile(String path, T paramValue, String paramName, MockMultipartFile file, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(paramValue);
        MockMultipartFile json = new MockMultipartFile(paramName, "", MediaType.APPLICATION_JSON_VALUE, jsonBody.getBytes());
        MvcResult res = mvc.perform(MockMvcRequestBuilders.multipart(new URI(path)).file(file).file(json)).andExpect(status().is(expectedStatus.value())).andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        restoreSecurityContext();
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
        MvcResult res = mvc.perform(requestBuilder).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (withLocation && !expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        assertThat(res.getResponse().containsHeader("location")).isTrue();
        return new URI(res.getResponse().getHeader("location"));
    }

    public void postForm(String path, Object body, HttpStatus expectedStatus) throws Exception {
        final var mapper = new ObjectMapper();
        final var jsonMap = mapper.convertValue(body, new TypeReference<Map<String, String>>() {
        });
        final var content = new LinkedMultiValueMap<String, String>();
        content.setAll(jsonMap);
        final var res = mvc.perform(MockMvcRequestBuilders.post(new URI(path)).params(content)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
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
        mvc.perform(request).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
    }

    public void postWithoutResponseBody(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
    }

    public void postWithoutResponseBody(String path, Object body, HttpStatus expectedStatus) throws Exception {
        postWithoutResponseBody(path, body, expectedStatus, null, null);
    }

    public void postWithoutResponseBody(String path, Object body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders) throws Exception {
        postWithoutResponseBody(path, body, expectedStatus, httpHeaders, null);
    }

    public void postWithoutResponseBody(String path, Object body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        var request = MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        if (httpHeaders != null) {
            request.headers(httpHeaders);
        }
        MvcResult res = mvc.perform(request).andExpect(status().is(expectedStatus.value())).andReturn();
        if (expectedResponseHeaders != null) {
            for (String headerKey : expectedResponseHeaders.keySet()) {
                assertThat(res.getResponse().getHeaderValues(headerKey).get(0)).isEqualTo(expectedResponseHeaders.get(headerKey));
            }
        }
        restoreSecurityContext();
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
        MvcResult res = mvc.perform(request).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        if (expectedResponseHeaders != null) {
            for (String headerKey : expectedResponseHeaders.keySet()) {
                assertThat(res.getResponse().getHeaderValues(headerKey).get(0)).isEqualTo(expectedResponseHeaders.get(headerKey));
            }
        }
        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders, @Nullable LinkedMultiValueMap<String, String> params) throws Exception {
        String res = postWithResponseBodyString(path, body, expectedStatus, httpHeaders, expectedResponseHeaders, params);
        if (res == null) {
            return null;
        }
        return mapper.readValue(res, responseType);
    }

    /**
     * Mocks sending a request and returns response content as string
     * @param path the url to send request to
     * @param body the body of the request
     * @param expectedStatus the status that the request will return
     * @param httpHeaders headers of request
     * @param expectedResponseHeaders headers of response
     * @param params parameters for multi value
     * @param <T> Request type
     * @return Request content as string
     * @throws Exception
     */
    public <T> String postWithResponseBodyString(String path, T body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders, @Nullable LinkedMultiValueMap<String, String> params) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        var request = MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        if (httpHeaders != null) {
            request = request.headers(httpHeaders);
        }
        if (params != null) {
            request = request.params(params);
        }
        MvcResult res = mvc.perform(request).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        if (expectedResponseHeaders != null) {
            for (String headerKey : expectedResponseHeaders.keySet()) {
                assertThat(res.getResponse().getHeaderValues(headerKey).get(0)).isEqualTo(expectedResponseHeaders.get(headerKey));
            }
        }
        return res.getResponse().getContentAsString();
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders) throws Exception {
        return postWithResponseBody(path, body, responseType, expectedStatus, httpHeaders, expectedResponseHeaders, new LinkedMultiValueMap<>());
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return postWithResponseBody(path, body, responseType, expectedStatus, null, null);
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
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).accept(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        final var tmpFile = File.createTempFile(res.getResponse().getHeader("filename"), null);
        Files.write(tmpFile.toPath(), res.getResponse().getContentAsByteArray());

        return tmpFile;
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody)).andReturn();
        restoreSecurityContext();
        if (res.getResponse().getStatus() >= 299) {
            return null;
        }
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
        MvcResult res = mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).params(params))
                .andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        if (expectedResponseHeaders != null) {
            for (String headerKey : expectedResponseHeaders.keySet()) {
                assertThat(res.getResponse().getHeaderValues(headerKey).get(0)).isEqualTo(expectedResponseHeaders.get(headerKey));
            }
        }

        if (res.getResponse().getStatus() >= 299) {
            return null;
        }

        if (responseType == String.class) {
            return (R) res.getResponse().getContentAsString();
        }

        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public <R> R patchWithResponseBody(String path, String body, Class<R> responseType, HttpStatus expectedStatus, MediaType mediaType) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.patch(new URI(path)).contentType(mediaType).content(body)).andExpect(status().is(expectedStatus.value())).andReturn();
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

    public void patch(String path, Object body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = body != null ? mapper.writeValueAsString(body) : null;
        var requestBuilder = MockMvcRequestBuilders.patch(new URI(path)).contentType(MediaType.APPLICATION_JSON);
        if (jsonBody != null) {
            requestBuilder = requestBuilder.content(jsonBody);
        }

        mvc.perform(requestBuilder).andExpect(status().is(expectedStatus.value()));
        restoreSecurityContext();

    }

    public <T, R> List<R> putWithResponseBodyList(String path, T body, Class<R> listElementType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
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

        mvc.perform(requestBuilder).andExpect(status().is(expectedStatus.value()));
        restoreSecurityContext();
    }

    public void putAndExpectError(String path, Object body, HttpStatus expectedStatus, String expectedErrorKey) throws Exception {
        final var jsonBody = mapper.writeValueAsString(body);
        final var response = mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
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

    public File getFile(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params).headers(new HttpHeaders())).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }

        String tmpDirectory = System.getProperty("java.io.tmpdir");
        var filename = res.getResponse().getHeader("filename");
        var tmpFile = Files.createFile(Path.of(tmpDirectory, filename));
        Files.write(tmpFile, res.getResponse().getContentAsByteArray());
        return tmpFile.toFile();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType, MultiValueMap<String, String> params, HttpHeaders httpHeaders) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params).headers(httpHeaders)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        final var contentAsString = res.getResponse().getContentAsString();
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
        return mapper.readValue(contentAsString, responseType);
    }

    public byte[] getPng(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        final var res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
        return res.getResponse().getContentAsByteArray();
    }

    public <T> List<T> getList(String path, HttpStatus expectedStatus, Class<T> listElementType) throws Exception {
        return getList(path, expectedStatus, listElementType, new LinkedMultiValueMap<>());
    }

    public <T> List<T> getList(String path, HttpStatus expectedStatus, Class<T> listElementType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")) {
                assertThat(res.getResponse().getContentAsString()).isNullOrEmpty();
            }
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }

    public <K, V> Map<K, V> getMap(String path, HttpStatus expectedStatus, Class<K> keyType, Class<V> valueType) throws Exception {
        return getMap(path, expectedStatus, keyType, valueType, new LinkedMultiValueMap<>());
    }

    public <K, V> Map<K, V> getMap(String path, HttpStatus expectedStatus, Class<K> keyType, Class<V> valueType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")) {
                assertThat(res.getResponse().getContentAsString()).isNullOrEmpty();
            }
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructMapType(Map.class, keyType, valueType));
    }

    public <T> T getWithHeaders(String path, HttpStatus expectedStatus, Class<T> responseType, MultiValueMap<String, String> params, HttpHeaders httpHeaders,
            String[] expectedResponseHeaders) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params).headers(httpHeaders)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();

        if (expectedResponseHeaders != null) {
            for (String header : expectedResponseHeaders) {
                assertThat(res.getResponse().containsHeader(header)).isTrue();
            }
        }

        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public void getWithForwardedUrl(String path, HttpStatus expectedStatus, String expectedRedirectedUrl) throws Exception {
        mvc.perform(MockMvcRequestBuilders.get(new URI(path))).andExpect(status().is(expectedStatus.value())).andExpect(forwardedUrl(expectedRedirectedUrl)).andReturn();
        restoreSecurityContext();
    }

    public void delete(String path, HttpStatus expectedStatus) throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete(new URI(path))).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
    }

    public void delete(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete(new URI(path)).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        restoreSecurityContext();
    }

    /**
     * The Security Context gets cleared by {@link org.springframework.security.web.context.SecurityContextPersistenceFilter} after a REST call.
     * To prevent issues with further queries and rest calls in a test we restore the security context from the test security context holder
     */
    private void restoreSecurityContext() {
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
}
