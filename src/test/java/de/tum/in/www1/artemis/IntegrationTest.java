package de.tum.in.www1.artemis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.artemis.domain.Course;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class IntegrationTest {

    @Autowired
    ObjectMapper mapper;

    @Autowired
    public MockMvc mvc;


    @Test
    @WithMockUser(roles = "ADMIN")
    public void test() throws Exception {
        Course course = new Course(null, "DASD", "", "abcd", "tumuser", "", "tumuser", null, null, true, 5, new HashSet<>());
        postRequest("/api/courses", course, HttpStatus.CREATED);
    }

    private <T> URI postRequest(String path, T body, HttpStatus status) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        MvcResult res = mvc.perform(post(new URI(path))
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


    private <T> void putRequest(String path, T body, HttpStatus status) throws Exception {
        String jsonBody = mapper.writeValueAsString(body);
        mvc.perform(put(new URI(path))
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody))
            .andExpect(status().is(status.value()));
    }


    private <T> T getRequest(String path, HttpStatus status, Class<T> responseType) throws Exception {
        MvcResult res = mvc.perform(get(new URI(path)))
            .andExpect(status().is(status.value()))
            .andReturn();
        if (status != HttpStatus.OK) {
            assertThat(res.getResponse().getContentAsString()).isEqualTo("");
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), responseType);
    }


    private <T> List<T> getListRequest(String path, HttpStatus status, Class<T> listElementType) throws Exception {
        MvcResult res = mvc.perform(get(new URI(path)))
            .andExpect(status().is(status.value()))
            .andReturn();
        if (status != HttpStatus.OK) {
            assertThat(res.getResponse().getContentAsString()).isEqualTo("");
            return null;
        }

        return mapper.readValue(res.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, listElementType));
    }


    private void deleteRequest(String path, HttpStatus status) throws Exception {
        mvc.perform(delete(new URI(path)))
            .andExpect(status().is(status.value()))
            .andReturn();
    }


}
