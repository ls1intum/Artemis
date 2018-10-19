package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
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

import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
/**
 * Test class for the ModelingExerciseResource REST controller.
 *
 * @see ModelingExerciseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class ModelingExerciseResourceIntTest {

    private static final DiagramType DEFAULT_DIAGRAM_TYPE = DiagramType.CLASS;
    private static final DiagramType UPDATED_DIAGRAM_TYPE = DiagramType.ACTIVITY;

    private static final String DEFAULT_SAMPLE_SOLUTION_MODEL = "AAAAAAAAAA";
    private static final String UPDATED_SAMPLE_SOLUTION_MODEL = "BBBBBBBBBB";

    private static final String DEFAULT_SAMPLE_SOLUTION_EXPLANATION = "AAAAAAAAAA";
    private static final String UPDATED_SAMPLE_SOLUTION_EXPLANATION = "BBBBBBBBBB";

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
            .setConversionService(createFormattingConversionService())
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
            .diagramType(DEFAULT_DIAGRAM_TYPE)
            .sampleSolutionModel(DEFAULT_SAMPLE_SOLUTION_MODEL)
            .sampleSolutionExplanation(DEFAULT_SAMPLE_SOLUTION_EXPLANATION);
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
        assertThat(testModelingExercise.getDiagramType()).isEqualTo(DEFAULT_DIAGRAM_TYPE);
        assertThat(testModelingExercise.getSampleSolutionModel()).isEqualTo(DEFAULT_SAMPLE_SOLUTION_MODEL);
        assertThat(testModelingExercise.getSampleSolutionExplanation()).isEqualTo(DEFAULT_SAMPLE_SOLUTION_EXPLANATION);
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

        // Validate the ModelingExercise in the database
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
            .andExpect(jsonPath("$.[*].diagramType").value(hasItem(DEFAULT_DIAGRAM_TYPE.toString())))
            .andExpect(jsonPath("$.[*].sampleSolutionModel").value(hasItem(DEFAULT_SAMPLE_SOLUTION_MODEL.toString())))
            .andExpect(jsonPath("$.[*].sampleSolutionExplanation").value(hasItem(DEFAULT_SAMPLE_SOLUTION_EXPLANATION.toString())));
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
            .andExpect(jsonPath("$.diagramType").value(DEFAULT_DIAGRAM_TYPE.toString()))
            .andExpect(jsonPath("$.sampleSolutionModel").value(DEFAULT_SAMPLE_SOLUTION_MODEL.toString()))
            .andExpect(jsonPath("$.sampleSolutionExplanation").value(DEFAULT_SAMPLE_SOLUTION_EXPLANATION.toString()));
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
        ModelingExercise updatedModelingExercise = modelingExerciseRepository.findById(modelingExercise.getId()).get();
        // Disconnect from session so that the updates on updatedModelingExercise are not directly saved in db
        em.detach(updatedModelingExercise);
        updatedModelingExercise
            .diagramType(UPDATED_DIAGRAM_TYPE)
            .sampleSolutionModel(UPDATED_SAMPLE_SOLUTION_MODEL)
            .sampleSolutionExplanation(UPDATED_SAMPLE_SOLUTION_EXPLANATION);

        restModelingExerciseMockMvc.perform(put("/api/modeling-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedModelingExercise)))
            .andExpect(status().isOk());

        // Validate the ModelingExercise in the database
        List<ModelingExercise> modelingExerciseList = modelingExerciseRepository.findAll();
        assertThat(modelingExerciseList).hasSize(databaseSizeBeforeUpdate);
        ModelingExercise testModelingExercise = modelingExerciseList.get(modelingExerciseList.size() - 1);
        assertThat(testModelingExercise.getDiagramType()).isEqualTo(UPDATED_DIAGRAM_TYPE);
        assertThat(testModelingExercise.getSampleSolutionModel()).isEqualTo(UPDATED_SAMPLE_SOLUTION_MODEL);
        assertThat(testModelingExercise.getSampleSolutionExplanation()).isEqualTo(UPDATED_SAMPLE_SOLUTION_EXPLANATION);
    }

    @Test
    @Transactional
    public void updateNonExistingModelingExercise() throws Exception {
        int databaseSizeBeforeUpdate = modelingExerciseRepository.findAll().size();

        // Create the ModelingExercise

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restModelingExerciseMockMvc.perform(put("/api/modeling-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(modelingExercise)))
            .andExpect(status().isBadRequest());

        // Validate the ModelingExercise in the database
        List<ModelingExercise> modelingExerciseList = modelingExerciseRepository.findAll();
        assertThat(modelingExerciseList).hasSize(databaseSizeBeforeUpdate);
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
