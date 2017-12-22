package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;

import de.tum.in.www1.exerciseapp.domain.PointCounter;
import de.tum.in.www1.exerciseapp.repository.PointCounterRepository;
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
 * Test class for the PointCounterResource REST controller.
 *
 * @see PointCounterResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class PointCounterResourceIntTest {

    private static final Double DEFAULT_POINTS = 1D;
    private static final Double UPDATED_POINTS = 2D;

    @Autowired
    private PointCounterRepository pointCounterRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restPointCounterMockMvc;

    private PointCounter pointCounter;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        PointCounterResource pointCounterResource = new PointCounterResource(pointCounterRepository);
        this.restPointCounterMockMvc = MockMvcBuilders.standaloneSetup(pointCounterResource)
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
    public static PointCounter createEntity(EntityManager em) {
        PointCounter pointCounter = new PointCounter()
            .points(DEFAULT_POINTS);
        return pointCounter;
    }

    @Before
    public void initTest() {
        pointCounter = createEntity(em);
    }

    @Test
    @Transactional
    public void createPointCounter() throws Exception {
        int databaseSizeBeforeCreate = pointCounterRepository.findAll().size();

        // Create the PointCounter
        restPointCounterMockMvc.perform(post("/api/point-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(pointCounter)))
            .andExpect(status().isCreated());

        // Validate the PointCounter in the database
        List<PointCounter> pointCounterList = pointCounterRepository.findAll();
        assertThat(pointCounterList).hasSize(databaseSizeBeforeCreate + 1);
        PointCounter testPointCounter = pointCounterList.get(pointCounterList.size() - 1);
        assertThat(testPointCounter.getPoints()).isEqualTo(DEFAULT_POINTS);
    }

    @Test
    @Transactional
    public void createPointCounterWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = pointCounterRepository.findAll().size();

        // Create the PointCounter with an existing ID
        pointCounter.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restPointCounterMockMvc.perform(post("/api/point-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(pointCounter)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<PointCounter> pointCounterList = pointCounterRepository.findAll();
        assertThat(pointCounterList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllPointCounters() throws Exception {
        // Initialize the database
        pointCounterRepository.saveAndFlush(pointCounter);

        // Get all the pointCounterList
        restPointCounterMockMvc.perform(get("/api/point-counters?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(pointCounter.getId().intValue())))
            .andExpect(jsonPath("$.[*].points").value(hasItem(DEFAULT_POINTS.doubleValue())));
    }

    @Test
    @Transactional
    public void getPointCounter() throws Exception {
        // Initialize the database
        pointCounterRepository.saveAndFlush(pointCounter);

        // Get the pointCounter
        restPointCounterMockMvc.perform(get("/api/point-counters/{id}", pointCounter.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(pointCounter.getId().intValue()))
            .andExpect(jsonPath("$.points").value(DEFAULT_POINTS.doubleValue()));
    }

    @Test
    @Transactional
    public void getNonExistingPointCounter() throws Exception {
        // Get the pointCounter
        restPointCounterMockMvc.perform(get("/api/point-counters/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updatePointCounter() throws Exception {
        // Initialize the database
        pointCounterRepository.saveAndFlush(pointCounter);
        int databaseSizeBeforeUpdate = pointCounterRepository.findAll().size();

        // Update the pointCounter
        PointCounter updatedPointCounter = pointCounterRepository.findOne(pointCounter.getId());
        updatedPointCounter
            .points(UPDATED_POINTS);

        restPointCounterMockMvc.perform(put("/api/point-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedPointCounter)))
            .andExpect(status().isOk());

        // Validate the PointCounter in the database
        List<PointCounter> pointCounterList = pointCounterRepository.findAll();
        assertThat(pointCounterList).hasSize(databaseSizeBeforeUpdate);
        PointCounter testPointCounter = pointCounterList.get(pointCounterList.size() - 1);
        assertThat(testPointCounter.getPoints()).isEqualTo(UPDATED_POINTS);
    }

    @Test
    @Transactional
    public void updateNonExistingPointCounter() throws Exception {
        int databaseSizeBeforeUpdate = pointCounterRepository.findAll().size();

        // Create the PointCounter

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restPointCounterMockMvc.perform(put("/api/point-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(pointCounter)))
            .andExpect(status().isCreated());

        // Validate the PointCounter in the database
        List<PointCounter> pointCounterList = pointCounterRepository.findAll();
        assertThat(pointCounterList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deletePointCounter() throws Exception {
        // Initialize the database
        pointCounterRepository.saveAndFlush(pointCounter);
        int databaseSizeBeforeDelete = pointCounterRepository.findAll().size();

        // Get the pointCounter
        restPointCounterMockMvc.perform(delete("/api/point-counters/{id}", pointCounter.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<PointCounter> pointCounterList = pointCounterRepository.findAll();
        assertThat(pointCounterList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(PointCounter.class);
        PointCounter pointCounter1 = new PointCounter();
        pointCounter1.setId(1L);
        PointCounter pointCounter2 = new PointCounter();
        pointCounter2.setId(pointCounter1.getId());
        assertThat(pointCounter1).isEqualTo(pointCounter2);
        pointCounter2.setId(2L);
        assertThat(pointCounter1).isNotEqualTo(pointCounter2);
        pointCounter1.setId(null);
        assertThat(pointCounter1).isNotEqualTo(pointCounter2);
    }
}
