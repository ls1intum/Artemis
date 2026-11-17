package de.tum.cit.aet.artemis.atlas.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionReviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionSummaryDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSubmissionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.EntityIdRefDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.MultipleChoiceSubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;

@TestPropertySource(properties = { "artemis.hyperion.enabled=true" })
class PracticeQuizIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "practicequiztest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private QuizQuestionPoolItemRepository quizQuestionPoolItemRepository;

    private Course course;

    private QuizQuestionPoolItem poolItem1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);

        Competency competency = competencyUtilService.createCompetency(course, "Practice Competency");
        Lecture lecture = lectureUtilService.createLecture(course);
        LectureUnit lectureUnit = lectureUtilService.createTextUnit(lecture);

        // Create practice-eligible questions in the AI question pool
        poolItem1 = createPoolItem(0, "Item 1 Title", "Item 1 Text", competency.getId(), lectureUnit.getId());
        createPoolItem(1, "Item 2 Title", "Item 2 Text", competency.getId(), lectureUnit.getId());
    }

    private QuizQuestionPoolItem createPoolItem(int index, String title, String text, Long competencyId, Long lectureUnitId) {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setCourse(course);
        item.setCompetencyId(competencyId);
        item.setLectureUnitId(lectureUnitId);
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setQuestionType(QuizQuestionGenerationType.MULTIPLE_CHOICE);
        item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
        item.setItemIndex(index);
        item.setState(QuizQuestionPoolItemState.FILTERED);
        item.setAccepted(true);
        item.setReviewState(QuizQuestionReviewState.APPROVED);

        GeneratedQuizAnswerOptionDTO opt1 = new GeneratedQuizAnswerOptionDTO("Correct Option", true, "Hint 1", "Explanation 1");
        GeneratedQuizAnswerOptionDTO opt2 = new GeneratedQuizAnswerOptionDTO("Wrong Option", false, null, null);
        GeneratedQuizQuestionDTO question = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.MULTIPLE_CHOICE, title, text, List.of(opt1, opt2), "Overall Hint",
                "Overall Explanation");
        item.setQuestion(question);

        return quizQuestionPoolItemRepository.save(item);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartAndSubmitPracticeQuizFlow() throws Exception {
        // 1. Start practice session
        PracticeSessionDTO sessionDTO = request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/smart-quizzes/start", null, PracticeSessionDTO.class,
                HttpStatus.OK);

        assertThat(sessionDTO).isNotNull();
        assertThat(sessionDTO.id()).isPositive();
        assertThat(sessionDTO.questions()).isNotEmpty();

        long sessionId = sessionDTO.id();

        // 2. Check current in-progress session
        PracticeSessionDTO currentSession = request.get("/api/atlas/courses/" + course.getId() + "/smart-quizzes/current", HttpStatus.OK, PracticeSessionDTO.class);
        assertThat(currentSession).isNotNull();
        assertThat(currentSession.id()).isEqualTo(sessionId);

        // 3. Submit answers
        SubmittedAnswerFromLiveClientDTO answerDTO = new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(poolItem1.getId()), Set.of(new EntityIdRefDTO(0L)));
        PracticeSubmissionDTO submission = new PracticeSubmissionDTO(List.of(answerDTO));

        PracticeSessionReviewDTO reviewDTO = request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/smart-quizzes/sessions/" + sessionId + "/submit", submission,
                PracticeSessionReviewDTO.class, HttpStatus.OK);

        assertThat(reviewDTO).isNotNull();
        assertThat(reviewDTO.id()).isEqualTo(sessionId);
        assertThat(reviewDTO.completedAt()).isNotNull();
        assertThat(reviewDTO.questions()).isNotEmpty();

        // 4. Verify review endpoint
        PracticeSessionReviewDTO fetchedReview = request.get("/api/atlas/courses/" + course.getId() + "/smart-quizzes/sessions/" + sessionId, HttpStatus.OK,
                PracticeSessionReviewDTO.class);
        assertThat(fetchedReview).isNotNull();
        assertThat(fetchedReview.id()).isEqualTo(sessionId);

        // 5. Verify sessions list
        List<PracticeSessionSummaryDTO> summaries = request.getList("/api/atlas/courses/" + course.getId() + "/smart-quizzes/sessions", HttpStatus.OK,
                PracticeSessionSummaryDTO.class);
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().id()).isEqualTo(sessionId);
        assertThat(summaries.getFirst().completed()).isTrue();
    }
}
