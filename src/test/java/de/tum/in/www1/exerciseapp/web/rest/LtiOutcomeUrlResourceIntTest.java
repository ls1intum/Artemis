package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.LtiOutcomeUrl;
import de.tum.in.www1.exerciseapp.repository.LtiOutcomeUrlRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.hamcrest.Matchers.hasItem;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Test class for the LtiOutcomeUrlResource REST controller.
 *
 * @see LtiOutcomeUrlResource
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ExerciseApplicationApp.class)
@WebAppConfiguration
@IntegrationTest
public class LtiOutcomeUrlResourceIntTest {

    private static final String DEFAULT_URL = "AAAAA";
    private static final String UPDATED_URL = "BBBBB";
    private static final String DEFAULT_SOURCED_ID = "AAAAA";
    private static final String UPDATED_SOURCED_ID = "BBBBB";

    @Inject
    private LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Inject
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Inject
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    private MockMvc restLtiOutcomeUrlMockMvc;

    private LtiOutcomeUrl ltiOutcomeUrl;

    @PostConstruct
    public void setup() {
        MockitoAnnotations.initMocks(this);
        LtiOutcomeUrlResource ltiOutcomeUrlResource = new LtiOutcomeUrlResource();
        ReflectionTestUtils.setField(ltiOutcomeUrlResource, "ltiOutcomeUrlRepository", ltiOutcomeUrlRepository);
        this.restLtiOutcomeUrlMockMvc = MockMvcBuilders.standaloneSetup(ltiOutcomeUrlResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setMessageConverters(jacksonMessageConverter).build();
    }

    @Before
    public void initTest() {
        ltiOutcomeUrl = new LtiOutcomeUrl();
        ltiOutcomeUrl.setUrl(DEFAULT_URL);
        ltiOutcomeUrl.setSourcedId(DEFAULT_SOURCED_ID);
    }

    @Test
    @Transactional
    public void createLtiOutcomeUrl() throws Exception {
        int databaseSizeBeforeCreate = ltiOutcomeUrlRepository.findAll().size();

        // Create the LtiOutcomeUrl

        restLtiOutcomeUrlMockMvc.perform(post("/api/lti-outcome-urls")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(TestUtil.convertObjectToJsonBytes(ltiOutcomeUrl)))
                .andExpect(status().isCreated());

        // Validate the LtiOutcomeUrl in the database
        List<LtiOutcomeUrl> ltiOutcomeUrls = ltiOutcomeUrlRepository.findAll();
        assertThat(ltiOutcomeUrls).hasSize(databaseSizeBeforeCreate + 1);
        LtiOutcomeUrl testLtiOutcomeUrl = ltiOutcomeUrls.get(ltiOutcomeUrls.size() - 1);
        assertThat(testLtiOutcomeUrl.getUrl()).isEqualTo(DEFAULT_URL);
        assertThat(testLtiOutcomeUrl.getSourcedId()).isEqualTo(DEFAULT_SOURCED_ID);
    }

    @Test
    @Transactional
    public void getAllLtiOutcomeUrls() throws Exception {
        // Initialize the database
        ltiOutcomeUrlRepository.saveAndFlush(ltiOutcomeUrl);

        // Get all the ltiOutcomeUrls
        restLtiOutcomeUrlMockMvc.perform(get("/api/lti-outcome-urls?sort=id,desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.[*].id").value(hasItem(ltiOutcomeUrl.getId().intValue())))
                .andExpect(jsonPath("$.[*].url").value(hasItem(DEFAULT_URL.toString())))
                .andExpect(jsonPath("$.[*].sourcedId").value(hasItem(DEFAULT_SOURCED_ID.toString())));
    }

    @Test
    @Transactional
    public void getLtiOutcomeUrl() throws Exception {
        // Initialize the database
        ltiOutcomeUrlRepository.saveAndFlush(ltiOutcomeUrl);

        // Get the ltiOutcomeUrl
        restLtiOutcomeUrlMockMvc.perform(get("/api/lti-outcome-urls/{id}", ltiOutcomeUrl.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(ltiOutcomeUrl.getId().intValue()))
            .andExpect(jsonPath("$.url").value(DEFAULT_URL.toString()))
            .andExpect(jsonPath("$.sourcedId").value(DEFAULT_SOURCED_ID.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingLtiOutcomeUrl() throws Exception {
        // Get the ltiOutcomeUrl
        restLtiOutcomeUrlMockMvc.perform(get("/api/lti-outcome-urls/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateLtiOutcomeUrl() throws Exception {
        // Initialize the database
        ltiOutcomeUrlRepository.saveAndFlush(ltiOutcomeUrl);
        int databaseSizeBeforeUpdate = ltiOutcomeUrlRepository.findAll().size();

        // Update the ltiOutcomeUrl
        LtiOutcomeUrl updatedLtiOutcomeUrl = new LtiOutcomeUrl();
        updatedLtiOutcomeUrl.setId(ltiOutcomeUrl.getId());
        updatedLtiOutcomeUrl.setUrl(UPDATED_URL);
        updatedLtiOutcomeUrl.setSourcedId(UPDATED_SOURCED_ID);

        restLtiOutcomeUrlMockMvc.perform(put("/api/lti-outcome-urls")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(TestUtil.convertObjectToJsonBytes(updatedLtiOutcomeUrl)))
                .andExpect(status().isOk());

        // Validate the LtiOutcomeUrl in the database
        List<LtiOutcomeUrl> ltiOutcomeUrls = ltiOutcomeUrlRepository.findAll();
        assertThat(ltiOutcomeUrls).hasSize(databaseSizeBeforeUpdate);
        LtiOutcomeUrl testLtiOutcomeUrl = ltiOutcomeUrls.get(ltiOutcomeUrls.size() - 1);
        assertThat(testLtiOutcomeUrl.getUrl()).isEqualTo(UPDATED_URL);
        assertThat(testLtiOutcomeUrl.getSourcedId()).isEqualTo(UPDATED_SOURCED_ID);
    }

    @Test
    @Transactional
    public void deleteLtiOutcomeUrl() throws Exception {
        // Initialize the database
        ltiOutcomeUrlRepository.saveAndFlush(ltiOutcomeUrl);
        int databaseSizeBeforeDelete = ltiOutcomeUrlRepository.findAll().size();

        // Get the ltiOutcomeUrl
        restLtiOutcomeUrlMockMvc.perform(delete("/api/lti-outcome-urls/{id}", ltiOutcomeUrl.getId())
                .accept(TestUtil.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());

        // Validate the database is empty
        List<LtiOutcomeUrl> ltiOutcomeUrls = ltiOutcomeUrlRepository.findAll();
        assertThat(ltiOutcomeUrls).hasSize(databaseSizeBeforeDelete - 1);
    }
}
