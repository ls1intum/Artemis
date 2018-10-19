package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
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
 * Test class for the ProgrammingSubmissionResource REST controller.
 *
 * @see ProgrammingSubmissionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class ProgrammingSubmissionResourceIntTest {

    private static final String DEFAULT_COMMIT_HASH = "AAAAAAAAAA";
    private static final String UPDATED_COMMIT_HASH = "BBBBBBBBBB";

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restProgrammingSubmissionMockMvc;

    private ProgrammingSubmission programmingSubmission;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ProgrammingSubmissionResource programmingSubmissionResource = new ProgrammingSubmissionResource(programmingSubmissionRepository);
        this.restProgrammingSubmissionMockMvc = MockMvcBuilders.standaloneSetup(programmingSubmissionResource)
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
    public static ProgrammingSubmission createEntity(EntityManager em) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission()
            .commitHash(DEFAULT_COMMIT_HASH);
        return programmingSubmission;
    }

    @Before
    public void initTest() {
        programmingSubmission = createEntity(em);
    }

    @Test
    @Transactional
    public void createProgrammingSubmission() throws Exception {
        int databaseSizeBeforeCreate = programmingSubmissionRepository.findAll().size();

        // Create the ProgrammingSubmission
        restProgrammingSubmissionMockMvc.perform(post("/api/programming-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(programmingSubmission)))
            .andExpect(status().isCreated());

        // Validate the ProgrammingSubmission in the database
        List<ProgrammingSubmission> programmingSubmissionList = programmingSubmissionRepository.findAll();
        assertThat(programmingSubmissionList).hasSize(databaseSizeBeforeCreate + 1);
        ProgrammingSubmission testProgrammingSubmission = programmingSubmissionList.get(programmingSubmissionList.size() - 1);
        assertThat(testProgrammingSubmission.getCommitHash()).isEqualTo(DEFAULT_COMMIT_HASH);
    }

    @Test
    @Transactional
    public void createProgrammingSubmissionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = programmingSubmissionRepository.findAll().size();

        // Create the ProgrammingSubmission with an existing ID
        programmingSubmission.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restProgrammingSubmissionMockMvc.perform(post("/api/programming-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(programmingSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the ProgrammingSubmission in the database
        List<ProgrammingSubmission> programmingSubmissionList = programmingSubmissionRepository.findAll();
        assertThat(programmingSubmissionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllProgrammingSubmissions() throws Exception {
        // Initialize the database
        programmingSubmissionRepository.saveAndFlush(programmingSubmission);

        // Get all the programmingSubmissionList
        restProgrammingSubmissionMockMvc.perform(get("/api/programming-submissions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(programmingSubmission.getId().intValue())))
            .andExpect(jsonPath("$.[*].commitHash").value(hasItem(DEFAULT_COMMIT_HASH.toString())));
    }
    
    @Test
    @Transactional
    public void getProgrammingSubmission() throws Exception {
        // Initialize the database
        programmingSubmissionRepository.saveAndFlush(programmingSubmission);

        // Get the programmingSubmission
        restProgrammingSubmissionMockMvc.perform(get("/api/programming-submissions/{id}", programmingSubmission.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(programmingSubmission.getId().intValue()))
            .andExpect(jsonPath("$.commitHash").value(DEFAULT_COMMIT_HASH.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingProgrammingSubmission() throws Exception {
        // Get the programmingSubmission
        restProgrammingSubmissionMockMvc.perform(get("/api/programming-submissions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateProgrammingSubmission() throws Exception {
        // Initialize the database
        programmingSubmissionRepository.saveAndFlush(programmingSubmission);

        int databaseSizeBeforeUpdate = programmingSubmissionRepository.findAll().size();

        // Update the programmingSubmission
        ProgrammingSubmission updatedProgrammingSubmission = programmingSubmissionRepository.findById(programmingSubmission.getId()).get();
        // Disconnect from session so that the updates on updatedProgrammingSubmission are not directly saved in db
        em.detach(updatedProgrammingSubmission);
        updatedProgrammingSubmission
            .commitHash(UPDATED_COMMIT_HASH);

        restProgrammingSubmissionMockMvc.perform(put("/api/programming-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedProgrammingSubmission)))
            .andExpect(status().isOk());

        // Validate the ProgrammingSubmission in the database
        List<ProgrammingSubmission> programmingSubmissionList = programmingSubmissionRepository.findAll();
        assertThat(programmingSubmissionList).hasSize(databaseSizeBeforeUpdate);
        ProgrammingSubmission testProgrammingSubmission = programmingSubmissionList.get(programmingSubmissionList.size() - 1);
        assertThat(testProgrammingSubmission.getCommitHash()).isEqualTo(UPDATED_COMMIT_HASH);
    }

    @Test
    @Transactional
    public void updateNonExistingProgrammingSubmission() throws Exception {
        int databaseSizeBeforeUpdate = programmingSubmissionRepository.findAll().size();

        // Create the ProgrammingSubmission

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restProgrammingSubmissionMockMvc.perform(put("/api/programming-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(programmingSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the ProgrammingSubmission in the database
        List<ProgrammingSubmission> programmingSubmissionList = programmingSubmissionRepository.findAll();
        assertThat(programmingSubmissionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteProgrammingSubmission() throws Exception {
        // Initialize the database
        programmingSubmissionRepository.saveAndFlush(programmingSubmission);

        int databaseSizeBeforeDelete = programmingSubmissionRepository.findAll().size();

        // Get the programmingSubmission
        restProgrammingSubmissionMockMvc.perform(delete("/api/programming-submissions/{id}", programmingSubmission.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<ProgrammingSubmission> programmingSubmissionList = programmingSubmissionRepository.findAll();
        assertThat(programmingSubmissionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ProgrammingSubmission.class);
        ProgrammingSubmission programmingSubmission1 = new ProgrammingSubmission();
        programmingSubmission1.setId(1L);
        ProgrammingSubmission programmingSubmission2 = new ProgrammingSubmission();
        programmingSubmission2.setId(programmingSubmission1.getId());
        assertThat(programmingSubmission1).isEqualTo(programmingSubmission2);
        programmingSubmission2.setId(2L);
        assertThat(programmingSubmission1).isNotEqualTo(programmingSubmission2);
        programmingSubmission1.setId(null);
        assertThat(programmingSubmission1).isNotEqualTo(programmingSubmission2);
    }
}
