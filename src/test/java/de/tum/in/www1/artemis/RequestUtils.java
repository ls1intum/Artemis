package de.tum.in.www1.artemis;

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
public class RequestUtils {
    private MockMvc mvc;
    private ObjectMapper mapper;

    public RequestUtils(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    public <T> URI post(String path, T body, HttpStatus status) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post(new URI(path))
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody)
            .with(csrf()))
            .andExpect(status().is(status.value()))
            .andReturn();
        if (status != HttpStatus.CREATED) {
            assertThat(res.getResponse().containsHeader("location")).isFalse();
            return null;
        }
        assertThat(res.getResponse().containsHeader("location")).isTrue();
        return new URI(res.getResponse().getHeader("location"));
    }


    public <T> void put(String path, T body, HttpStatus status) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        mvc.perform(MockMvcRequestBuilders.put(new URI(path))
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody)
            .with(csrf()))
            .andExpect(status().is(status.value()));
    }


    public <T> T get(String path, HttpStatus status, Class<T> responseType) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).with(csrf()))
            .andExpect(status().is(status.value()))
            .andReturn();
        if (status != HttpStatus.OK) {
            assertThat(res.getResponse().getContentAsString()).isEqualTo("");
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }


    public <T> List<T> getList(String path, HttpStatus status, Class<T> listElementType) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).with(csrf()))
            .andExpect(status().is(status.value()))
            .andReturn();
        if (!status.is2xxSuccessful()) {
            assertThat(res.getResponse().getContentType()).isEqualTo("application/problem+json");
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }


    public void delete(String path, HttpStatus status) throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete(new URI(path)).with(csrf()))
            .andExpect(status().is(status.value()))
            .andReturn();
    }
}
