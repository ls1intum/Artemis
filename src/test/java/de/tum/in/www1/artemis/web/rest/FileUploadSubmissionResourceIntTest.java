package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
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
 * Test class for the FileUploadSubmissionResource REST controller.
 *
 * @see FileUploadSubmissionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class FileUploadSubmissionResourceIntTest {

    private static final String DEFAULT_FILE_PATH = "AAAAAAAAAA";
    private static final String UPDATED_FILE_PATH = "BBBBBBBBBB";

    @Autowired
    private FileUploadSubmissionRepository fileUploadSubmissionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restFileUploadSubmissionMockMvc;

    private FileUploadSubmission fileUploadSubmission;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final FileUploadSubmissionResource fileUploadSubmissionResource = new FileUploadSubmissionResource(fileUploadSubmissionRepository);
        this.restFileUploadSubmissionMockMvc = MockMvcBuilders.standaloneSetup(fileUploadSubmissionResource)
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
    public static FileUploadSubmission createEntity(EntityManager em) {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission()
            .filePath(DEFAULT_FILE_PATH);
        return fileUploadSubmission;
    }

    @Before
    public void initTest() {
        fileUploadSubmission = createEntity(em);
    }

    @Test
    @Transactional
    public void createFileUploadSubmission() throws Exception {
        int databaseSizeBeforeCreate = fileUploadSubmissionRepository.findAll().size();

        // Create the FileUploadSubmission
        restFileUploadSubmissionMockMvc.perform(post("/api/file-upload-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(fileUploadSubmission)))
            .andExpect(status().isCreated());

        // Validate the FileUploadSubmission in the database
        List<FileUploadSubmission> fileUploadSubmissionList = fileUploadSubmissionRepository.findAll();
        assertThat(fileUploadSubmissionList).hasSize(databaseSizeBeforeCreate + 1);
        FileUploadSubmission testFileUploadSubmission = fileUploadSubmissionList.get(fileUploadSubmissionList.size() - 1);
        assertThat(testFileUploadSubmission.getFilePath()).isEqualTo(DEFAULT_FILE_PATH);
    }

    @Test
    @Transactional
    public void createFileUploadSubmissionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = fileUploadSubmissionRepository.findAll().size();

        // Create the FileUploadSubmission with an existing ID
        fileUploadSubmission.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restFileUploadSubmissionMockMvc.perform(post("/api/file-upload-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(fileUploadSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the FileUploadSubmission in the database
        List<FileUploadSubmission> fileUploadSubmissionList = fileUploadSubmissionRepository.findAll();
        assertThat(fileUploadSubmissionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllFileUploadSubmissions() throws Exception {
        // Initialize the database
        fileUploadSubmissionRepository.saveAndFlush(fileUploadSubmission);

        // Get all the fileUploadSubmissionList
        restFileUploadSubmissionMockMvc.perform(get("/api/file-upload-submissions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(fileUploadSubmission.getId().intValue())))
            .andExpect(jsonPath("$.[*].filePath").value(hasItem(DEFAULT_FILE_PATH.toString())));
    }
    
    @Test
    @Transactional
    public void getFileUploadSubmission() throws Exception {
        // Initialize the database
        fileUploadSubmissionRepository.saveAndFlush(fileUploadSubmission);

        // Get the fileUploadSubmission
        restFileUploadSubmissionMockMvc.perform(get("/api/file-upload-submissions/{id}", fileUploadSubmission.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(fileUploadSubmission.getId().intValue()))
            .andExpect(jsonPath("$.filePath").value(DEFAULT_FILE_PATH.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingFileUploadSubmission() throws Exception {
        // Get the fileUploadSubmission
        restFileUploadSubmissionMockMvc.perform(get("/api/file-upload-submissions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateFileUploadSubmission() throws Exception {
        // Initialize the database
        fileUploadSubmissionRepository.saveAndFlush(fileUploadSubmission);

        int databaseSizeBeforeUpdate = fileUploadSubmissionRepository.findAll().size();

        // Update the fileUploadSubmission
        FileUploadSubmission updatedFileUploadSubmission = fileUploadSubmissionRepository.findById(fileUploadSubmission.getId()).get();
        // Disconnect from session so that the updates on updatedFileUploadSubmission are not directly saved in db
        em.detach(updatedFileUploadSubmission);
        updatedFileUploadSubmission
            .filePath(UPDATED_FILE_PATH);

        restFileUploadSubmissionMockMvc.perform(put("/api/file-upload-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedFileUploadSubmission)))
            .andExpect(status().isOk());

        // Validate the FileUploadSubmission in the database
        List<FileUploadSubmission> fileUploadSubmissionList = fileUploadSubmissionRepository.findAll();
        assertThat(fileUploadSubmissionList).hasSize(databaseSizeBeforeUpdate);
        FileUploadSubmission testFileUploadSubmission = fileUploadSubmissionList.get(fileUploadSubmissionList.size() - 1);
        assertThat(testFileUploadSubmission.getFilePath()).isEqualTo(UPDATED_FILE_PATH);
    }

    @Test
    @Transactional
    public void updateNonExistingFileUploadSubmission() throws Exception {
        int databaseSizeBeforeUpdate = fileUploadSubmissionRepository.findAll().size();

        // Create the FileUploadSubmission

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFileUploadSubmissionMockMvc.perform(put("/api/file-upload-submissions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(fileUploadSubmission)))
            .andExpect(status().isBadRequest());

        // Validate the FileUploadSubmission in the database
        List<FileUploadSubmission> fileUploadSubmissionList = fileUploadSubmissionRepository.findAll();
        assertThat(fileUploadSubmissionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteFileUploadSubmission() throws Exception {
        // Initialize the database
        fileUploadSubmissionRepository.saveAndFlush(fileUploadSubmission);

        int databaseSizeBeforeDelete = fileUploadSubmissionRepository.findAll().size();

        // Get the fileUploadSubmission
        restFileUploadSubmissionMockMvc.perform(delete("/api/file-upload-submissions/{id}", fileUploadSubmission.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<FileUploadSubmission> fileUploadSubmissionList = fileUploadSubmissionRepository.findAll();
        assertThat(fileUploadSubmissionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(FileUploadSubmission.class);
        FileUploadSubmission fileUploadSubmission1 = new FileUploadSubmission();
        fileUploadSubmission1.setId(1L);
        FileUploadSubmission fileUploadSubmission2 = new FileUploadSubmission();
        fileUploadSubmission2.setId(fileUploadSubmission1.getId());
        assertThat(fileUploadSubmission1).isEqualTo(fileUploadSubmission2);
        fileUploadSubmission2.setId(2L);
        assertThat(fileUploadSubmission1).isNotEqualTo(fileUploadSubmission2);
        fileUploadSubmission1.setId(null);
        assertThat(fileUploadSubmission1).isNotEqualTo(fileUploadSubmission2);
    }
}
