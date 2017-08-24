package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for the ParticipationResource REST controller.
 *
 * @see ParticipationResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ExerciseApplicationApp.class)
public class ParticipationResourceIntTest {

    private static final DateTimeFormatter dateTimeFormatter;

    static {
        dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("Z"));
    }

    private static final String DEFAULT_REPOSITORY_URL = "AAAAA";
    private static final String UPDATED_REPOSITORY_URL = "BBBBB";
    private static final String DEFAULT_BUILD_PLAN_ID = "AAAAA";
    private static final String UPDATED_BUILD_PLAN_ID = "BBBBB";

    private static final ParticipationState DEFAULT_INITIALIZATION_STATE = ParticipationState.UNINITIALIZED;
    private static final ParticipationState UPDATED_INITIALIZATION_STATE = ParticipationState.REPO_COPIED;

    private static final ZonedDateTime DEFAULT_INITIALIZATION_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneId.systemDefault());
    private static final ZonedDateTime UPDATED_INITIALIZATION_DATE = ZonedDateTime.now(ZoneId
        .systemDefault()).withNano(0);
    private static final String DEFAULT_INITIALIZATION_DATE_STR = dateTimeFormatter.format(DEFAULT_INITIALIZATION_DATE);

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restParticipationMockMvc;

    private Participation participation;

    private final ParticipationResource participationResource;

    public ParticipationResourceIntTest(ParticipationResource participationResource) {
        this.participationResource = participationResource;
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        this.restParticipationMockMvc = MockMvcBuilders.standaloneSetup(participationResource)
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
    public static Participation createEntity(EntityManager em) {
        Participation participation = new Participation();
        participation = new Participation();
        participation.setRepositoryUrl(DEFAULT_REPOSITORY_URL);
        participation.setBuildPlanId(DEFAULT_BUILD_PLAN_ID);
        participation.setInitializationState(DEFAULT_INITIALIZATION_STATE);
        participation.setInitializationDate(DEFAULT_INITIALIZATION_DATE);
        return participation;
    }

    @Before
    public void initTest() {
        participation = createEntity(em);
    }

    @Test
    @Transactional
    public void createParticipation() throws Exception {
        int databaseSizeBeforeCreate = participationRepository.findAll().size();

        // Create the Participation
        restParticipationMockMvc.perform(post("/api/participations")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(participation)))
            .andExpect(status().isCreated());

        // Validate the Participation in the database
        List<Participation> participationList = participationRepository.findAll();
        assertThat(participationList).hasSize(databaseSizeBeforeCreate + 1);
        Participation testParticipation = participationList.get(participationList.size() - 1);
        assertThat(testParticipation.getRepositoryUrl()).isEqualTo(DEFAULT_REPOSITORY_URL);
        assertThat(testParticipation.getBuildPlanId()).isEqualTo(DEFAULT_BUILD_PLAN_ID);
        assertThat(testParticipation.getInitializationState()).isEqualTo(DEFAULT_INITIALIZATION_STATE);
        assertThat(testParticipation.getInitializationDate()).isEqualTo(DEFAULT_INITIALIZATION_DATE);
    }

    @Test
    @Transactional
    public void createParticipationWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = participationRepository.findAll().size();

        // Create the Participation with an existing ID
        participation.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restParticipationMockMvc.perform(post("/api/participations")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(participation)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<Participation> participationList = participationRepository.findAll();
        assertThat(participationList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllParticipations() throws Exception {
        // Initialize the database
        participationRepository.saveAndFlush(participation);

        // Get all the participationList
        restParticipationMockMvc.perform(get("/api/participations?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(participation.getId().intValue())))
            .andExpect(jsonPath("$.[*].repositoryUrl").value(hasItem(DEFAULT_REPOSITORY_URL.toString())))
            .andExpect(jsonPath("$.[*].buildPlanId").value(hasItem(DEFAULT_BUILD_PLAN_ID.toString())))
            .andExpect(jsonPath("$.[*].initializationState").value(hasItem(DEFAULT_INITIALIZATION_STATE.toString())))
            .andExpect(jsonPath("$.[*].initializationDate").value(hasItem(DEFAULT_INITIALIZATION_DATE_STR)));
    }

    @Test
    @Transactional
    public void getParticipation() throws Exception {
        // Initialize the database
        participationRepository.saveAndFlush(participation);

        // Get the participation
        restParticipationMockMvc.perform(get("/api/participations/{id}", participation.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(participation.getId().intValue()))
            .andExpect(jsonPath("$.repositoryUrl").value(DEFAULT_REPOSITORY_URL.toString()))
            .andExpect(jsonPath("$.buildPlanId").value(DEFAULT_BUILD_PLAN_ID.toString()))
            .andExpect(jsonPath("$.initializationState").value(DEFAULT_INITIALIZATION_STATE.toString()))
            .andExpect(jsonPath("$.initializationDate").value(DEFAULT_INITIALIZATION_DATE_STR));
    }

    @Test
    @Transactional
    public void getNonExistingParticipation() throws Exception {
        // Get the participation
        restParticipationMockMvc.perform(get("/api/participations/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateParticipation() throws Exception {
        // Initialize the database
        participationService.save(participation);

        int databaseSizeBeforeUpdate = participationRepository.findAll().size();

        // Update the participation
        Participation updatedParticipation = participationRepository.findOne(participation.getId());
        updatedParticipation.setRepositoryUrl(UPDATED_REPOSITORY_URL);
        updatedParticipation.setBuildPlanId(UPDATED_BUILD_PLAN_ID);
        updatedParticipation.setInitializationState(UPDATED_INITIALIZATION_STATE);
        updatedParticipation.setInitializationDate(UPDATED_INITIALIZATION_DATE);

        restParticipationMockMvc.perform(put("/api/participations")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedParticipation)))
            .andExpect(status().isOk());

        // Validate the Participation in the database
        List<Participation> participationList = participationRepository.findAll();
        assertThat(participationList).hasSize(databaseSizeBeforeUpdate);
        Participation testParticipation = participationList.get(participationList.size() - 1);
        assertThat(testParticipation.getRepositoryUrl()).isEqualTo(UPDATED_REPOSITORY_URL);
        assertThat(testParticipation.getBuildPlanId()).isEqualTo(UPDATED_BUILD_PLAN_ID);
        assertThat(testParticipation.getInitializationState()).isEqualTo(UPDATED_INITIALIZATION_STATE);
        assertThat(testParticipation.getInitializationDate()).isEqualTo(UPDATED_INITIALIZATION_DATE);
    }

    @Test
    @Transactional
    public void updateNonExistingParticipation() throws Exception {
        int databaseSizeBeforeUpdate = participationRepository.findAll().size();

        // Create the Participation

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restParticipationMockMvc.perform(put("/api/participations")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(participation)))
            .andExpect(status().isCreated());

        // Validate the Participation in the database
        List<Participation> participationList = participationRepository.findAll();
        assertThat(participationList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteParticipation() throws Exception {
        // Initialize the database
        participationService.save(participation);

        int databaseSizeBeforeDelete = participationRepository.findAll().size();

        // Get the participation
        restParticipationMockMvc.perform(delete("/api/participations/{id}", participation.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<Participation> participationList = participationRepository.findAll();
        assertThat(participationList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Participation.class);
        Participation participation1 = new Participation();
        participation1.setId(1L);
        Participation participation2 = new Participation();
        participation2.setId(participation1.getId());
        assertThat(participation1).isEqualTo(participation2);
        participation2.setId(2L);
        assertThat(participation1).isNotEqualTo(participation2);
        participation1.setId(null);
        assertThat(participation1).isNotEqualTo(participation2);
    }
}
