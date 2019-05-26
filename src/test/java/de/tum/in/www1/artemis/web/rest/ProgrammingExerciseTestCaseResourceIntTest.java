package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import javax.persistence.EntityManager;

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
import org.springframework.validation.Validator;

import de.tum.in.www1.artemis.ArtemisApp;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.TestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.web.rest.errors.ExceptionTranslator;

/**
 * Test class for the ProgrammingExerciseTestCaseResource REST controller.
 *
 * @see ProgrammingExerciseTestCaseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArtemisApp.class)
public class ProgrammingExerciseTestCaseResourceIntTest {

    private static final String DEFAULT_FILE_NAME = "AAAAAAAAAA";

    private static final String UPDATED_FILE_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_TEST_NAME = "AAAAAAAAAA";

    private static final String UPDATED_TEST_NAME = "BBBBBBBBBB";

    private static final TestCaseType DEFAULT_TYPE = TestCaseType.STRUCTURAL;

    private static final TestCaseType UPDATED_TYPE = TestCaseType.BEHAVIOR;

    private static final Integer DEFAULT_WEIGHT = 1;

    private static final Integer UPDATED_WEIGHT = 2;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    @Autowired
    private Validator validator;

    private MockMvc restProgrammingExerciseTestCaseMockMvc;

    private ProgrammingExerciseTestCase programmingExerciseTestCase;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ProgrammingExerciseTestCaseResource programmingExerciseTestCaseResource = new ProgrammingExerciseTestCaseResource(programmingExerciseTestCaseRepository);
        this.restProgrammingExerciseTestCaseMockMvc = MockMvcBuilders.standaloneSetup(programmingExerciseTestCaseResource).setCustomArgumentResolvers(pageableArgumentResolver)
                .setControllerAdvice(exceptionTranslator).setConversionService(createFormattingConversionService()).setMessageConverters(jacksonMessageConverter)
                .setValidator(validator).build();
    }

    /**
     * Create an entity for this test. This is a static method, as tests for other entities might also need it, if they test an entity which requires the current entity.
     */
    public static ProgrammingExerciseTestCase createEntity(EntityManager em) {
        ProgrammingExerciseTestCase programmingExerciseTestCase = new ProgrammingExerciseTestCase().file_name(DEFAULT_FILE_NAME).test_name(DEFAULT_TEST_NAME).type(DEFAULT_TYPE)
                .weight(DEFAULT_WEIGHT);
        return programmingExerciseTestCase;
    }

    @Before
    public void initTest() {
        programmingExerciseTestCase = createEntity(em);
    }

    @Test
    @Transactional
    public void createProgrammingExerciseTestCase() throws Exception {
        int databaseSizeBeforeCreate = programmingExerciseTestCaseRepository.findAll().size();

        // Create the ProgrammingExerciseTestCase
        restProgrammingExerciseTestCaseMockMvc.perform(
                post("/api/programming-exercise-test-cases").contentType(TestUtil.APPLICATION_JSON_UTF8).content(TestUtil.convertObjectToJsonBytes(programmingExerciseTestCase)))
                .andExpect(status().isCreated());

        // Validate the ProgrammingExerciseTestCase in the database
        List<ProgrammingExerciseTestCase> programmingExerciseTestCaseList = programmingExerciseTestCaseRepository.findAll();
        assertThat(programmingExerciseTestCaseList).hasSize(databaseSizeBeforeCreate + 1);
        ProgrammingExerciseTestCase testProgrammingExerciseTestCase = programmingExerciseTestCaseList.get(programmingExerciseTestCaseList.size() - 1);
        assertThat(testProgrammingExerciseTestCase.getFileName()).isEqualTo(DEFAULT_FILE_NAME);
        assertThat(testProgrammingExerciseTestCase.getTestName()).isEqualTo(DEFAULT_TEST_NAME);
        assertThat(testProgrammingExerciseTestCase.getType()).isEqualTo(DEFAULT_TYPE);
        assertThat(testProgrammingExerciseTestCase.getWeight()).isEqualTo(DEFAULT_WEIGHT);
    }

    @Test
    @Transactional
    public void createProgrammingExerciseTestCaseWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = programmingExerciseTestCaseRepository.findAll().size();

        // Create the ProgrammingExerciseTestCase with an existing ID
        programmingExerciseTestCase.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restProgrammingExerciseTestCaseMockMvc.perform(
                post("/api/programming-exercise-test-cases").contentType(TestUtil.APPLICATION_JSON_UTF8).content(TestUtil.convertObjectToJsonBytes(programmingExerciseTestCase)))
                .andExpect(status().isBadRequest());

        // Validate the ProgrammingExerciseTestCase in the database
        List<ProgrammingExerciseTestCase> programmingExerciseTestCaseList = programmingExerciseTestCaseRepository.findAll();
        assertThat(programmingExerciseTestCaseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllProgrammingExerciseTestCases() throws Exception {
        // Initialize the database
        programmingExerciseTestCaseRepository.saveAndFlush(programmingExerciseTestCase);

        // Get all the programmingExerciseTestCaseList
        restProgrammingExerciseTestCaseMockMvc.perform(get("/api/programming-exercise-test-cases?sort=id,desc")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath("$.[*].id").value(hasItem(programmingExerciseTestCase.getId().intValue())))
                .andExpect(jsonPath("$.[*].file_name").value(hasItem(DEFAULT_FILE_NAME.toString())))
                .andExpect(jsonPath("$.[*].test_name").value(hasItem(DEFAULT_TEST_NAME.toString()))).andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE.toString())))
                .andExpect(jsonPath("$.[*].weight").value(hasItem(DEFAULT_WEIGHT)));
    }

    @Test
    @Transactional
    public void getProgrammingExerciseTestCase() throws Exception {
        // Initialize the database
        programmingExerciseTestCaseRepository.saveAndFlush(programmingExerciseTestCase);

        // Get the programmingExerciseTestCase
        restProgrammingExerciseTestCaseMockMvc.perform(get("/api/programming-exercise-test-cases/{id}", programmingExerciseTestCase.getId())).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)).andExpect(jsonPath("$.id").value(programmingExerciseTestCase.getId().intValue()))
                .andExpect(jsonPath("$.file_name").value(DEFAULT_FILE_NAME.toString())).andExpect(jsonPath("$.test_name").value(DEFAULT_TEST_NAME.toString()))
                .andExpect(jsonPath("$.type").value(DEFAULT_TYPE.toString())).andExpect(jsonPath("$.weight").value(DEFAULT_WEIGHT));
    }

    @Test
    @Transactional
    public void getNonExistingProgrammingExerciseTestCase() throws Exception {
        // Get the programmingExerciseTestCase
        restProgrammingExerciseTestCaseMockMvc.perform(get("/api/programming-exercise-test-cases/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateProgrammingExerciseTestCase() throws Exception {
        // Initialize the database
        programmingExerciseTestCaseRepository.saveAndFlush(programmingExerciseTestCase);

        int databaseSizeBeforeUpdate = programmingExerciseTestCaseRepository.findAll().size();

        // Update the programmingExerciseTestCase
        ProgrammingExerciseTestCase updatedProgrammingExerciseTestCase = programmingExerciseTestCaseRepository.findById(programmingExerciseTestCase.getId()).get();
        // Disconnect from session so that the updates on updatedProgrammingExerciseTestCase are not directly saved in db
        em.detach(updatedProgrammingExerciseTestCase);
        updatedProgrammingExerciseTestCase.file_name(UPDATED_FILE_NAME).test_name(UPDATED_TEST_NAME).type(UPDATED_TYPE).weight(UPDATED_WEIGHT);

        restProgrammingExerciseTestCaseMockMvc.perform(put("/api/programming-exercise-test-cases").contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(TestUtil.convertObjectToJsonBytes(updatedProgrammingExerciseTestCase))).andExpect(status().isOk());

        // Validate the ProgrammingExerciseTestCase in the database
        List<ProgrammingExerciseTestCase> programmingExerciseTestCaseList = programmingExerciseTestCaseRepository.findAll();
        assertThat(programmingExerciseTestCaseList).hasSize(databaseSizeBeforeUpdate);
        ProgrammingExerciseTestCase testProgrammingExerciseTestCase = programmingExerciseTestCaseList.get(programmingExerciseTestCaseList.size() - 1);
        assertThat(testProgrammingExerciseTestCase.getFileName()).isEqualTo(UPDATED_FILE_NAME);
        assertThat(testProgrammingExerciseTestCase.getTestName()).isEqualTo(UPDATED_TEST_NAME);
        assertThat(testProgrammingExerciseTestCase.getType()).isEqualTo(UPDATED_TYPE);
        assertThat(testProgrammingExerciseTestCase.getWeight()).isEqualTo(UPDATED_WEIGHT);
    }

    @Test
    @Transactional
    public void updateNonExistingProgrammingExerciseTestCase() throws Exception {
        int databaseSizeBeforeUpdate = programmingExerciseTestCaseRepository.findAll().size();

        // Create the ProgrammingExerciseTestCase

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restProgrammingExerciseTestCaseMockMvc.perform(
                put("/api/programming-exercise-test-cases").contentType(TestUtil.APPLICATION_JSON_UTF8).content(TestUtil.convertObjectToJsonBytes(programmingExerciseTestCase)))
                .andExpect(status().isBadRequest());

        // Validate the ProgrammingExerciseTestCase in the database
        List<ProgrammingExerciseTestCase> programmingExerciseTestCaseList = programmingExerciseTestCaseRepository.findAll();
        assertThat(programmingExerciseTestCaseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteProgrammingExerciseTestCase() throws Exception {
        // Initialize the database
        programmingExerciseTestCaseRepository.saveAndFlush(programmingExerciseTestCase);

        int databaseSizeBeforeDelete = programmingExerciseTestCaseRepository.findAll().size();

        // Delete the programmingExerciseTestCase
        restProgrammingExerciseTestCaseMockMvc
                .perform(delete("/api/programming-exercise-test-cases/{id}", programmingExerciseTestCase.getId()).accept(TestUtil.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());

        // Validate the database is empty
        List<ProgrammingExerciseTestCase> programmingExerciseTestCaseList = programmingExerciseTestCaseRepository.findAll();
        assertThat(programmingExerciseTestCaseList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ProgrammingExerciseTestCase.class);
        ProgrammingExerciseTestCase programmingExerciseTestCase1 = new ProgrammingExerciseTestCase();
        programmingExerciseTestCase1.setId(1L);
        ProgrammingExerciseTestCase programmingExerciseTestCase2 = new ProgrammingExerciseTestCase();
        programmingExerciseTestCase2.setId(programmingExerciseTestCase1.getId());
        assertThat(programmingExerciseTestCase1).isEqualTo(programmingExerciseTestCase2);
        programmingExerciseTestCase2.setId(2L);
        assertThat(programmingExerciseTestCase1).isNotEqualTo(programmingExerciseTestCase2);
        programmingExerciseTestCase1.setId(null);
        assertThat(programmingExerciseTestCase1).isNotEqualTo(programmingExerciseTestCase2);
    }
}
