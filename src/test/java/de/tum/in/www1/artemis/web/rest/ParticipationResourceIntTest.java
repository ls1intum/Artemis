package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.ArTeMiSApp;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.service.ParticipationService;
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
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

import static de.tum.in.www1.artemis.web.rest.TestUtil.sameInstant;
import static de.tum.in.www1.artemis.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;
/**
 * Test class for the ParticipationResource REST controller.
 *
 * @see ParticipationResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTeMiSApp.class)
public class ParticipationResourceIntTest {

    private static final String DEFAULT_REPOSITORY_URL = "AAAAAAAAAA";
    private static final String UPDATED_REPOSITORY_URL = "BBBBBBBBBB";

    private static final String DEFAULT_BUILD_PLAN_ID = "AAAAAAAAAA";
    private static final String UPDATED_BUILD_PLAN_ID = "BBBBBBBBBB";

    private static final ParticipationState DEFAULT_INITIALIZATION_STATE = ParticipationState.UNINITIALIZED;
    private static final ParticipationState UPDATED_INITIALIZATION_STATE = ParticipationState.REPO_COPIED;

    private static final ZonedDateTime DEFAULT_INITIALIZATION_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_INITIALIZATION_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

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

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final ParticipationResource participationResource = new ParticipationResource(participationService);
        this.restParticipationMockMvc = MockMvcBuilders.standaloneSetup(participationResource)
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
    public static Participation createEntity(EntityManager em) {
        Participation participation = new Participation()
            .repositoryUrl(DEFAULT_REPOSITORY_URL)
            .buildPlanId(DEFAULT_BUILD_PLAN_ID)
            .initializationState(DEFAULT_INITIALIZATION_STATE)
            .initializationDate(DEFAULT_INITIALIZATION_DATE);
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

        // Validate the Participation in the database
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
            .andExpect(jsonPath("$.[*].initializationDate").value(hasItem(sameInstant(DEFAULT_INITIALIZATION_DATE))));
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
            .andExpect(jsonPath("$.initializationDate").value(sameInstant(DEFAULT_INITIALIZATION_DATE)));
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
        // Disconnect from session so that the updates on updatedParticipation are not directly saved in db
        em.detach(updatedParticipation);
        updatedParticipation
            .repositoryUrl(UPDATED_REPOSITORY_URL)
            .buildPlanId(UPDATED_BUILD_PLAN_ID)
            .initializationState(UPDATED_INITIALIZATION_STATE)
            .initializationDate(UPDATED_INITIALIZATION_DATE);

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
