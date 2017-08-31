package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;

import de.tum.in.www1.exerciseapp.domain.DropLocation;
import de.tum.in.www1.exerciseapp.repository.DropLocationRepository;
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
 * Test class for the DropLocationResource REST controller.
 *
 * @see DropLocationResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ExerciseApplicationApp.class)
public class DropLocationResourceIntTest {

    private static final Integer DEFAULT_POS_X = 1;
    private static final Integer UPDATED_POS_X = 2;

    private static final Integer DEFAULT_POS_Y = 1;
    private static final Integer UPDATED_POS_Y = 2;

    private static final Integer DEFAULT_WIDTH = 1;
    private static final Integer UPDATED_WIDTH = 2;

    private static final Integer DEFAULT_HEIGHT = 1;
    private static final Integer UPDATED_HEIGHT = 2;

    @Autowired
    private DropLocationRepository dropLocationRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDropLocationMockMvc;

    private DropLocation dropLocation;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final DropLocationResource dropLocationResource = new DropLocationResource(dropLocationRepository);
        this.restDropLocationMockMvc = MockMvcBuilders.standaloneSetup(dropLocationResource)
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
    public static DropLocation createEntity(EntityManager em) {
        DropLocation dropLocation = new DropLocation()
            .posX(DEFAULT_POS_X)
            .posY(DEFAULT_POS_Y)
            .width(DEFAULT_WIDTH)
            .height(DEFAULT_HEIGHT);
        return dropLocation;
    }

    @Before
    public void initTest() {
        dropLocation = createEntity(em);
    }

    @Test
    @Transactional
    public void createDropLocation() throws Exception {
        int databaseSizeBeforeCreate = dropLocationRepository.findAll().size();

        // Create the DropLocation
        restDropLocationMockMvc.perform(post("/api/drop-locations")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dropLocation)))
            .andExpect(status().isCreated());

        // Validate the DropLocation in the database
        List<DropLocation> dropLocationList = dropLocationRepository.findAll();
        assertThat(dropLocationList).hasSize(databaseSizeBeforeCreate + 1);
        DropLocation testDropLocation = dropLocationList.get(dropLocationList.size() - 1);
        assertThat(testDropLocation.getPosX()).isEqualTo(DEFAULT_POS_X);
        assertThat(testDropLocation.getPosY()).isEqualTo(DEFAULT_POS_Y);
        assertThat(testDropLocation.getWidth()).isEqualTo(DEFAULT_WIDTH);
        assertThat(testDropLocation.getHeight()).isEqualTo(DEFAULT_HEIGHT);
    }

    @Test
    @Transactional
    public void createDropLocationWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dropLocationRepository.findAll().size();

        // Create the DropLocation with an existing ID
        dropLocation.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDropLocationMockMvc.perform(post("/api/drop-locations")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dropLocation)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<DropLocation> dropLocationList = dropLocationRepository.findAll();
        assertThat(dropLocationList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDropLocations() throws Exception {
        // Initialize the database
        dropLocationRepository.saveAndFlush(dropLocation);

        // Get all the dropLocationList
        restDropLocationMockMvc.perform(get("/api/drop-locations?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dropLocation.getId().intValue())))
            .andExpect(jsonPath("$.[*].posX").value(hasItem(DEFAULT_POS_X)))
            .andExpect(jsonPath("$.[*].posY").value(hasItem(DEFAULT_POS_Y)))
            .andExpect(jsonPath("$.[*].width").value(hasItem(DEFAULT_WIDTH)))
            .andExpect(jsonPath("$.[*].height").value(hasItem(DEFAULT_HEIGHT)));
    }

    @Test
    @Transactional
    public void getDropLocation() throws Exception {
        // Initialize the database
        dropLocationRepository.saveAndFlush(dropLocation);

        // Get the dropLocation
        restDropLocationMockMvc.perform(get("/api/drop-locations/{id}", dropLocation.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dropLocation.getId().intValue()))
            .andExpect(jsonPath("$.posX").value(DEFAULT_POS_X))
            .andExpect(jsonPath("$.posY").value(DEFAULT_POS_Y))
            .andExpect(jsonPath("$.width").value(DEFAULT_WIDTH))
            .andExpect(jsonPath("$.height").value(DEFAULT_HEIGHT));
    }

    @Test
    @Transactional
    public void getNonExistingDropLocation() throws Exception {
        // Get the dropLocation
        restDropLocationMockMvc.perform(get("/api/drop-locations/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDropLocation() throws Exception {
        // Initialize the database
        dropLocationRepository.saveAndFlush(dropLocation);
        int databaseSizeBeforeUpdate = dropLocationRepository.findAll().size();

        // Update the dropLocation
        DropLocation updatedDropLocation = dropLocationRepository.findOne(dropLocation.getId());
        updatedDropLocation
            .posX(UPDATED_POS_X)
            .posY(UPDATED_POS_Y)
            .width(UPDATED_WIDTH)
            .height(UPDATED_HEIGHT);

        restDropLocationMockMvc.perform(put("/api/drop-locations")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDropLocation)))
            .andExpect(status().isOk());

        // Validate the DropLocation in the database
        List<DropLocation> dropLocationList = dropLocationRepository.findAll();
        assertThat(dropLocationList).hasSize(databaseSizeBeforeUpdate);
        DropLocation testDropLocation = dropLocationList.get(dropLocationList.size() - 1);
        assertThat(testDropLocation.getPosX()).isEqualTo(UPDATED_POS_X);
        assertThat(testDropLocation.getPosY()).isEqualTo(UPDATED_POS_Y);
        assertThat(testDropLocation.getWidth()).isEqualTo(UPDATED_WIDTH);
        assertThat(testDropLocation.getHeight()).isEqualTo(UPDATED_HEIGHT);
    }

    @Test
    @Transactional
    public void updateNonExistingDropLocation() throws Exception {
        int databaseSizeBeforeUpdate = dropLocationRepository.findAll().size();

        // Create the DropLocation

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restDropLocationMockMvc.perform(put("/api/drop-locations")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dropLocation)))
            .andExpect(status().isCreated());

        // Validate the DropLocation in the database
        List<DropLocation> dropLocationList = dropLocationRepository.findAll();
        assertThat(dropLocationList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteDropLocation() throws Exception {
        // Initialize the database
        dropLocationRepository.saveAndFlush(dropLocation);
        int databaseSizeBeforeDelete = dropLocationRepository.findAll().size();

        // Get the dropLocation
        restDropLocationMockMvc.perform(delete("/api/drop-locations/{id}", dropLocation.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DropLocation> dropLocationList = dropLocationRepository.findAll();
        assertThat(dropLocationList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DropLocation.class);
        DropLocation dropLocation1 = new DropLocation();
        dropLocation1.setId(1L);
        DropLocation dropLocation2 = new DropLocation();
        dropLocation2.setId(dropLocation1.getId());
        assertThat(dropLocation1).isEqualTo(dropLocation2);
        dropLocation2.setId(2L);
        assertThat(dropLocation1).isNotEqualTo(dropLocation2);
        dropLocation1.setId(null);
        assertThat(dropLocation1).isNotEqualTo(dropLocation2);
    }
}
