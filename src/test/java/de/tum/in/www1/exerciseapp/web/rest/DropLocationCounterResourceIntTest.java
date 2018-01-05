package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.DropLocationCounter;
import de.tum.in.www1.exerciseapp.repository.DropLocationCounterRepository;
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
 * Test class for the DropLocationCounterResource REST controller.
 *
 * @see DropLocationCounterResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class DropLocationCounterResourceIntTest {

    @Autowired
    private DropLocationCounterRepository dropLocationCounterRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restDropLocationCounterMockMvc;

    private DropLocationCounter dropLocationCounter;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        DropLocationCounterResource dropLocationCounterResource = new DropLocationCounterResource(dropLocationCounterRepository);
        this.restDropLocationCounterMockMvc = MockMvcBuilders.standaloneSetup(dropLocationCounterResource)
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
    public static DropLocationCounter createEntity(EntityManager em) {
        DropLocationCounter dropLocationCounter = new DropLocationCounter();
        return dropLocationCounter;
    }

    @Before
    public void initTest() {
        dropLocationCounter = createEntity(em);
    }

    @Test
    @Transactional
    public void createDropLocationCounter() throws Exception {
        int databaseSizeBeforeCreate = dropLocationCounterRepository.findAll().size();

        // Create the DropLocationCounter
        restDropLocationCounterMockMvc.perform(post("/api/drop-location-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dropLocationCounter)))
            .andExpect(status().isCreated());

        // Validate the DropLocationCounter in the database
        List<DropLocationCounter> dropLocationCounterList = dropLocationCounterRepository.findAll();
        assertThat(dropLocationCounterList).hasSize(databaseSizeBeforeCreate + 1);
        DropLocationCounter testDropLocationCounter = dropLocationCounterList.get(dropLocationCounterList.size() - 1);
    }

    @Test
    @Transactional
    public void createDropLocationCounterWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = dropLocationCounterRepository.findAll().size();

        // Create the DropLocationCounter with an existing ID
        dropLocationCounter.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restDropLocationCounterMockMvc.perform(post("/api/drop-location-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dropLocationCounter)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<DropLocationCounter> dropLocationCounterList = dropLocationCounterRepository.findAll();
        assertThat(dropLocationCounterList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllDropLocationCounters() throws Exception {
        // Initialize the database
        dropLocationCounterRepository.saveAndFlush(dropLocationCounter);

        // Get all the dropLocationCounterList
        restDropLocationCounterMockMvc.perform(get("/api/drop-location-counters?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dropLocationCounter.getId().intValue())));
    }

    @Test
    @Transactional
    public void getDropLocationCounter() throws Exception {
        // Initialize the database
        dropLocationCounterRepository.saveAndFlush(dropLocationCounter);

        // Get the dropLocationCounter
        restDropLocationCounterMockMvc.perform(get("/api/drop-location-counters/{id}", dropLocationCounter.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(dropLocationCounter.getId().intValue()));
    }

    @Test
    @Transactional
    public void getNonExistingDropLocationCounter() throws Exception {
        // Get the dropLocationCounter
        restDropLocationCounterMockMvc.perform(get("/api/drop-location-counters/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateDropLocationCounter() throws Exception {
        // Initialize the database
        dropLocationCounterRepository.saveAndFlush(dropLocationCounter);
        int databaseSizeBeforeUpdate = dropLocationCounterRepository.findAll().size();

        // Update the dropLocationCounter
        DropLocationCounter updatedDropLocationCounter = dropLocationCounterRepository.findOne(dropLocationCounter.getId());

        restDropLocationCounterMockMvc.perform(put("/api/drop-location-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedDropLocationCounter)))
            .andExpect(status().isOk());

        // Validate the DropLocationCounter in the database
        List<DropLocationCounter> dropLocationCounterList = dropLocationCounterRepository.findAll();
        assertThat(dropLocationCounterList).hasSize(databaseSizeBeforeUpdate);
        DropLocationCounter testDropLocationCounter = dropLocationCounterList.get(dropLocationCounterList.size() - 1);
    }

    @Test
    @Transactional
    public void updateNonExistingDropLocationCounter() throws Exception {
        int databaseSizeBeforeUpdate = dropLocationCounterRepository.findAll().size();

        // Create the DropLocationCounter

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restDropLocationCounterMockMvc.perform(put("/api/drop-location-counters")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dropLocationCounter)))
            .andExpect(status().isCreated());

        // Validate the DropLocationCounter in the database
        List<DropLocationCounter> dropLocationCounterList = dropLocationCounterRepository.findAll();
        assertThat(dropLocationCounterList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteDropLocationCounter() throws Exception {
        // Initialize the database
        dropLocationCounterRepository.saveAndFlush(dropLocationCounter);
        int databaseSizeBeforeDelete = dropLocationCounterRepository.findAll().size();

        // Get the dropLocationCounter
        restDropLocationCounterMockMvc.perform(delete("/api/drop-location-counters/{id}", dropLocationCounter.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<DropLocationCounter> dropLocationCounterList = dropLocationCounterRepository.findAll();
        assertThat(dropLocationCounterList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DropLocationCounter.class);
        DropLocationCounter dropLocationCounter1 = new DropLocationCounter();
        dropLocationCounter1.setId(1L);
        DropLocationCounter dropLocationCounter2 = new DropLocationCounter();
        dropLocationCounter2.setId(dropLocationCounter1.getId());
        assertThat(dropLocationCounter1).isEqualTo(dropLocationCounter2);
        dropLocationCounter2.setId(2L);
        assertThat(dropLocationCounter1).isNotEqualTo(dropLocationCounter2);
        dropLocationCounter1.setId(null);
        assertThat(dropLocationCounter1).isNotEqualTo(dropLocationCounter2);
    }
}
