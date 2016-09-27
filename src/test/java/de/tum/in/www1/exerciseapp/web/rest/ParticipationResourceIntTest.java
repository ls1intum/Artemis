package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.service.ParticipationService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.hamcrest.Matchers.hasItem;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;

/**
 * Test class for the ParticipationResource REST controller.
 *
 * @see ParticipationResource
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ExerciseApplicationApp.class)
@WebAppConfiguration
@IntegrationTest
public class ParticipationResourceIntTest {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("Z"));

    private static final String DEFAULT_CLONE_URL = "AAAAA";
    private static final String UPDATED_CLONE_URL = "BBBBB";

    private static final ParticipationState DEFAULT_INITIALIZATION_STATE = ParticipationState.UNINITIALIZED;
    private static final ParticipationState UPDATED_INITIALIZATION_STATE = ParticipationState.REPO_COPIED;

    private static final ZonedDateTime DEFAULT_INITIALIZATION_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneId.systemDefault());
    private static final ZonedDateTime UPDATED_INITIALIZATION_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);
    private static final String DEFAULT_INITIALIZATION_DATE_STR = dateTimeFormatter.format(DEFAULT_INITIALIZATION_DATE);
    private static final String DEFAULT_BUILD_PLAN_ID = "AAAAA";
    private static final String UPDATED_BUILD_PLAN_ID = "BBBBB";

    @Inject
    private ParticipationRepository participationRepository;

    @Inject
    private ParticipationService participationService;

    @Inject
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Inject
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    private MockMvc restParticipationMockMvc;

    private Participation participation;

    @PostConstruct
    public void setup() {
        MockitoAnnotations.initMocks(this);
        ParticipationResource participationResource = new ParticipationResource();
        ReflectionTestUtils.setField(participationResource, "participationService", participationService);
        this.restParticipationMockMvc = MockMvcBuilders.standaloneSetup(participationResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setMessageConverters(jacksonMessageConverter).build();
    }

    @Before
    public void initTest() {
        participation = new Participation();
        participation.setCloneUrl(DEFAULT_CLONE_URL);
        participation.setInitializationState(DEFAULT_INITIALIZATION_STATE);
        participation.setInitializationDate(DEFAULT_INITIALIZATION_DATE);
        participation.setBuildPlanId(DEFAULT_BUILD_PLAN_ID);
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
        List<Participation> participations = participationRepository.findAll();
        assertThat(participations).hasSize(databaseSizeBeforeCreate + 1);
        Participation testParticipation = participations.get(participations.size() - 1);
        assertThat(testParticipation.getCloneUrl()).isEqualTo(DEFAULT_CLONE_URL);
        assertThat(testParticipation.getInitializationState()).isEqualTo(DEFAULT_INITIALIZATION_STATE);
        assertThat(testParticipation.getInitializationDate()).isEqualTo(DEFAULT_INITIALIZATION_DATE);
        assertThat(testParticipation.getBuildPlanId()).isEqualTo(DEFAULT_BUILD_PLAN_ID);
    }

    @Test
    @Transactional
    public void getAllParticipations() throws Exception {
        // Initialize the database
        participationRepository.saveAndFlush(participation);

        // Get all the participations
        restParticipationMockMvc.perform(get("/api/participations?sort=id,desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.[*].id").value(hasItem(participation.getId().intValue())))
                .andExpect(jsonPath("$.[*].cloneUrl").value(hasItem(DEFAULT_CLONE_URL.toString())))
                .andExpect(jsonPath("$.[*].initializationState").value(hasItem(DEFAULT_INITIALIZATION_STATE.toString())))
                .andExpect(jsonPath("$.[*].initializationDate").value(hasItem(DEFAULT_INITIALIZATION_DATE_STR)))
                .andExpect(jsonPath("$.[*].buildPlanId").value(hasItem(DEFAULT_BUILD_PLAN_ID.toString())));
    }

    @Test
    @Transactional
    public void getParticipation() throws Exception {
        // Initialize the database
        participationRepository.saveAndFlush(participation);

        // Get the participation
        restParticipationMockMvc.perform(get("/api/participations/{id}", participation.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(participation.getId().intValue()))
            .andExpect(jsonPath("$.cloneUrl").value(DEFAULT_CLONE_URL.toString()))
            .andExpect(jsonPath("$.initializationState").value(DEFAULT_INITIALIZATION_STATE.toString()))
            .andExpect(jsonPath("$.initializationDate").value(DEFAULT_INITIALIZATION_DATE_STR))
            .andExpect(jsonPath("$.buildPlanId").value(DEFAULT_BUILD_PLAN_ID.toString()));
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
        Participation updatedParticipation = new Participation();
        updatedParticipation.setId(participation.getId());
        updatedParticipation.setCloneUrl(UPDATED_CLONE_URL);
        updatedParticipation.setInitializationState(UPDATED_INITIALIZATION_STATE);
        updatedParticipation.setInitializationDate(UPDATED_INITIALIZATION_DATE);
        updatedParticipation.setBuildPlanId(UPDATED_BUILD_PLAN_ID);

        restParticipationMockMvc.perform(put("/api/participations")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(TestUtil.convertObjectToJsonBytes(updatedParticipation)))
                .andExpect(status().isOk());

        // Validate the Participation in the database
        List<Participation> participations = participationRepository.findAll();
        assertThat(participations).hasSize(databaseSizeBeforeUpdate);
        Participation testParticipation = participations.get(participations.size() - 1);
        assertThat(testParticipation.getCloneUrl()).isEqualTo(UPDATED_CLONE_URL);
        assertThat(testParticipation.getInitializationState()).isEqualTo(UPDATED_INITIALIZATION_STATE);
        assertThat(testParticipation.getInitializationDate()).isEqualTo(UPDATED_INITIALIZATION_DATE);
        assertThat(testParticipation.getBuildPlanId()).isEqualTo(UPDATED_BUILD_PLAN_ID);
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
        List<Participation> participations = participationRepository.findAll();
        assertThat(participations).hasSize(databaseSizeBeforeDelete - 1);
    }
}
