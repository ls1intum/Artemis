package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.ProgrammingExercise;
import de.tum.in.www1.exerciseapp.repository.ProgrammingExerciseRepository;
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
 * Test class for the ProgrammingExerciseResource REST controller.
 *
 * @see ProgrammingExerciseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class ProgrammingExerciseResourceIntTest {

    private static final String DEFAULT_BASE_REPOSITORY_URL = "AAAAAAAAAA";
    private static final String UPDATED_BASE_REPOSITORY_URL = "BBBBBBBBBB";

    private static final String DEFAULT_BASE_BUILD_PLAN_ID = "AAAAAAAAAA";
    private static final String UPDATED_BASE_BUILD_PLAN_ID = "BBBBBBBBBB";

    private static final Boolean DEFAULT_PUBLISH_BUILD_PLAN_URL = false;
    private static final Boolean UPDATED_PUBLISH_BUILD_PLAN_URL = true;

    private static final Boolean DEFAULT_ALLOW_ONLINE_EDITOR = false;
    private static final Boolean UPDATED_ALLOW_ONLINE_EDITOR = true;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restProgrammingExerciseMockMvc;

    private ProgrammingExercise programmingExercise;

    private final ProgrammingExerciseResource programmingExerciseResource;

    public ProgrammingExerciseResourceIntTest(ProgrammingExerciseResource programmingExerciseResource) {
        this.programmingExerciseResource = programmingExerciseResource;
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.restProgrammingExerciseMockMvc = MockMvcBuilders.standaloneSetup(programmingExerciseResource)
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
    public static ProgrammingExercise createEntity(EntityManager em) {
        ProgrammingExercise programmingExercise = new ProgrammingExercise()
            .baseRepositoryUrl(DEFAULT_BASE_REPOSITORY_URL)
            .baseBuildPlanId(DEFAULT_BASE_BUILD_PLAN_ID)
            .publishBuildPlanUrl(DEFAULT_PUBLISH_BUILD_PLAN_URL)
            .allowOnlineEditor(DEFAULT_ALLOW_ONLINE_EDITOR);
        return programmingExercise;
    }

    @Before
    public void initTest() {
        programmingExercise = createEntity(em);
    }

    @Test
    @Transactional
    public void createProgrammingExercise() throws Exception {
        int databaseSizeBeforeCreate = programmingExerciseRepository.findAll().size();

        // Create the ProgrammingExercise
        restProgrammingExerciseMockMvc.perform(post("/api/programming-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(programmingExercise)))
            .andExpect(status().isCreated());

        // Validate the ProgrammingExercise in the database
        List<ProgrammingExercise> programmingExerciseList = programmingExerciseRepository.findAll();
        assertThat(programmingExerciseList).hasSize(databaseSizeBeforeCreate + 1);
        ProgrammingExercise testProgrammingExercise = programmingExerciseList.get(programmingExerciseList.size() - 1);
        assertThat(testProgrammingExercise.getBaseRepositoryUrl()).isEqualTo(DEFAULT_BASE_REPOSITORY_URL);
        assertThat(testProgrammingExercise.getBaseBuildPlanId()).isEqualTo(DEFAULT_BASE_BUILD_PLAN_ID);
        assertThat(testProgrammingExercise.isPublishBuildPlanUrl()).isEqualTo(DEFAULT_PUBLISH_BUILD_PLAN_URL);
        assertThat(testProgrammingExercise.isAllowOnlineEditor()).isEqualTo(DEFAULT_ALLOW_ONLINE_EDITOR);
    }

    @Test
    @Transactional
    public void createProgrammingExerciseWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = programmingExerciseRepository.findAll().size();

        // Create the ProgrammingExercise with an existing ID
        programmingExercise.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restProgrammingExerciseMockMvc.perform(post("/api/programming-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(programmingExercise)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<ProgrammingExercise> programmingExerciseList = programmingExerciseRepository.findAll();
        assertThat(programmingExerciseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllProgrammingExercises() throws Exception {
        // Initialize the database
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        // Get all the programmingExerciseList
        restProgrammingExerciseMockMvc.perform(get("/api/programming-exercises?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(programmingExercise.getId().intValue())))
            .andExpect(jsonPath("$.[*].baseRepositoryUrl").value(hasItem(DEFAULT_BASE_REPOSITORY_URL.toString())))
            .andExpect(jsonPath("$.[*].baseBuildPlanId").value(hasItem(DEFAULT_BASE_BUILD_PLAN_ID.toString())))
            .andExpect(jsonPath("$.[*].publishBuildPlanUrl").value(hasItem(DEFAULT_PUBLISH_BUILD_PLAN_URL.booleanValue())))
            .andExpect(jsonPath("$.[*].allowOnlineEditor").value(hasItem(DEFAULT_ALLOW_ONLINE_EDITOR.booleanValue())));
    }

    @Test
    @Transactional
    public void getProgrammingExercise() throws Exception {
        // Initialize the database
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        // Get the programmingExercise
        restProgrammingExerciseMockMvc.perform(get("/api/programming-exercises/{id}", programmingExercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(programmingExercise.getId().intValue()))
            .andExpect(jsonPath("$.baseRepositoryUrl").value(DEFAULT_BASE_REPOSITORY_URL.toString()))
            .andExpect(jsonPath("$.baseBuildPlanId").value(DEFAULT_BASE_BUILD_PLAN_ID.toString()))
            .andExpect(jsonPath("$.publishBuildPlanUrl").value(DEFAULT_PUBLISH_BUILD_PLAN_URL.booleanValue()))
            .andExpect(jsonPath("$.allowOnlineEditor").value(DEFAULT_ALLOW_ONLINE_EDITOR.booleanValue()));
    }

    @Test
    @Transactional
    public void getNonExistingProgrammingExercise() throws Exception {
        // Get the programmingExercise
        restProgrammingExerciseMockMvc.perform(get("/api/programming-exercises/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateProgrammingExercise() throws Exception {
        // Initialize the database
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        int databaseSizeBeforeUpdate = programmingExerciseRepository.findAll().size();

        // Update the programmingExercise
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.findOne(programmingExercise.getId());
        updatedProgrammingExercise
            .baseRepositoryUrl(UPDATED_BASE_REPOSITORY_URL)
            .baseBuildPlanId(UPDATED_BASE_BUILD_PLAN_ID)
            .publishBuildPlanUrl(UPDATED_PUBLISH_BUILD_PLAN_URL)
            .allowOnlineEditor(UPDATED_ALLOW_ONLINE_EDITOR);

        restProgrammingExerciseMockMvc.perform(put("/api/programming-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedProgrammingExercise)))
            .andExpect(status().isOk());

        // Validate the ProgrammingExercise in the database
        List<ProgrammingExercise> programmingExerciseList = programmingExerciseRepository.findAll();
        assertThat(programmingExerciseList).hasSize(databaseSizeBeforeUpdate);
        ProgrammingExercise testProgrammingExercise = programmingExerciseList.get(programmingExerciseList.size() - 1);
        assertThat(testProgrammingExercise.getBaseRepositoryUrl()).isEqualTo(UPDATED_BASE_REPOSITORY_URL);
        assertThat(testProgrammingExercise.getBaseBuildPlanId()).isEqualTo(UPDATED_BASE_BUILD_PLAN_ID);
        assertThat(testProgrammingExercise.isPublishBuildPlanUrl()).isEqualTo(UPDATED_PUBLISH_BUILD_PLAN_URL);
        assertThat(testProgrammingExercise.isAllowOnlineEditor()).isEqualTo(UPDATED_ALLOW_ONLINE_EDITOR);
    }

    @Test
    @Transactional
    public void updateNonExistingProgrammingExercise() throws Exception {
        int databaseSizeBeforeUpdate = programmingExerciseRepository.findAll().size();

        // Create the ProgrammingExercise

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restProgrammingExerciseMockMvc.perform(put("/api/programming-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(programmingExercise)))
            .andExpect(status().isCreated());

        // Validate the ProgrammingExercise in the database
        List<ProgrammingExercise> programmingExerciseList = programmingExerciseRepository.findAll();
        assertThat(programmingExerciseList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteProgrammingExercise() throws Exception {
        // Initialize the database
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        int databaseSizeBeforeDelete = programmingExerciseRepository.findAll().size();

        // Get the programmingExercise
        restProgrammingExerciseMockMvc.perform(delete("/api/programming-exercises/{id}", programmingExercise.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<ProgrammingExercise> programmingExerciseList = programmingExerciseRepository.findAll();
        assertThat(programmingExerciseList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ProgrammingExercise.class);
        ProgrammingExercise programmingExercise1 = new ProgrammingExercise();
        programmingExercise1.setId(1L);
        ProgrammingExercise programmingExercise2 = new ProgrammingExercise();
        programmingExercise2.setId(programmingExercise1.getId());
        assertThat(programmingExercise1).isEqualTo(programmingExercise2);
        programmingExercise2.setId(2L);
        assertThat(programmingExercise1).isNotEqualTo(programmingExercise2);
        programmingExercise1.setId(null);
        assertThat(programmingExercise1).isNotEqualTo(programmingExercise2);
    }
}
