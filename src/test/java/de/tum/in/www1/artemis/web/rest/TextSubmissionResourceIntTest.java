package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
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
 * Test class for the TextSubmissionResource REST controller.
 *
 * @see TextSubmissionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class TextSubmissionResourceIntTest {

    private static final String DEFAULT_TEXT = "AAAAAAAAAA";
    private static final String UPDATED_TEXT = "BBBBBBBBBB";

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restTextSubmissionMockMvc;

    private TextSubmission textSubmission;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final TextSubmissionResource textSubmissionResource = new TextSubmissionResource(textSubmissionRepository);
        this.restTextSubmissionMockMvc = MockMvcBuilders.standaloneSetup(textSubmissionResource)
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
    public static TextSubmission createEntity(EntityManager em) {
        TextSubmission textSubmission = new TextSubmission()
            .text(DEFAULT_TEXT);
        return textSubmission;
    }

    @Before
    public void initTest() {
        textSubmission = createEntity(em);
    }

    @Test
    @Transactional
    public void createTextSubmission() throws Exception {
        int databaseSizeBeforeCreate = textSubmissionRepository.findAll().size();

        // Create the TextSubmission
        restTextSubmissionMockMvc.perform(post("/api/text-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(textSubmission)))
            .andExpect(status().isCreated());

        // Validate the TextSubmission in the database
        List<TextSubmission> textSubmissionList = textSubmissionRepository.findAll();
        assertThat(textSubmissionList).hasSize(databaseSizeBeforeCreate + 1);
        TextSubmission testTextSubmission = textSubmissionList.get(textSubmissionList.size() - 1);
        assertThat(testTextSubmission.getText()).isEqualTo(DEFAULT_TEXT);
    }

    @Test
    @Transactional
    public void createTextSubmissionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = textSubmissionRepository.findAll().size();

        // Create the TextSubmission with an existing ID
        textSubmission.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restTextSubmissionMockMvc.perform(post("/api/text-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(textSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the TextSubmission in the database
        List<TextSubmission> textSubmissionList = textSubmissionRepository.findAll();
        assertThat(textSubmissionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllTextSubmissions() throws Exception {
        // Initialize the database
        textSubmissionRepository.saveAndFlush(textSubmission);

        // Get all the textSubmissionList
        restTextSubmissionMockMvc.perform(get("/api/text-submissions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(textSubmission.getId().intValue())))
            .andExpect(jsonPath("$.[*].text").value(hasItem(DEFAULT_TEXT.toString())));
    }
    
    @Test
    @Transactional
    public void getTextSubmission() throws Exception {
        // Initialize the database
        textSubmissionRepository.saveAndFlush(textSubmission);

        // Get the textSubmission
        restTextSubmissionMockMvc.perform(get("/api/text-submissions/{id}", textSubmission.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(textSubmission.getId().intValue()))
            .andExpect(jsonPath("$.text").value(DEFAULT_TEXT.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingTextSubmission() throws Exception {
        // Get the textSubmission
        restTextSubmissionMockMvc.perform(get("/api/text-submissions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateTextSubmission() throws Exception {
        // Initialize the database
        textSubmissionRepository.saveAndFlush(textSubmission);

        int databaseSizeBeforeUpdate = textSubmissionRepository.findAll().size();

        // Update the textSubmission
        TextSubmission updatedTextSubmission = textSubmissionRepository.findById(textSubmission.getId()).get();
        // Disconnect from session so that the updates on updatedTextSubmission are not directly saved in db
        em.detach(updatedTextSubmission);
        updatedTextSubmission
            .text(UPDATED_TEXT);

        restTextSubmissionMockMvc.perform(put("/api/text-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedTextSubmission)))
            .andExpect(status().isOk());

        // Validate the TextSubmission in the database
        List<TextSubmission> textSubmissionList = textSubmissionRepository.findAll();
        assertThat(textSubmissionList).hasSize(databaseSizeBeforeUpdate);
        TextSubmission testTextSubmission = textSubmissionList.get(textSubmissionList.size() - 1);
        assertThat(testTextSubmission.getText()).isEqualTo(UPDATED_TEXT);
    }

    @Test
    @Transactional
    public void updateNonExistingTextSubmission() throws Exception {
        int databaseSizeBeforeUpdate = textSubmissionRepository.findAll().size();

        // Create the TextSubmission

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restTextSubmissionMockMvc.perform(put("/api/text-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(textSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the TextSubmission in the database
        List<TextSubmission> textSubmissionList = textSubmissionRepository.findAll();
        assertThat(textSubmissionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteTextSubmission() throws Exception {
        // Initialize the database
        textSubmissionRepository.saveAndFlush(textSubmission);

        int databaseSizeBeforeDelete = textSubmissionRepository.findAll().size();

        // Get the textSubmission
        restTextSubmissionMockMvc.perform(delete("/api/text-submissions/{id}", textSubmission.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<TextSubmission> textSubmissionList = textSubmissionRepository.findAll();
        assertThat(textSubmissionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(TextSubmission.class);
        TextSubmission textSubmission1 = new TextSubmission();
        textSubmission1.setId(1L);
        TextSubmission textSubmission2 = new TextSubmission();
        textSubmission2.setId(textSubmission1.getId());
        assertThat(textSubmission1).isEqualTo(textSubmission2);
        textSubmission2.setId(2L);
        assertThat(textSubmission1).isNotEqualTo(textSubmission2);
        textSubmission1.setId(null);
        assertThat(textSubmission1).isNotEqualTo(textSubmission2);
    }
}
