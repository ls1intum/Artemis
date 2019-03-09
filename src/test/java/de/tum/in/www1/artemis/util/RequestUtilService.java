package de.tum.in.www1.artemis.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Service
public class RequestUtilService {
    private MockMvc mvc;
    private ObjectMapper mapper;


    public RequestUtilService(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }


    public <T> URI post(String path, T body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res =
            mvc.perform(
                MockMvcRequestBuilders.post(new URI(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonBody)
                    .with(csrf()))
                .andExpect(status().is(expectedStatus.value()))
                .andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location"))
                .as("no location header on failed request")
                .isFalse();
            return null;
        }
        assertThat(res.getResponse().containsHeader("location")).isTrue();
        return new URI(res.getResponse().getHeader("location"));
    }


    public <T, R> R postWithResponseBody(
        String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res =
            mvc.perform(
                MockMvcRequestBuilders.post(new URI(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonBody)
                    .with(csrf()))
                .andExpect(status().is(expectedStatus.value()))
                .andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().containsHeader("location"))
                .as("no location header on failed request")
                .isFalse();
            return null;
        }
        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }


    public <T, R> R putWithResponseBody(
        String path, T body, Class<R> responseType, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res =
            mvc.perform(
                MockMvcRequestBuilders.put(new URI(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonBody)
                    .with(csrf()))
                .andExpect(status().is(expectedStatus.value()))
                .andReturn();

        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }


    public <T> void put(String path, T body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        mvc.perform(
            MockMvcRequestBuilders.put(new URI(path))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody)
                .with(csrf()))
            .andExpect(status().is(expectedStatus.value()));
    }


    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType) throws Exception {
        MvcResult res =
            mvc.perform(MockMvcRequestBuilders.get(new URI(path)).with(csrf()))
                .andExpect(status().is(expectedStatus.value()))
                .andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().getContentAsString())
                .as("problem description instead of result")
                .isEqualTo("application/problem+json"); // TODO MJ more sufficient check?
            return null;
        }
        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }


    public <T> List<T> getList(String path, HttpStatus expectedStatus, Class<T> listElementType)
        throws Exception {
        MvcResult res =
            mvc.perform(MockMvcRequestBuilders.get(new URI(path)).with(csrf()))
                .andExpect(status().is(expectedStatus.value()))
                .andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().getContentType())
                .as("problem description instead of result")
                .isEqualTo("application/problem+json"); // TODO MJ more sufficient check?
            return null;
        }

        return mapper.readValue(
            res.getResponse().getContentAsString(),
            mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }


    public void delete(String path, HttpStatus expectedStatus) throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete(new URI(path)).with(csrf()))
            .andExpect(status().is(expectedStatus.value()))
            .andReturn();
    }
}
