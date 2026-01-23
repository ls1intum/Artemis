package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExerciseDeletionSummaryIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exdelsuminteg";

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private BuildJobTestRepository buildJobRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        // Add an instructor who is not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetDeletionSummary() throws Exception {
        var studentParticipation1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var studentParticipation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        BuildJob buildJob1 = new BuildJob();
        buildJob1.setExerciseId(programmingExercise.getId());
        buildJob1.setParticipationId(studentParticipation1.getId());
        buildJob1.setCourseId(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        buildJobRepository.save(buildJob1);

        BuildJob buildJob2 = new BuildJob();
        buildJob2.setExerciseId(programmingExercise.getId());
        buildJob2.setParticipationId(studentParticipation2.getId());
        buildJob2.setCourseId(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        buildJobRepository.save(buildJob2);

        Channel channel = new Channel();
        channel.setExercise(programmingExercise);
        channel.setName("test-channel-exercise");
        channel.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        channelRepository.save(channel);

        Post post1 = new Post();
        post1.setConversation(channel);
        post1.setContent("Test Post 1");
        post1.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        postRepository.save(post1);

        Post post2 = new Post();
        post2.setConversation(channel);
        post2.setContent("Test Post 2");
        post2.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        postRepository.save(post2);

        Channel channel2 = new Channel();
        channel2.setName("test-channel-course");
        channel2.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        channel2.setIsCourseWide(true);
        channelRepository.save(channel2);

        Post post3 = new Post();
        post3.setConversation(channel2);
        post3.setContent("Test Post 3");
        post3.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        postRepository.save(post3);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setPost(post1);
        answerPost.setContent("Test Answer");
        answerPost.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        answerPostRepository.save(answerPost);

        var summary = request.get("/api/exercise/exercises/" + programmingExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(2);
        assertThat(summary.numberOfBuilds()).isEqualTo(2);
        assertThat(summary.numberOfCommunicationPosts()).isEqualTo(2);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetDeletionSummary_noChannel() throws Exception {
        // No student participations, no builds, no channel
        var summary = request.get("/api/exercise/exercises/" + programmingExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(0);
        assertThat(summary.numberOfBuilds()).isEqualTo(0);
        assertThat(summary.numberOfCommunicationPosts()).isEqualTo(0);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetDeletionSummary_editorForbidden() throws Exception {
        request.get("/api/exercise/exercises/" + programmingExercise.getId() + "/deletion-summary", HttpStatus.FORBIDDEN, ExerciseDeletionSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetDeletionSummary_instructorNotInCourseForbidden() throws Exception {
        request.get("/api/exercise/exercises/" + programmingExercise.getId() + "/deletion-summary", HttpStatus.FORBIDDEN, ExerciseDeletionSummaryDTO.class);
    }
}
