package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RequestUtilService {

    @Value("${jhipster.clientApp.name}")
    private String APPLICATION_NAME;

    private MockMvc mvc;

    private ObjectMapper mapper;

    public RequestUtilService(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    public void registerObjectMapperModule(Module module) {
        mapper.registerModule(module);
    }

    public <T, R> R postWithMultipartFile(String path, T paramValue, String paramName, MockMultipartFile file, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(paramValue);
        MockMultipartFile json = new MockMultipartFile(paramName, "", MediaType.APPLICATION_JSON_VALUE, jsonBody.getBytes());
        MvcResult res = mvc.perform(MockMvcRequestBuilders.multipart(new URI(path)).file(file).file(json).with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
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
        String jsonBody = body != null ? mapper.writeValueAsString(body) : null;
        var requestBuilder = MockMvcRequestBuilders.post(new URI(path)).contentType(contentType);
        if (jsonBody != null) {
            requestBuilder = requestBuilder.content(jsonBody);
        }
        MvcResult res = mvc.perform(requestBuilder.with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
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
        final var res = mvc.perform(MockMvcRequestBuilders.post(new URI(path)).params(content).with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
    }

    public <T> void postWithoutLocation(String path, T body, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        var request = MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        if (httpHeaders != null) {
            request = request.headers(httpHeaders);
        }
        mvc.perform(request.with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
    }

    public void postWithoutResponseBody(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(new URI(path)).params(params).with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders) throws Exception {
        return postWithResponseBody(path, body, responseType, expectedStatus, httpHeaders, null);
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus, @Nullable HttpHeaders httpHeaders,
            @Nullable Map<String, String> expectedResponseHeaders) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        var request = MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        if (httpHeaders != null) {
            request = request.headers(httpHeaders);
        }
        MvcResult res = mvc.perform(request.with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        if (expectedResponseHeaders != null) {
            for (String headerKey : expectedResponseHeaders.keySet()) {
                assertThat(res.getResponse().getHeaderValues(headerKey).get(0)).isEqualTo(expectedResponseHeaders.get(headerKey));
            }
        }
        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return postWithResponseBody(path, body, responseType, expectedStatus, null, null);
    }

    public File postWithResponseBodyFile(String path, Object body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(
                MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).accept(MediaType.APPLICATION_OCTET_STREAM).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn();
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
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).with(csrf())).andReturn();
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
        MvcResult res = mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).params(params).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn();

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
        MvcResult res = mvc.perform(MockMvcRequestBuilders.patch(new URI(path)).contentType(mediaType).content(body).with(csrf())).andExpect(status().is(expectedStatus.value()))
                .andReturn();

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

    public <T, R> List<R> putWithResponseBodyList(String path, T body, Class<R> listElementType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn();

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }

    public void put(String path, Object body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = body != null ? mapper.writeValueAsString(body) : null;
        var requestBuilder = MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON);
        if (jsonBody != null) {
            requestBuilder = requestBuilder.content(jsonBody);
        }

        mvc.perform(requestBuilder.with(csrf())).andExpect(status().is(expectedStatus.value()));
    }

    public void putAndExpectError(String path, Object body, HttpStatus expectedStatus, String expectedErrorKey) throws Exception {
        final var jsonBody = mapper.writeValueAsString(body);
        final var response = mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn().getResponse();

        final var fullErrorKey = "error." + expectedErrorKey;
        final var errorHeader = "X-" + APPLICATION_NAME + "-error";
        assertThat(response.getHeader(errorHeader)).isEqualTo(fullErrorKey);
    }

    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType) throws Exception {
        return get(path, expectedStatus, responseType, new LinkedMultiValueMap<>());
    }

    public <T> T getNullable(String path, HttpStatus expectedStatus, Class<T> responseType) throws Exception {
        final var res = get(path, expectedStatus, String.class, new LinkedMultiValueMap<>());
        if (res != null && res.equals("")) {
            return null;
        }

        return mapper.readValue(res, responseType);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params).with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
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
        final var res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params).with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
        return res.getResponse().getContentAsByteArray();
    }

    public <T> List<T> getList(String path, HttpStatus expectedStatus, Class<T> listElementType) throws Exception {
        return getList(path, expectedStatus, listElementType, new LinkedMultiValueMap<>());
    }

    public <T> List<T> getList(String path, HttpStatus expectedStatus, Class<T> listElementType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).with(csrf()).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
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
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).with(csrf()).params(params)).andExpect(status().is(expectedStatus.value())).andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")) {
                assertThat(res.getResponse().getContentAsString()).isNullOrEmpty();
            }
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructMapType(Map.class, keyType, valueType));
    }

    public void delete(String path, HttpStatus expectedStatus) throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete(new URI(path)).with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
    }

    public void delete(String path, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete(new URI(path)).params(params).with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
    }
}
