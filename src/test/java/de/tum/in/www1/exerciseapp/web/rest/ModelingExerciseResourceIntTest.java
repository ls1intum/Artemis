package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.ModelingExercise;
import de.tum.in.www1.exerciseapp.repository.ModelingExerciseRepository;
import de.tum.in.www1.exerciseapp.web.rest.errors.ExceptionTranslator;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the ModelingExerciseResource REST controller.
 *
 * @see ModelingExerciseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class ModelingExerciseResourceIntTest {

    private static final String DEFAULT_BASE_FILE_PATH = "AAAAAAAAAA";
    private static final String UPDATED_BASE_FILE_PATH = "BBBBBBBBBB";

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restModelingExerciseMockMvc;

    private ModelingExercise modelingExercise;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ModelingExerciseResource modelingExerciseResource = new ModelingExerciseResource(modelingExerciseRepository);
        this.restModelingExerciseMockMvc = MockMvcBuilders.standaloneSetup(modelingExerciseResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setMessageConverters(jacksonMessageConverter).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ModelingExercise createEntity(EntityManager em) {
        ModelingExercise modelingExercise = new ModelingExercise()
            .baseFilePath(DEFAULT_BASE_FILE_PATH);
        return modelingExercise;
    }

    @Before
    public void initTest() {
        modelingExercise = createEntity(em);
    }

    @Test
    @Transactional
    public void createModelingExercise() throws Exception {
        int databaseSizeBeforeCreate = modelingExerciseRepository.findAll().size();

        // Create the ModelingExercise
        restModelingExerciseMockMvc.perform(post("/api/modeling-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(modelingExercise)))
            .andExpect(status().isCreated());

        // Validate the ModelingExercise in the database
        List<ModelingExercise> modelingExerciseList = modelingExerciseRepository.findAll();
        assertThat(modelingExerciseList).hasSize(databaseSizeBeforeCreate + 1);
        ModelingExercise testModelingExercise = modelingExerciseList.get(modelingExerciseList.size() - 1);
        assertThat(testModelingExercise.getBaseFilePath()).isEqualTo(DEFAULT_BASE_FILE_PATH);
    }

    @Test
    @Transactional
    public void createModelingExerciseWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = modelingExerciseRepository.findAll().size();

        // Create the ModelingExercise with an existing ID
        modelingExercise.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restModelingExerciseMockMvc.perform(post("/api/modeling-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(modelingExercise)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<ModelingExercise> modelingExerciseList = modelingExerciseRepository.findAll();
        assertThat(modelingExerciseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllModelingExercises() throws Exception {
        // Initialize the database
        modelingExerciseRepository.saveAndFlush(modelingExercise);

        // Get all the modelingExerciseList
        restModelingExerciseMockMvc.perform(get("/api/modeling-exercises?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(modelingExercise.getId().intValue())))
            .andExpect(jsonPath("$.[*].baseFilePath").value(hasItem(DEFAULT_BASE_FILE_PATH.toString())));
    }

    @Test
    @Transactional
    public void getModelingExercise() throws Exception {
        // Initialize the database
        modelingExerciseRepository.saveAndFlush(modelingExercise);

        // Get the modelingExercise
        restModelingExerciseMockMvc.perform(get("/api/modeling-exercises/{id}", modelingExercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(modelingExercise.getId().intValue()))
            .andExpect(jsonPath("$.baseFilePath").value(DEFAULT_BASE_FILE_PATH.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingModelingExercise() throws Exception {
        // Get the modelingExercise
        restModelingExerciseMockMvc.perform(get("/api/modeling-exercises/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateModelingExercise() throws Exception {
        // Initialize the database
        modelingExerciseRepository.saveAndFlush(modelingExercise);
        int databaseSizeBeforeUpdate = modelingExerciseRepository.findAll().size();

        // Update the modelingExercise
        ModelingExercise updatedModelingExercise = modelingExerciseRepository.findOne(modelingExercise.getId());
        updatedModelingExercise
            .baseFilePath(UPDATED_BASE_FILE_PATH);

        restModelingExerciseMockMvc.perform(put("/api/modeling-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedModelingExercise)))
            .andExpect(status().isOk());

        // Validate the ModelingExercise in the database
        List<ModelingExercise> modelingExerciseList = modelingExerciseRepository.findAll();
        assertThat(modelingExerciseList).hasSize(databaseSizeBeforeUpdate);
        ModelingExercise testModelingExercise = modelingExerciseList.get(modelingExerciseList.size() - 1);
        assertThat(testModelingExercise.getBaseFilePath()).isEqualTo(UPDATED_BASE_FILE_PATH);
    }

    @Test
    @Transactional
    public void updateNonExistingModelingExercise() throws Exception {
        int databaseSizeBeforeUpdate = modelingExerciseRepository.findAll().size();

        // Create the ModelingExercise

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restModelingExerciseMockMvc.perform(put("/api/modeling-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(modelingExercise)))
            .andExpect(status().isCreated());

        // Validate the ModelingExercise in the database
        List<ModelingExercise> modelingExerciseList = modelingExerciseRepository.findAll();
        assertThat(modelingExerciseList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteModelingExercise() throws Exception {
        // Initialize the database
        modelingExerciseRepository.saveAndFlush(modelingExercise);
        int databaseSizeBeforeDelete = modelingExerciseRepository.findAll().size();

        // Get the modelingExercise
        restModelingExerciseMockMvc.perform(delete("/api/modeling-exercises/{id}", modelingExercise.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<ModelingExercise> modelingExerciseList = modelingExerciseRepository.findAll();
        assertThat(modelingExerciseList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ModelingExercise.class);
        ModelingExercise modelingExercise1 = new ModelingExercise();
        modelingExercise1.setId(1L);
        ModelingExercise modelingExercise2 = new ModelingExercise();
        modelingExercise2.setId(modelingExercise1.getId());
        assertThat(modelingExercise1).isEqualTo(modelingExercise2);
        modelingExercise2.setId(2L);
        assertThat(modelingExercise1).isNotEqualTo(modelingExercise2);
        modelingExercise1.setId(null);
        assertThat(modelingExercise1).isNotEqualTo(modelingExercise2);
    }
}
