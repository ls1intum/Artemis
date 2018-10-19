package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.ApollonDiagram;
import de.tum.in.www1.artemis.repository.ApollonDiagramRepository;
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
 * Test class for the ApollonDiagramResource REST controller.
 *
 * @see ApollonDiagramResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class ApollonDiagramResourceIntTest {

    private static final String DEFAULT_TITLE = "AAAAAAAAAA";
    private static final String UPDATED_TITLE = "BBBBBBBBBB";

    private static final String DEFAULT_JSON_REPRESENTATION = "AAAAAAAAAA";
    private static final String UPDATED_JSON_REPRESENTATION = "BBBBBBBBBB";

    @Autowired
    private ApollonDiagramRepository apollonDiagramRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restApollonDiagramMockMvc;

    private ApollonDiagram apollonDiagram;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ApollonDiagramResource apollonDiagramResource = new ApollonDiagramResource(apollonDiagramRepository);
        this.restApollonDiagramMockMvc = MockMvcBuilders.standaloneSetup(apollonDiagramResource)
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
    public static ApollonDiagram createEntity(EntityManager em) {
        ApollonDiagram apollonDiagram = new ApollonDiagram()
            .title(DEFAULT_TITLE)
            .jsonRepresentation(DEFAULT_JSON_REPRESENTATION);
        return apollonDiagram;
    }

    @Before
    public void initTest() {
        apollonDiagram = createEntity(em);
    }

    @Test
    @Transactional
    public void createApollonDiagram() throws Exception {
        int databaseSizeBeforeCreate = apollonDiagramRepository.findAll().size();

        // Create the ApollonDiagram
        restApollonDiagramMockMvc.perform(post("/api/apollon-diagrams")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(apollonDiagram)))
            .andExpect(status().isCreated());

        // Validate the ApollonDiagram in the database
        List<ApollonDiagram> apollonDiagramList = apollonDiagramRepository.findAll();
        assertThat(apollonDiagramList).hasSize(databaseSizeBeforeCreate + 1);
        ApollonDiagram testApollonDiagram = apollonDiagramList.get(apollonDiagramList.size() - 1);
        assertThat(testApollonDiagram.getTitle()).isEqualTo(DEFAULT_TITLE);
        assertThat(testApollonDiagram.getJsonRepresentation()).isEqualTo(DEFAULT_JSON_REPRESENTATION);
    }

    @Test
    @Transactional
    public void createApollonDiagramWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = apollonDiagramRepository.findAll().size();

        // Create the ApollonDiagram with an existing ID
        apollonDiagram.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restApollonDiagramMockMvc.perform(post("/api/apollon-diagrams")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(apollonDiagram)))
            .andExpect(status().isBadRequest());

        // Validate the ApollonDiagram in the database
        List<ApollonDiagram> apollonDiagramList = apollonDiagramRepository.findAll();
        assertThat(apollonDiagramList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllApollonDiagrams() throws Exception {
        // Initialize the database
        apollonDiagramRepository.saveAndFlush(apollonDiagram);

        // Get all the apollonDiagramList
        restApollonDiagramMockMvc.perform(get("/api/apollon-diagrams?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(apollonDiagram.getId().intValue())))
            .andExpect(jsonPath("$.[*].title").value(hasItem(DEFAULT_TITLE.toString())))
            .andExpect(jsonPath("$.[*].jsonRepresentation").value(hasItem(DEFAULT_JSON_REPRESENTATION.toString())));
    }
    
    @Test
    @Transactional
    public void getApollonDiagram() throws Exception {
        // Initialize the database
        apollonDiagramRepository.saveAndFlush(apollonDiagram);

        // Get the apollonDiagram
        restApollonDiagramMockMvc.perform(get("/api/apollon-diagrams/{id}", apollonDiagram.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(apollonDiagram.getId().intValue()))
            .andExpect(jsonPath("$.title").value(DEFAULT_TITLE.toString()))
            .andExpect(jsonPath("$.jsonRepresentation").value(DEFAULT_JSON_REPRESENTATION.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingApollonDiagram() throws Exception {
        // Get the apollonDiagram
        restApollonDiagramMockMvc.perform(get("/api/apollon-diagrams/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateApollonDiagram() throws Exception {
        // Initialize the database
        apollonDiagramRepository.saveAndFlush(apollonDiagram);

        int databaseSizeBeforeUpdate = apollonDiagramRepository.findAll().size();

        // Update the apollonDiagram
        ApollonDiagram updatedApollonDiagram = apollonDiagramRepository.findById(apollonDiagram.getId()).get();
        // Disconnect from session so that the updates on updatedApollonDiagram are not directly saved in db
        em.detach(updatedApollonDiagram);
        updatedApollonDiagram
            .title(UPDATED_TITLE)
            .jsonRepresentation(UPDATED_JSON_REPRESENTATION);

        restApollonDiagramMockMvc.perform(put("/api/apollon-diagrams")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedApollonDiagram)))
            .andExpect(status().isOk());

        // Validate the ApollonDiagram in the database
        List<ApollonDiagram> apollonDiagramList = apollonDiagramRepository.findAll();
        assertThat(apollonDiagramList).hasSize(databaseSizeBeforeUpdate);
        ApollonDiagram testApollonDiagram = apollonDiagramList.get(apollonDiagramList.size() - 1);
        assertThat(testApollonDiagram.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testApollonDiagram.getJsonRepresentation()).isEqualTo(UPDATED_JSON_REPRESENTATION);
    }

    @Test
    @Transactional
    public void updateNonExistingApollonDiagram() throws Exception {
        int databaseSizeBeforeUpdate = apollonDiagramRepository.findAll().size();

        // Create the ApollonDiagram

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restApollonDiagramMockMvc.perform(put("/api/apollon-diagrams")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(apollonDiagram)))
            .andExpect(status().isBadRequest());

        // Validate the ApollonDiagram in the database
        List<ApollonDiagram> apollonDiagramList = apollonDiagramRepository.findAll();
        assertThat(apollonDiagramList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteApollonDiagram() throws Exception {
        // Initialize the database
        apollonDiagramRepository.saveAndFlush(apollonDiagram);

        int databaseSizeBeforeDelete = apollonDiagramRepository.findAll().size();

        // Get the apollonDiagram
        restApollonDiagramMockMvc.perform(delete("/api/apollon-diagrams/{id}", apollonDiagram.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<ApollonDiagram> apollonDiagramList = apollonDiagramRepository.findAll();
        assertThat(apollonDiagramList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ApollonDiagram.class);
        ApollonDiagram apollonDiagram1 = new ApollonDiagram();
        apollonDiagram1.setId(1L);
        ApollonDiagram apollonDiagram2 = new ApollonDiagram();
        apollonDiagram2.setId(apollonDiagram1.getId());
        assertThat(apollonDiagram1).isEqualTo(apollonDiagram2);
        apollonDiagram2.setId(2L);
        assertThat(apollonDiagram1).isNotEqualTo(apollonDiagram2);
        apollonDiagram1.setId(null);
        assertThat(apollonDiagram1).isNotEqualTo(apollonDiagram2);
    }
}
