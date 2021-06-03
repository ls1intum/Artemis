package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;

public class AnswerPostIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(5, 5, 0, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateAnswerPostAsInstructor() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("instructor1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now());
        answerPost.setPost(post);
        AnswerPost response = request.postWithResponseBody("/api/courses/" + post.getCourse().getId() + "/answer-posts", answerPost, AnswerPost.class, HttpStatus.CREATED);

        // should be automatically approved
        assertThat(response.isTutorApproved()).isTrue();
        // trying to create same answerPost again --> bad request
        request.postWithResponseBody("/api/courses/" + post.getCourse().getId() + "/answer-posts", response, AnswerPost.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateAnswerPostWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Course courseDummy = database.createCourse();

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("instructor1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now());
        answerPost.setPost(post);

        AnswerPost response = request.postWithResponseBody("/api/courses/" + courseDummy.getId() + "/answer-posts", answerPost, AnswerPost.class, HttpStatus.BAD_REQUEST);

        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateAnswerPostWithLectureNotNullAndExerciseNull() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndLectureAndPosts();
        Post post = posts.get(0);
        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("instructor1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now());
        answerPost.setPost(post);
        Lecture lecture = new Lecture();
        lecture.setCourse(post.getCourse());
        // this basically moves the post from the exercise to the lecture
        post.setLecture(lecture);
        post.setExercise(null);
        lectureRepository.save(lecture);
        postRepository.save(post);
        // remove some values not required for the json
        var course = post.getCourse();
        course.setExercises(Set.of());
        course.setLectures(Set.of());
        AnswerPost response = request.postWithResponseBody("/api/courses/" + course.getId() + "/answer-posts", answerPost, AnswerPost.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCreateAnswerPost_asTA() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("tutor1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now());
        answerPost.setPost(post);
        AnswerPost response = request.postWithResponseBody("/api/courses/" + post.getCourse().getId() + "/answer-posts", answerPost, AnswerPost.class, HttpStatus.CREATED);

        // shouldn't be automatically approved
        assertThat(response.isTutorApproved()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateAnswerPost_asStudent() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now());
        answerPost.setPost(post);
        AnswerPost response = request.postWithResponseBody("/api/courses/" + post.getCourse().getId() + "/answer-posts", answerPost, AnswerPost.class, HttpStatus.CREATED);

        // shouldn't be automatically approved
        assertThat(response.isTutorApproved()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditAnswerPost_asInstructor() throws Exception {
        AnswerPost answerPost = createAnswerPostsOnServer().get(0);

        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("tutor2"));
        answerPost.setContent("New Answer Text");
        answerPost.setCreationDate(ZonedDateTime.now().minusHours(1));
        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + answerPost.getPost().getCourse().getId() + "/answer-posts", answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(updatedAnswerPostServer).isEqualTo(answerPost);

        // try to update answer which is not yet on the server (no id) --> bad request
        AnswerPost newAnswerPost = new AnswerPost();
        AnswerPost newAnswerPostServer = request.putWithResponseBody("/api/courses/" + answerPost.getPost().getCourse().getId() + "/answer-posts", newAnswerPost, AnswerPost.class,
                HttpStatus.BAD_REQUEST);
        assertThat(newAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditAnswerPostWithWrongCourseId() throws Exception {
        AnswerPost answerPost = createAnswerPostsOnServer().get(0);
        Course courseDummy = database.createCourse();

        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("tutor2"));
        answerPost.setContent("New Answer Text");
        answerPost.setCreationDate(ZonedDateTime.now().minusHours(1));
        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + courseDummy.getId() + "/answer-posts", answerPost, AnswerPost.class,
                HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testEditAnswerPost_asTA() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        AnswerPost answerPost_tutor1 = answers.get(0);
        AnswerPost answerPost_tutor2 = answers.get(1);
        AnswerPost answerPost_student1 = answers.get(2);

        // edit own answer --> OK
        answerPost_tutor1.setContent("New Answer Text");
        answerPost_tutor1.setCreationDate(ZonedDateTime.now().minusHours(1));
        AnswerPost updatedAnswerPostServer1 = request.putWithResponseBody("/api/courses/" + answerPost_tutor1.getPost().getCourse().getId() + "/answer-posts", answerPost_tutor1,
                AnswerPost.class, HttpStatus.OK);
        assertThat(updatedAnswerPostServer1).isEqualTo(answerPost_tutor1);

        // edit answer of other TA --> OK
        answerPost_tutor2.setContent("New Answer Text");
        answerPost_tutor2.setCreationDate(ZonedDateTime.now().minusHours(1));
        AnswerPost updatedAnswerPostServer2 = request.putWithResponseBody("/api/courses/" + answerPost_tutor2.getPost().getCourse().getId() + "/answer-posts", answerPost_tutor2,
                AnswerPost.class, HttpStatus.OK);
        assertThat(updatedAnswerPostServer2).isEqualTo(answerPost_tutor2);

        // edit answer of other student --> OK
        answerPost_student1.setContent("New Answer Text");
        answerPost_student1.setCreationDate(ZonedDateTime.now().minusHours(1));
        AnswerPost updatedAnswerPostServer3 = request.putWithResponseBody("/api/courses/" + answerPost_student1.getPost().getCourse().getId() + "/answer-posts",
                answerPost_student1, AnswerPost.class, HttpStatus.OK);
        assertThat(updatedAnswerPostServer3).isEqualTo(answerPost_student1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditAnswerPost_asStudent() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        AnswerPost answerPost_tutor1 = answers.get(0);
        AnswerPost answerPost_student1 = answers.get(2);

        // update own answer --> OK
        answerPost_student1.setContent("New Answer Text");
        answerPost_student1.setCreationDate(ZonedDateTime.now().minusHours(1));
        AnswerPost updatedAnswerPostServer1 = request.putWithResponseBody("/api/courses/" + answerPost_student1.getPost().getCourse().getId() + "/answer-posts",
                answerPost_student1, AnswerPost.class, HttpStatus.OK);
        assertThat(updatedAnswerPostServer1).isEqualTo(answerPost_student1);

        // update answer of other user --> forbidden
        answerPost_tutor1.setContent("New Answer Text");
        answerPost_tutor1.setCreationDate(ZonedDateTime.now().minusHours(1));
        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + answerPost_tutor1.getPost().getCourse().getId() + "/answer-posts", answerPost_tutor1,
                AnswerPost.class, HttpStatus.FORBIDDEN);
        assertThat(updatedAnswerPostServer).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteAnswerPost_asInstructor() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        AnswerPost answerPost_tutor1 = answers.get(0);
        AnswerPost answerPost_tutor2 = answers.get(1);

        request.delete("/api/courses/" + answerPost_tutor1.getPost().getCourse().getId() + "/answer-posts/" + answerPost_tutor1.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.findById(answerPost_tutor1.getId())).isEmpty();

        // try to delete not existing answer --> not found
        request.delete("/api/courses/" + answerPost_tutor1.getPost().getCourse().getId() + "/answer-posts/999", HttpStatus.NOT_FOUND);

        // delete answer without lecture id --> OK
        Post post = answerPost_tutor2.getPost();
        post.setLecture(null);
        postRepository.save(post);
        request.delete("/api/courses/" + answerPost_tutor2.getPost().getCourse().getId() + "/answer-posts/" + answerPost_tutor2.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.findById(answerPost_tutor2.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteAnswerPostWithWrongCourseId() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        Course dummyCourse = database.createCourse();
        AnswerPost answerPost_tutor1 = answers.get(0);

        request.delete("/api/courses/" + dummyCourse.getId() + "/answer-posts/" + answerPost_tutor1.getId(), HttpStatus.BAD_REQUEST);
        assertThat(answerPostRepository.findById(answerPost_tutor1.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteAnswerPostWithLectureNotNullAndExerciseNull() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        AnswerPost answerPost = answers.get(0);
        Course course = answerPost.getPost().getCourse();
        answerPost.getPost().setExercise(null);
        Lecture notNullLecture = new Lecture();
        notNullLecture.setCourse(course);
        lectureRepository.save(notNullLecture);
        answerPost.getPost().setLecture(notNullLecture);
        postRepository.save(answerPost.getPost());

        request.delete("/api/courses/" + course.getId() + "/answer-posts/" + answerPost.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.findById(answerPost.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteAnswerPostWithWithCourseNull() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        AnswerPost answerPost = answers.get(0);
        Course course = answerPost.getPost().getCourse();
        answerPost.getPost().setExercise(null);
        postRepository.save(answerPost.getPost());

        request.delete("/api/courses/" + course.getId() + "/answer-posts/" + answerPost.getId(), HttpStatus.BAD_REQUEST);
        assertThat(answerPostRepository.findById(answerPost.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteAnswerPost_AsTA() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        AnswerPost answerPost_tutor1 = answers.get(0);
        AnswerPost answerPost_student2 = answers.get(3);

        // delete own answer --> OK
        request.delete("/api/courses/" + answerPost_tutor1.getPost().getCourse().getId() + "/answer-posts/" + answerPost_tutor1.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.findById(answerPost_tutor1.getId())).isEmpty();

        // delete answer of other student --> OK
        request.delete("/api/courses/" + answerPost_student2.getPost().getCourse().getId() + "/answer-posts/" + answerPost_student2.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.findById(answerPost_student2.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeleteAnswerPost_AsStudent() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        AnswerPost answerPost_student1 = answers.get(2);
        AnswerPost answerPost_student2 = answers.get(3);

        // delete own answer --> OK
        request.delete("/api/courses/" + answerPost_student1.getPost().getCourse().getId() + "/answer-posts/" + answerPost_student1.getId(), HttpStatus.OK);
        assertThat(answerPostRepository.findById(answerPost_student1.getId())).isEmpty();

        // delete answer of other student --> forbidden
        request.delete("/api/courses/" + answerPost_student2.getPost().getCourse().getId() + "/answer-posts/" + answerPost_student2.getId(), HttpStatus.FORBIDDEN);
        assertThat(answerPostRepository.findById(answerPost_student2.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testToggleAnswerPostApproved() throws Exception {
        List<AnswerPost> answers = createAnswerPostsOnServer();
        AnswerPost answerPost = answers.get(0);

        // approve answer
        answerPost.setTutorApproved(true);
        AnswerPost updatedAnswerPost1 = request.putWithResponseBody("/api/courses/" + answerPost.getPost().getCourse().getId() + "/answer-posts", answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(updatedAnswerPost1).isEqualTo(answerPost);

        // unapprove answer
        answerPost.setTutorApproved(false);
        AnswerPost updatedAnswerPost2 = request.putWithResponseBody("/api/courses/" + answerPost.getPost().getCourse().getId() + "/answer-posts", answerPost, AnswerPost.class,
                HttpStatus.OK);
        assertThat(updatedAnswerPost2).isEqualTo(answerPost);
    }

    private List<AnswerPost> createAnswerPostsOnServer() {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        List<AnswerPost> answers = new ArrayList<>();

        AnswerPost answerPost = new AnswerPost();
        answerPost.setAuthor(database.getUserByLoginWithoutAuthorities("tutor1"));
        answerPost.setContent("Test Answer");
        answerPost.setCreationDate(ZonedDateTime.now());
        answerPost.setPost(post);
        answerPostRepository.save(answerPost);
        answers.add(answerPost);

        AnswerPost answerPost1 = new AnswerPost();
        answerPost1.setAuthor(database.getUserByLoginWithoutAuthorities("tutor2"));
        answerPost1.setContent("Test Answer");
        answerPost1.setCreationDate(ZonedDateTime.now());
        answerPost1.setPost(post);
        answerPostRepository.save(answerPost1);
        answers.add(answerPost1);

        AnswerPost answerPost2 = new AnswerPost();
        answerPost2.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        answerPost2.setContent("Test Answer");
        answerPost2.setCreationDate(ZonedDateTime.now());
        answerPost2.setPost(post);
        answerPostRepository.save(answerPost2);
        answers.add(answerPost2);

        AnswerPost answerPost3 = new AnswerPost();
        answerPost3.setAuthor(database.getUserByLoginWithoutAuthorities("student2"));
        answerPost3.setContent("Test Answer");
        answerPost3.setCreationDate(ZonedDateTime.now());
        answerPost3.setPost(post);
        answerPostRepository.save(answerPost3);
        answers.add(answerPost3);

        return answers;
    }
}
