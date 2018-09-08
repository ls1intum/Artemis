package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.LtiOutcomeUrl;
import de.tum.in.www1.artemis.repository.LtiOutcomeUrlRepository;
import de.tum.in.www1.artemis.web.rest.errors.ExceptionTranslator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;


import static de.tum.in.www1.artemis.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the LtiOutcomeUrlResource REST controller.
 *
 * @see LtiOutcomeUrlResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class LtiOutcomeUrlResourceIntTest {

    private static final String DEFAULT_URL = "AAAAAAAAAA";
    private static final String UPDATED_URL = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCED_ID = "AAAAAAAAAA";
    private static final String UPDATED_SOURCED_ID = "BBBBBBBBBB";

    @Autowired
    private LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restLtiOutcomeUrlMockMvc;

    private LtiOutcomeUrl ltiOutcomeUrl;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final LtiOutcomeUrlResource ltiOutcomeUrlResource = new LtiOutcomeUrlResource(ltiOutcomeUrlRepository);
        this.restLtiOutcomeUrlMockMvc = MockMvcBuilders.standaloneSetup(ltiOutcomeUrlResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setConversionService(createFormattingConversionService())
            .setMessageConverters(jacksonMessageConverter).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static LtiOutcomeUrl createEntity(EntityManager em) {
        LtiOutcomeUrl ltiOutcomeUrl = new LtiOutcomeUrl()
            .url(DEFAULT_URL)
            .sourcedId(DEFAULT_SOURCED_ID);
        return ltiOutcomeUrl;
    }

    @Before
    public void initTest() {
        ltiOutcomeUrl = createEntity(em);
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
        List<LtiOutcomeUrl> ltiOutcomeUrlList = ltiOutcomeUrlRepository.findAll();
        assertThat(ltiOutcomeUrlList).hasSize(databaseSizeBeforeCreate + 1);
        LtiOutcomeUrl testLtiOutcomeUrl = ltiOutcomeUrlList.get(ltiOutcomeUrlList.size() - 1);
        assertThat(testLtiOutcomeUrl.getUrl()).isEqualTo(DEFAULT_URL);
        assertThat(testLtiOutcomeUrl.getSourcedId()).isEqualTo(DEFAULT_SOURCED_ID);
    }

    @Test
    @Transactional
    public void createLtiOutcomeUrlWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = ltiOutcomeUrlRepository.findAll().size();

        // Create the LtiOutcomeUrl with an existing ID
        ltiOutcomeUrl.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restLtiOutcomeUrlMockMvc.perform(post("/api/lti-outcome-urls")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(ltiOutcomeUrl)))
            .andExpect(status().isBadRequest());

        // Validate the LtiOutcomeUrl in the database
        List<LtiOutcomeUrl> ltiOutcomeUrlList = ltiOutcomeUrlRepository.findAll();
        assertThat(ltiOutcomeUrlList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllLtiOutcomeUrls() throws Exception {
        // Initialize the database
        ltiOutcomeUrlRepository.saveAndFlush(ltiOutcomeUrl);

        // Get all the ltiOutcomeUrlList
        restLtiOutcomeUrlMockMvc.perform(get("/api/lti-outcome-urls?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
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
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
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
        LtiOutcomeUrl updatedLtiOutcomeUrl = ltiOutcomeUrlRepository.findById(ltiOutcomeUrl.getId()).get();
        // Disconnect from session so that the updates on updatedLtiOutcomeUrl are not directly saved in db
        em.detach(updatedLtiOutcomeUrl);
        updatedLtiOutcomeUrl
            .url(UPDATED_URL)
            .sourcedId(UPDATED_SOURCED_ID);

        restLtiOutcomeUrlMockMvc.perform(put("/api/lti-outcome-urls")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedLtiOutcomeUrl)))
            .andExpect(status().isOk());

        // Validate the LtiOutcomeUrl in the database
        List<LtiOutcomeUrl> ltiOutcomeUrlList = ltiOutcomeUrlRepository.findAll();
        assertThat(ltiOutcomeUrlList).hasSize(databaseSizeBeforeUpdate);
        LtiOutcomeUrl testLtiOutcomeUrl = ltiOutcomeUrlList.get(ltiOutcomeUrlList.size() - 1);
        assertThat(testLtiOutcomeUrl.getUrl()).isEqualTo(UPDATED_URL);
        assertThat(testLtiOutcomeUrl.getSourcedId()).isEqualTo(UPDATED_SOURCED_ID);
    }

    @Test
    @Transactional
    public void updateNonExistingLtiOutcomeUrl() throws Exception {
        int databaseSizeBeforeUpdate = ltiOutcomeUrlRepository.findAll().size();

        // Create the LtiOutcomeUrl

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restLtiOutcomeUrlMockMvc.perform(put("/api/lti-outcome-urls")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(ltiOutcomeUrl)))
            .andExpect(status().isBadRequest());

        // Validate the LtiOutcomeUrl in the database
        List<LtiOutcomeUrl> ltiOutcomeUrlList = ltiOutcomeUrlRepository.findAll();
        assertThat(ltiOutcomeUrlList).hasSize(databaseSizeBeforeUpdate);
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
        List<LtiOutcomeUrl> ltiOutcomeUrlList = ltiOutcomeUrlRepository.findAll();
        assertThat(ltiOutcomeUrlList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(LtiOutcomeUrl.class);
        LtiOutcomeUrl ltiOutcomeUrl1 = new LtiOutcomeUrl();
        ltiOutcomeUrl1.setId(1L);
        LtiOutcomeUrl ltiOutcomeUrl2 = new LtiOutcomeUrl();
        ltiOutcomeUrl2.setId(ltiOutcomeUrl1.getId());
        assertThat(ltiOutcomeUrl1).isEqualTo(ltiOutcomeUrl2);
        ltiOutcomeUrl2.setId(2L);
        assertThat(ltiOutcomeUrl1).isNotEqualTo(ltiOutcomeUrl2);
        ltiOutcomeUrl1.setId(null);
        assertThat(ltiOutcomeUrl1).isNotEqualTo(ltiOutcomeUrl2);
    }
}
