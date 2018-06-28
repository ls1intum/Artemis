package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
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
 * Test class for the ModelingSubmissionResource REST controller.
 *
 * @see ModelingSubmissionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class ModelingSubmissionResourceIntTest {

    private static final String DEFAULT_SUBMISSION_PATH = "AAAAAAAAAA";
    private static final String UPDATED_SUBMISSION_PATH = "BBBBBBBBBB";

    @Autowired
    private ModelingSubmissionRepository modelingSubmissionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restModelingSubmissionMockMvc;

    private ModelingSubmission modelingSubmission;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ModelingSubmissionResource modelingSubmissionResource = new ModelingSubmissionResource(modelingSubmissionRepository);
        this.restModelingSubmissionMockMvc = MockMvcBuilders.standaloneSetup(modelingSubmissionResource)
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
    public static ModelingSubmission createEntity(EntityManager em) {
        ModelingSubmission modelingSubmission = new ModelingSubmission()
            .submissionPath(DEFAULT_SUBMISSION_PATH);
        return modelingSubmission;
    }

    @Before
    public void initTest() {
        modelingSubmission = createEntity(em);
    }

    @Test
    @Transactional
    public void createModelingSubmission() throws Exception {
        int databaseSizeBeforeCreate = modelingSubmissionRepository.findAll().size();

        // Create the ModelingSubmission
        restModelingSubmissionMockMvc.perform(post("/api/modeling-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(modelingSubmission)))
            .andExpect(status().isCreated());

        // Validate the ModelingSubmission in the database
        List<ModelingSubmission> modelingSubmissionList = modelingSubmissionRepository.findAll();
        assertThat(modelingSubmissionList).hasSize(databaseSizeBeforeCreate + 1);
        ModelingSubmission testModelingSubmission = modelingSubmissionList.get(modelingSubmissionList.size() - 1);
        assertThat(testModelingSubmission.getSubmissionPath()).isEqualTo(DEFAULT_SUBMISSION_PATH);
    }

    @Test
    @Transactional
    public void createModelingSubmissionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = modelingSubmissionRepository.findAll().size();

        // Create the ModelingSubmission with an existing ID
        modelingSubmission.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restModelingSubmissionMockMvc.perform(post("/api/modeling-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(modelingSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the ModelingSubmission in the database
        List<ModelingSubmission> modelingSubmissionList = modelingSubmissionRepository.findAll();
        assertThat(modelingSubmissionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllModelingSubmissions() throws Exception {
        // Initialize the database
        modelingSubmissionRepository.saveAndFlush(modelingSubmission);

        // Get all the modelingSubmissionList
        restModelingSubmissionMockMvc.perform(get("/api/modeling-submissions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(modelingSubmission.getId().intValue())))
            .andExpect(jsonPath("$.[*].submissionPath").value(hasItem(DEFAULT_SUBMISSION_PATH.toString())));
    }

    @Test
    @Transactional
    public void getModelingSubmission() throws Exception {
        // Initialize the database
        modelingSubmissionRepository.saveAndFlush(modelingSubmission);

        // Get the modelingSubmission
        restModelingSubmissionMockMvc.perform(get("/api/modeling-submissions/{id}", modelingSubmission.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(modelingSubmission.getId().intValue()))
            .andExpect(jsonPath("$.submissionPath").value(DEFAULT_SUBMISSION_PATH.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingModelingSubmission() throws Exception {
        // Get the modelingSubmission
        restModelingSubmissionMockMvc.perform(get("/api/modeling-submissions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateModelingSubmission() throws Exception {
        // Initialize the database
        modelingSubmissionRepository.saveAndFlush(modelingSubmission);
        int databaseSizeBeforeUpdate = modelingSubmissionRepository.findAll().size();

        // Update the modelingSubmission
        ModelingSubmission updatedModelingSubmission = modelingSubmissionRepository.findOne(modelingSubmission.getId());
        // Disconnect from session so that the updates on updatedModelingSubmission are not directly saved in db
        em.detach(updatedModelingSubmission);
        updatedModelingSubmission
            .submissionPath(UPDATED_SUBMISSION_PATH);

        restModelingSubmissionMockMvc.perform(put("/api/modeling-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedModelingSubmission)))
            .andExpect(status().isOk());

        // Validate the ModelingSubmission in the database
        List<ModelingSubmission> modelingSubmissionList = modelingSubmissionRepository.findAll();
        assertThat(modelingSubmissionList).hasSize(databaseSizeBeforeUpdate);
        ModelingSubmission testModelingSubmission = modelingSubmissionList.get(modelingSubmissionList.size() - 1);
        assertThat(testModelingSubmission.getSubmissionPath()).isEqualTo(UPDATED_SUBMISSION_PATH);
    }

    @Test
    @Transactional
    public void updateNonExistingModelingSubmission() throws Exception {
        int databaseSizeBeforeUpdate = modelingSubmissionRepository.findAll().size();

        // Create the ModelingSubmission

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restModelingSubmissionMockMvc.perform(put("/api/modeling-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(modelingSubmission)))
            .andExpect(status().isCreated());

        // Validate the ModelingSubmission in the database
        List<ModelingSubmission> modelingSubmissionList = modelingSubmissionRepository.findAll();
        assertThat(modelingSubmissionList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteModelingSubmission() throws Exception {
        // Initialize the database
        modelingSubmissionRepository.saveAndFlush(modelingSubmission);
        int databaseSizeBeforeDelete = modelingSubmissionRepository.findAll().size();

        // Get the modelingSubmission
        restModelingSubmissionMockMvc.perform(delete("/api/modeling-submissions/{id}", modelingSubmission.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<ModelingSubmission> modelingSubmissionList = modelingSubmissionRepository.findAll();
        assertThat(modelingSubmissionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ModelingSubmission.class);
        ModelingSubmission modelingSubmission1 = new ModelingSubmission();
        modelingSubmission1.setId(1L);
        ModelingSubmission modelingSubmission2 = new ModelingSubmission();
        modelingSubmission2.setId(modelingSubmission1.getId());
        assertThat(modelingSubmission1).isEqualTo(modelingSubmission2);
        modelingSubmission2.setId(2L);
        assertThat(modelingSubmission1).isNotEqualTo(modelingSubmission2);
        modelingSubmission1.setId(null);
        assertThat(modelingSubmission1).isNotEqualTo(modelingSubmission2);
    }
}
