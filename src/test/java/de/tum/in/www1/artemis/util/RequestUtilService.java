package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RequestUtilService {

    private MockMvc mvc;

    private ObjectMapper mapper;

    public RequestUtilService(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
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

    public <T> URI post(String path, T body, HttpStatus expectedStatus, MediaType contentType, boolean withLocation) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post(new URI(path)).contentType(contentType).content(jsonBody).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn();
        if (withLocation && !expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        assertThat(res.getResponse().containsHeader("location")).isTrue();
        return new URI(res.getResponse().getHeader("location"));
    }

    public <T> void postWithoutLocation(String path, T body, HttpStatus expectedStatus, HttpHeaders httpHeaders) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        mvc.perform(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).headers(httpHeaders).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn();
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location")).as("no location header on failed request").isFalse();
            return null;
        }
        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public <T, R> R postWithResponseBody(String path, T body, Class<R> responseType) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).with(csrf())).andReturn();
        if (res.getResponse().getStatus() >= 299) {
            return null;
        }
        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public <T, R> R putWithResponseBody(String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return putWithResponseBodyAndParams(path, body, responseType, expectedStatus, new LinkedMultiValueMap<String, String>());
    }

    public <T, R> R putWithResponseBodyAndParams(String path, T body, Class<R> responseType, HttpStatus expectedStatus, MultiValueMap<String, String> params) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).params(params).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn();

        if (res.getResponse().getStatus() >= 299) {
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public <R> R patchWithResponseBody(String path, String body, Class<R> responseType, HttpStatus expectedStatus, MediaType mediaType) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.patch(new URI(path)).contentType(mediaType).content(body).with(csrf())).andExpect(status().is(expectedStatus.value()))
                .andReturn();

        if (res.getResponse().getStatus() >= 299) {
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }

    public <R> R patchWithResponseBody(String path, Object body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        return patchWithResponseBody(path, mapper.writeValueAsString(body), responseType, expectedStatus, MediaType.APPLICATION_JSON);
    }

    public <T, R> List<R> putWithResponseBodyList(String path, T body, Class<R> listElementType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).with(csrf()))
                .andExpect(status().is(expectedStatus.value())).andReturn();

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }

    public <T> void put(String path, T body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        mvc.perform(MockMvcRequestBuilders.put(new URI(path)).contentType(MediaType.APPLICATION_JSON).content(jsonBody).with(csrf()))
                .andExpect(status().is(expectedStatus.value()));
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

    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType, MultiValueMap<String, String> params) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).params(params).with(csrf())).andExpect(status().is(expectedStatus.value())).andReturn();
        final var contentAsString = res.getResponse().getContentAsString();
        if (!expectedStatus.is2xxSuccessful()) {
            if (res.getResponse().getContentType() != null && !res.getResponse().getContentType().equals("application/problem+json")) {
                assertThat(contentAsString).isNullOrEmpty();
            }
            return null;
        }

        return responseType == String.class ? (T) contentAsString : mapper.readValue(contentAsString, responseType);
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
}
