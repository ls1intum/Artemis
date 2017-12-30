package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.QuizSubmission;
import de.tum.in.www1.exerciseapp.domain.Submission;
import de.tum.in.www1.exerciseapp.domain.enumeration.SubmissionType;
import de.tum.in.www1.exerciseapp.repository.SubmissionRepository;
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
 * Test class for the SubmissionResource REST controller.
 *
 * @see SubmissionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class SubmissionResourceIntTest {

    private static final Boolean DEFAULT_SUBMITTED = false;
    private static final Boolean UPDATED_SUBMITTED = true;

    private static final SubmissionType DEFAULT_TYPE = SubmissionType.MANUAL;
    private static final SubmissionType UPDATED_TYPE = SubmissionType.TIMEOUT;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restSubmissionMockMvc;

    private Submission submission;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final SubmissionResource submissionResource = new SubmissionResource(submissionRepository);
        this.restSubmissionMockMvc = MockMvcBuilders.standaloneSetup(submissionResource)
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
    public static Submission createEntity(EntityManager em) {
        Submission submission = new QuizSubmission()
            .submitted(DEFAULT_SUBMITTED)
            .type(DEFAULT_TYPE);
        return submission;
    }

    @Before
    public void initTest() {
        submission = createEntity(em);
    }

    @Test
    @Transactional
    public void createSubmission() throws Exception {
        int databaseSizeBeforeCreate = submissionRepository.findAll().size();

        // Create the Submission
        restSubmissionMockMvc.perform(post("/api/submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(submission)))
            .andExpect(status().isCreated());

        // Validate the Submission in the database
        List<Submission> submissionList = submissionRepository.findAll();
        assertThat(submissionList).hasSize(databaseSizeBeforeCreate + 1);
        Submission testSubmission = submissionList.get(submissionList.size() - 1);
        assertThat(testSubmission.isSubmitted()).isEqualTo(DEFAULT_SUBMITTED);
        assertThat(testSubmission.getType()).isEqualTo(DEFAULT_TYPE);
    }

    @Test
    @Transactional
    public void createSubmissionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = submissionRepository.findAll().size();

        // Create the Submission with an existing ID
        submission.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restSubmissionMockMvc.perform(post("/api/submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(submission)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<Submission> submissionList = submissionRepository.findAll();
        assertThat(submissionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllSubmissions() throws Exception {
        // Initialize the database
        submissionRepository.saveAndFlush(submission);

        // Get all the submissionList
        restSubmissionMockMvc.perform(get("/api/submissions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(submission.getId().intValue())))
            .andExpect(jsonPath("$.[*].submitted").value(hasItem(DEFAULT_SUBMITTED.booleanValue())))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE.toString())));
    }

    @Test
    @Transactional
    public void getSubmission() throws Exception {
        // Initialize the database
        submissionRepository.saveAndFlush(submission);

        // Get the submission
        restSubmissionMockMvc.perform(get("/api/submissions/{id}", submission.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(submission.getId().intValue()))
            .andExpect(jsonPath("$.submitted").value(DEFAULT_SUBMITTED.booleanValue()))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingSubmission() throws Exception {
        // Get the submission
        restSubmissionMockMvc.perform(get("/api/submissions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateSubmission() throws Exception {
        // Initialize the database
        submissionRepository.saveAndFlush(submission);
        int databaseSizeBeforeUpdate = submissionRepository.findAll().size();

        // Update the submission
        Submission updatedSubmission = submissionRepository.findOne(submission.getId());
        updatedSubmission
            .submitted(UPDATED_SUBMITTED)
            .type(UPDATED_TYPE);

        restSubmissionMockMvc.perform(put("/api/submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedSubmission)))
            .andExpect(status().isOk());

        // Validate the Submission in the database
        List<Submission> submissionList = submissionRepository.findAll();
        assertThat(submissionList).hasSize(databaseSizeBeforeUpdate);
        Submission testSubmission = submissionList.get(submissionList.size() - 1);
        assertThat(testSubmission.isSubmitted()).isEqualTo(UPDATED_SUBMITTED);
        assertThat(testSubmission.getType()).isEqualTo(UPDATED_TYPE);
    }

    @Test
    @Transactional
    public void updateNonExistingSubmission() throws Exception {
        int databaseSizeBeforeUpdate = submissionRepository.findAll().size();

        // Create the Submission

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restSubmissionMockMvc.perform(put("/api/submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(submission)))
            .andExpect(status().isCreated());

        // Validate the Submission in the database
        List<Submission> submissionList = submissionRepository.findAll();
        assertThat(submissionList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteSubmission() throws Exception {
        // Initialize the database
        submissionRepository.saveAndFlush(submission);
        int databaseSizeBeforeDelete = submissionRepository.findAll().size();

        // Get the submission
        restSubmissionMockMvc.perform(delete("/api/submissions/{id}", submission.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<Submission> submissionList = submissionRepository.findAll();
        assertThat(submissionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Submission.class);
        Submission submission1 = new QuizSubmission();
        submission1.setId(1L);
        Submission submission2 = new QuizSubmission();
        submission2.setId(submission1.getId());
        assertThat(submission1).isEqualTo(submission2);
        submission2.setId(2L);
        assertThat(submission1).isNotEqualTo(submission2);
        submission1.setId(null);
        assertThat(submission1).isNotEqualTo(submission2);
    }
}
