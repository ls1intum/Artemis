package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
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
 * Test class for the FileUploadExerciseResource REST controller.
 *
 * @see FileUploadExerciseResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class FileUploadExerciseResourceIntTest {

    private static final String DEFAULT_FILE_PATTERN = "AAAAAAAAAA";
    private static final String UPDATED_FILE_PATTERN = "BBBBBBBBBB";

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restFileUploadExerciseMockMvc;

    private FileUploadExercise fileUploadExercise;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final FileUploadExerciseResource fileUploadExerciseResource = new FileUploadExerciseResource(fileUploadExerciseRepository);
        this.restFileUploadExerciseMockMvc = MockMvcBuilders.standaloneSetup(fileUploadExerciseResource)
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
    public static FileUploadExercise createEntity(EntityManager em) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise()
            .filePattern(DEFAULT_FILE_PATTERN);
        return fileUploadExercise;
    }

    @Before
    public void initTest() {
        fileUploadExercise = createEntity(em);
    }

    @Test
    @Transactional
    public void createFileUploadExercise() throws Exception {
        int databaseSizeBeforeCreate = fileUploadExerciseRepository.findAll().size();

        // Create the FileUploadExercise
        restFileUploadExerciseMockMvc.perform(post("/api/file-upload-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(fileUploadExercise)))
            .andExpect(status().isCreated());

        // Validate the FileUploadExercise in the database
        List<FileUploadExercise> fileUploadExerciseList = fileUploadExerciseRepository.findAll();
        assertThat(fileUploadExerciseList).hasSize(databaseSizeBeforeCreate + 1);
        FileUploadExercise testFileUploadExercise = fileUploadExerciseList.get(fileUploadExerciseList.size() - 1);
        assertThat(testFileUploadExercise.getFilePattern()).isEqualTo(DEFAULT_FILE_PATTERN);
    }

    @Test
    @Transactional
    public void createFileUploadExerciseWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = fileUploadExerciseRepository.findAll().size();

        // Create the FileUploadExercise with an existing ID
        fileUploadExercise.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restFileUploadExerciseMockMvc.perform(post("/api/file-upload-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(fileUploadExercise)))
            .andExpect(status().isBadRequest());

        // Validate the FileUploadExercise in the database
        List<FileUploadExercise> fileUploadExerciseList = fileUploadExerciseRepository.findAll();
        assertThat(fileUploadExerciseList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllFileUploadExercises() throws Exception {
        // Initialize the database
        fileUploadExerciseRepository.saveAndFlush(fileUploadExercise);

        // Get all the fileUploadExerciseList
        restFileUploadExerciseMockMvc.perform(get("/api/file-upload-exercises?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(fileUploadExercise.getId().intValue())))
            .andExpect(jsonPath("$.[*].filePattern").value(hasItem(DEFAULT_FILE_PATTERN.toString())));
    }
    
    @Test
    @Transactional
    public void getFileUploadExercise() throws Exception {
        // Initialize the database
        fileUploadExerciseRepository.saveAndFlush(fileUploadExercise);

        // Get the fileUploadExercise
        restFileUploadExerciseMockMvc.perform(get("/api/file-upload-exercises/{id}", fileUploadExercise.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(fileUploadExercise.getId().intValue()))
            .andExpect(jsonPath("$.filePattern").value(DEFAULT_FILE_PATTERN.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingFileUploadExercise() throws Exception {
        // Get the fileUploadExercise
        restFileUploadExerciseMockMvc.perform(get("/api/file-upload-exercises/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateFileUploadExercise() throws Exception {
        // Initialize the database
        fileUploadExerciseRepository.saveAndFlush(fileUploadExercise);

        int databaseSizeBeforeUpdate = fileUploadExerciseRepository.findAll().size();

        // Update the fileUploadExercise
        FileUploadExercise updatedFileUploadExercise = fileUploadExerciseRepository.findById(fileUploadExercise.getId()).get();
        // Disconnect from session so that the updates on updatedFileUploadExercise are not directly saved in db
        em.detach(updatedFileUploadExercise);
        updatedFileUploadExercise
            .filePattern(UPDATED_FILE_PATTERN);

        restFileUploadExerciseMockMvc.perform(put("/api/file-upload-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedFileUploadExercise)))
            .andExpect(status().isOk());

        // Validate the FileUploadExercise in the database
        List<FileUploadExercise> fileUploadExerciseList = fileUploadExerciseRepository.findAll();
        assertThat(fileUploadExerciseList).hasSize(databaseSizeBeforeUpdate);
        FileUploadExercise testFileUploadExercise = fileUploadExerciseList.get(fileUploadExerciseList.size() - 1);
        assertThat(testFileUploadExercise.getFilePattern()).isEqualTo(UPDATED_FILE_PATTERN);
    }

    @Test
    @Transactional
    public void updateNonExistingFileUploadExercise() throws Exception {
        int databaseSizeBeforeUpdate = fileUploadExerciseRepository.findAll().size();

        // Create the FileUploadExercise

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFileUploadExerciseMockMvc.perform(put("/api/file-upload-exercises")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(fileUploadExercise)))
            .andExpect(status().isBadRequest());

        // Validate the FileUploadExercise in the database
        List<FileUploadExercise> fileUploadExerciseList = fileUploadExerciseRepository.findAll();
        assertThat(fileUploadExerciseList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteFileUploadExercise() throws Exception {
        // Initialize the database
        fileUploadExerciseRepository.saveAndFlush(fileUploadExercise);

        int databaseSizeBeforeDelete = fileUploadExerciseRepository.findAll().size();

        // Get the fileUploadExercise
        restFileUploadExerciseMockMvc.perform(delete("/api/file-upload-exercises/{id}", fileUploadExercise.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<FileUploadExercise> fileUploadExerciseList = fileUploadExerciseRepository.findAll();
        assertThat(fileUploadExerciseList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(FileUploadExercise.class);
        FileUploadExercise fileUploadExercise1 = new FileUploadExercise();
        fileUploadExercise1.setId(1L);
        FileUploadExercise fileUploadExercise2 = new FileUploadExercise();
        fileUploadExercise2.setId(fileUploadExercise1.getId());
        assertThat(fileUploadExercise1).isEqualTo(fileUploadExercise2);
        fileUploadExercise2.setId(2L);
        assertThat(fileUploadExercise1).isNotEqualTo(fileUploadExercise2);
        fileUploadExercise1.setId(null);
        assertThat(fileUploadExercise1).isNotEqualTo(fileUploadExercise2);
    }
}
