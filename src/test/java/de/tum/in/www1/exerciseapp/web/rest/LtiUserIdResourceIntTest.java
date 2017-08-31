package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;

import de.tum.in.www1.exerciseapp.domain.LtiUserId;
import de.tum.in.www1.exerciseapp.repository.LtiUserIdRepository;
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
 * Test class for the LtiUserIdResource REST controller.
 *
 * @see LtiUserIdResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ExerciseApplicationApp.class)
public class LtiUserIdResourceIntTest {

    private static final String DEFAULT_LTI_USER_ID = "AAAAAAAAAA";
    private static final String UPDATED_LTI_USER_ID = "BBBBBBBBBB";

    @Autowired
    private LtiUserIdRepository ltiUserIdRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restLtiUserIdMockMvc;

    private LtiUserId ltiUserId;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final LtiUserIdResource ltiUserIdResource = new LtiUserIdResource(ltiUserIdRepository);
        this.restLtiUserIdMockMvc = MockMvcBuilders.standaloneSetup(ltiUserIdResource)
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
    public static LtiUserId createEntity(EntityManager em) {
        LtiUserId ltiUserId = new LtiUserId()
            .ltiUserId(DEFAULT_LTI_USER_ID);
        return ltiUserId;
    }

    @Before
    public void initTest() {
        ltiUserId = createEntity(em);
    }

    @Test
    @Transactional
    public void createLtiUserId() throws Exception {
        int databaseSizeBeforeCreate = ltiUserIdRepository.findAll().size();

        // Create the LtiUserId
        restLtiUserIdMockMvc.perform(post("/api/lti-user-ids")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(ltiUserId)))
            .andExpect(status().isCreated());

        // Validate the LtiUserId in the database
        List<LtiUserId> ltiUserIdList = ltiUserIdRepository.findAll();
        assertThat(ltiUserIdList).hasSize(databaseSizeBeforeCreate + 1);
        LtiUserId testLtiUserId = ltiUserIdList.get(ltiUserIdList.size() - 1);
        assertThat(testLtiUserId.getLtiUserId()).isEqualTo(DEFAULT_LTI_USER_ID);
    }

    @Test
    @Transactional
    public void createLtiUserIdWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = ltiUserIdRepository.findAll().size();

        // Create the LtiUserId with an existing ID
        ltiUserId.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restLtiUserIdMockMvc.perform(post("/api/lti-user-ids")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(ltiUserId)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<LtiUserId> ltiUserIdList = ltiUserIdRepository.findAll();
        assertThat(ltiUserIdList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllLtiUserIds() throws Exception {
        // Initialize the database
        ltiUserIdRepository.saveAndFlush(ltiUserId);

        // Get all the ltiUserIdList
        restLtiUserIdMockMvc.perform(get("/api/lti-user-ids?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ltiUserId.getId().intValue())))
            .andExpect(jsonPath("$.[*].ltiUserId").value(hasItem(DEFAULT_LTI_USER_ID.toString())));
    }

    @Test
    @Transactional
    public void getLtiUserId() throws Exception {
        // Initialize the database
        ltiUserIdRepository.saveAndFlush(ltiUserId);

        // Get the ltiUserId
        restLtiUserIdMockMvc.perform(get("/api/lti-user-ids/{id}", ltiUserId.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(ltiUserId.getId().intValue()))
            .andExpect(jsonPath("$.ltiUserId").value(DEFAULT_LTI_USER_ID.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingLtiUserId() throws Exception {
        // Get the ltiUserId
        restLtiUserIdMockMvc.perform(get("/api/lti-user-ids/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateLtiUserId() throws Exception {
        // Initialize the database
        ltiUserIdRepository.saveAndFlush(ltiUserId);
        int databaseSizeBeforeUpdate = ltiUserIdRepository.findAll().size();

        // Update the ltiUserId
        LtiUserId updatedLtiUserId = ltiUserIdRepository.findOne(ltiUserId.getId());
        updatedLtiUserId
            .ltiUserId(UPDATED_LTI_USER_ID);

        restLtiUserIdMockMvc.perform(put("/api/lti-user-ids")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedLtiUserId)))
            .andExpect(status().isOk());

        // Validate the LtiUserId in the database
        List<LtiUserId> ltiUserIdList = ltiUserIdRepository.findAll();
        assertThat(ltiUserIdList).hasSize(databaseSizeBeforeUpdate);
        LtiUserId testLtiUserId = ltiUserIdList.get(ltiUserIdList.size() - 1);
        assertThat(testLtiUserId.getLtiUserId()).isEqualTo(UPDATED_LTI_USER_ID);
    }

    @Test
    @Transactional
    public void updateNonExistingLtiUserId() throws Exception {
        int databaseSizeBeforeUpdate = ltiUserIdRepository.findAll().size();

        // Create the LtiUserId

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restLtiUserIdMockMvc.perform(put("/api/lti-user-ids")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(ltiUserId)))
            .andExpect(status().isCreated());

        // Validate the LtiUserId in the database
        List<LtiUserId> ltiUserIdList = ltiUserIdRepository.findAll();
        assertThat(ltiUserIdList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteLtiUserId() throws Exception {
        // Initialize the database
        ltiUserIdRepository.saveAndFlush(ltiUserId);
        int databaseSizeBeforeDelete = ltiUserIdRepository.findAll().size();

        // Get the ltiUserId
        restLtiUserIdMockMvc.perform(delete("/api/lti-user-ids/{id}", ltiUserId.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<LtiUserId> ltiUserIdList = ltiUserIdRepository.findAll();
        assertThat(ltiUserIdList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(LtiUserId.class);
        LtiUserId ltiUserId1 = new LtiUserId();
        ltiUserId1.setId(1L);
        LtiUserId ltiUserId2 = new LtiUserId();
        ltiUserId2.setId(ltiUserId1.getId());
        assertThat(ltiUserId1).isEqualTo(ltiUserId2);
        ltiUserId2.setId(2L);
        assertThat(ltiUserId1).isNotEqualTo(ltiUserId2);
        ltiUserId1.setId(null);
        assertThat(ltiUserId1).isNotEqualTo(ltiUserId2);
    }
}
