package de.tum.in.www1.artemis;

import java.net.URI;
import java.util.List;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
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


    public <T> URI post(String path, T body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post(new URI(path))
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody)
            .with(csrf()))
            .andExpect(status().is(expectedStatus.value()))
            .andReturn();
        if (expectedStatus != HttpStatus.CREATED) {
            assertThat(res.getResponse().containsHeader("location"))
                .as("location header set on POST request")
                .isFalse();
            return null;
        }
        assertThat(res.getResponse().containsHeader("location")).isTrue();
        return new URI(res.getResponse().getHeader("location"));
    }


    public <T> void put(String path, T body, HttpStatus expectedStatus) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        mvc.perform(MockMvcRequestBuilders.put(new URI(path))
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody)
            .with(csrf()))
            .andExpect(status().is(expectedStatus.value()));
    }


    public <T> T get(String path, HttpStatus expectedStatus, Class<T> responseType) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).with(csrf()))
            .andExpect(status().is(expectedStatus.value()))
            .andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().getContentAsString())
                .as("problem description instead of result")
                .isEqualTo("application/problem+json");//TODO MJ more sufficient check?
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }


    public <T> List<T> getList(String path, HttpStatus expectedStatus, Class<T> listElementType) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(path)).with(csrf()))
            .andExpect(status().is(expectedStatus.value()))
            .andReturn();
        if (!expectedStatus.is2xxSuccessful()) {
            assertThat(res.getResponse().getContentType())
                .as("problem description instead of result")
                .isEqualTo("application/problem+json");//TODO MJ more sufficient check?
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }


    public void delete(String path, HttpStatus expectedStatus) throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete(new URI(path)).with(csrf()))
            .andExpect(status().is(expectedStatus.value()))
            .andReturn();
    }
}
