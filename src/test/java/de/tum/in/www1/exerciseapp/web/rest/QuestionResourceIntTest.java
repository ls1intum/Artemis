package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ArTEMiSApp;
import de.tum.in.www1.exerciseapp.domain.MultipleChoiceQuestion;
import de.tum.in.www1.exerciseapp.domain.Question;
import de.tum.in.www1.exerciseapp.domain.enumeration.ScoringType;
import de.tum.in.www1.exerciseapp.repository.QuestionRepository;
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
 * Test class for the QuestionResource REST controller.
 *
 * @see QuestionResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ArTEMiSApp.class)
public class QuestionResourceIntTest {

    private static final String DEFAULT_TITLE = "AAAAAAAAAA";
    private static final String UPDATED_TITLE = "BBBBBBBBBB";

    private static final String DEFAULT_TEXT = "AAAAAAAAAA";
    private static final String UPDATED_TEXT = "BBBBBBBBBB";

    private static final String DEFAULT_HINT = "AAAAAAAAAA";
    private static final String UPDATED_HINT = "BBBBBBBBBB";

    private static final String DEFAULT_EXPLANATION = "AAAAAAAAAA";
    private static final String UPDATED_EXPLANATION = "BBBBBBBBBB";

    private static final Integer DEFAULT_SCORE = 1;
    private static final Integer UPDATED_SCORE = 2;

    private static final ScoringType DEFAULT_SCORING_TYPE = ScoringType.ALL_OR_NOTHING;
    private static final ScoringType UPDATED_SCORING_TYPE = ScoringType.PROPORTIONAL_WITH_PENALTY;

    private static final Boolean DEFAULT_RANDOMIZE_ORDER = false;
    private static final Boolean UPDATED_RANDOMIZE_ORDER = true;

    private static final Boolean DEFAULT_INVALID = false;
    private static final Boolean UPDATED_INVALID = true;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    private MockMvc restQuestionMockMvc;

    private Question question;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        QuestionResource questionResource = new QuestionResource(questionRepository);
        this.restQuestionMockMvc = MockMvcBuilders.standaloneSetup(questionResource)
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
    public static Question createEntity(EntityManager em) {
        Question question = new MultipleChoiceQuestion()
            .title(DEFAULT_TITLE)
            .text(DEFAULT_TEXT)
            .hint(DEFAULT_HINT)
            .explanation(DEFAULT_EXPLANATION)
            .score(DEFAULT_SCORE)
            .scoringType(DEFAULT_SCORING_TYPE)
            .randomizeOrder(DEFAULT_RANDOMIZE_ORDER)
            .invalid(DEFAULT_INVALID);
        return question;
    }

    @Before
    public void initTest() {
        question = createEntity(em);
    }

    @Test
    @Transactional
    public void createQuestion() throws Exception {
        int databaseSizeBeforeCreate = questionRepository.findAll().size();

        // Create the Question
        restQuestionMockMvc.perform(post("/api/questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(question)))
            .andExpect(status().isCreated());

        // Validate the Question in the database
        List<Question> questionList = questionRepository.findAll();
        assertThat(questionList).hasSize(databaseSizeBeforeCreate + 1);
        Question testQuestion = questionList.get(questionList.size() - 1);
        assertThat(testQuestion.getTitle()).isEqualTo(DEFAULT_TITLE);
        assertThat(testQuestion.getText()).isEqualTo(DEFAULT_TEXT);
        assertThat(testQuestion.getHint()).isEqualTo(DEFAULT_HINT);
        assertThat(testQuestion.getExplanation()).isEqualTo(DEFAULT_EXPLANATION);
        assertThat(testQuestion.getScore()).isEqualTo(DEFAULT_SCORE);
        assertThat(testQuestion.getScoringType()).isEqualTo(DEFAULT_SCORING_TYPE);
        assertThat(testQuestion.isRandomizeOrder()).isEqualTo(DEFAULT_RANDOMIZE_ORDER);
        assertThat(testQuestion.isInvalid()).isEqualTo(DEFAULT_INVALID);
    }

    @Test
    @Transactional
    public void createQuestionWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = questionRepository.findAll().size();

        // Create the Question with an existing ID
        question.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restQuestionMockMvc.perform(post("/api/questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(question)))
            .andExpect(status().isBadRequest());

        // Validate the Alice in the database
        List<Question> questionList = questionRepository.findAll();
        assertThat(questionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    public void getAllQuestions() throws Exception {
        // Initialize the database
        questionRepository.saveAndFlush(question);

        // Get all the questionList
        restQuestionMockMvc.perform(get("/api/questions?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(question.getId().intValue())))
            .andExpect(jsonPath("$.[*].title").value(hasItem(DEFAULT_TITLE.toString())))
            .andExpect(jsonPath("$.[*].text").value(hasItem(DEFAULT_TEXT.toString())))
            .andExpect(jsonPath("$.[*].hint").value(hasItem(DEFAULT_HINT.toString())))
            .andExpect(jsonPath("$.[*].explanation").value(hasItem(DEFAULT_EXPLANATION.toString())))
            .andExpect(jsonPath("$.[*].score").value(hasItem(DEFAULT_SCORE)))
            .andExpect(jsonPath("$.[*].scoringType").value(hasItem(DEFAULT_SCORING_TYPE.toString())))
            .andExpect(jsonPath("$.[*].randomizeOrder").value(hasItem(DEFAULT_RANDOMIZE_ORDER.booleanValue())))
            .andExpect(jsonPath("$.[*].invalid").value(hasItem(DEFAULT_INVALID.booleanValue())));
    }

    @Test
    @Transactional
    public void getQuestion() throws Exception {
        // Initialize the database
        questionRepository.saveAndFlush(question);

        // Get the question
        restQuestionMockMvc.perform(get("/api/questions/{id}", question.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(question.getId().intValue()))
            .andExpect(jsonPath("$.title").value(DEFAULT_TITLE.toString()))
            .andExpect(jsonPath("$.text").value(DEFAULT_TEXT.toString()))
            .andExpect(jsonPath("$.hint").value(DEFAULT_HINT.toString()))
            .andExpect(jsonPath("$.explanation").value(DEFAULT_EXPLANATION.toString()))
            .andExpect(jsonPath("$.score").value(DEFAULT_SCORE))
            .andExpect(jsonPath("$.scoringType").value(DEFAULT_SCORING_TYPE.toString()))
            .andExpect(jsonPath("$.randomizeOrder").value(DEFAULT_RANDOMIZE_ORDER.booleanValue()))
            .andExpect(jsonPath("$.invalid").value(DEFAULT_INVALID.booleanValue()));
    }

    @Test
    @Transactional
    public void getNonExistingQuestion() throws Exception {
        // Get the question
        restQuestionMockMvc.perform(get("/api/questions/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateQuestion() throws Exception {
        // Initialize the database
        questionRepository.saveAndFlush(question);
        int databaseSizeBeforeUpdate = questionRepository.findAll().size();

        // Update the question
        Question updatedQuestion = questionRepository.findOne(question.getId());
        updatedQuestion
            .title(UPDATED_TITLE)
            .text(UPDATED_TEXT)
            .hint(UPDATED_HINT)
            .explanation(UPDATED_EXPLANATION)
            .score(UPDATED_SCORE)
            .scoringType(UPDATED_SCORING_TYPE)
            .randomizeOrder(UPDATED_RANDOMIZE_ORDER)
            .invalid(UPDATED_INVALID);

        restQuestionMockMvc.perform(put("/api/questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedQuestion)))
            .andExpect(status().isOk());

        // Validate the Question in the database
        List<Question> questionList = questionRepository.findAll();
        assertThat(questionList).hasSize(databaseSizeBeforeUpdate);
        Question testQuestion = questionList.get(questionList.size() - 1);
        assertThat(testQuestion.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testQuestion.getText()).isEqualTo(UPDATED_TEXT);
        assertThat(testQuestion.getHint()).isEqualTo(UPDATED_HINT);
        assertThat(testQuestion.getExplanation()).isEqualTo(UPDATED_EXPLANATION);
        assertThat(testQuestion.getScore()).isEqualTo(UPDATED_SCORE);
        assertThat(testQuestion.getScoringType()).isEqualTo(UPDATED_SCORING_TYPE);
        assertThat(testQuestion.isRandomizeOrder()).isEqualTo(UPDATED_RANDOMIZE_ORDER);
        assertThat(testQuestion.isInvalid()).isEqualTo(UPDATED_INVALID);
    }

    @Test
    @Transactional
    public void updateNonExistingQuestion() throws Exception {
        int databaseSizeBeforeUpdate = questionRepository.findAll().size();

        // Create the Question

        // If the entity doesn't have an ID, it will be created instead of just being updated
        restQuestionMockMvc.perform(put("/api/questions")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(question)))
            .andExpect(status().isCreated());

        // Validate the Question in the database
        List<Question> questionList = questionRepository.findAll();
        assertThat(questionList).hasSize(databaseSizeBeforeUpdate + 1);
    }

    @Test
    @Transactional
    public void deleteQuestion() throws Exception {
        // Initialize the database
        questionRepository.saveAndFlush(question);
        int databaseSizeBeforeDelete = questionRepository.findAll().size();

        // Get the question
        restQuestionMockMvc.perform(delete("/api/questions/{id}", question.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk());

        // Validate the database is empty
        List<Question> questionList = questionRepository.findAll();
        assertThat(questionList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Question.class);
        Question question1 = new MultipleChoiceQuestion();
        question1.setId(1L);
        Question question2 = new MultipleChoiceQuestion();
        question2.setId(question1.getId());
        assertThat(question1).isEqualTo(question2);
        question2.setId(2L);
        assertThat(question1).isNotEqualTo(question2);
        question1.setId(null);
        assertThat(question1).isNotEqualTo(question2);
    }
}
