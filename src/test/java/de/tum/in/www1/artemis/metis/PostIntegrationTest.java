package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class PostIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private UserRepository userRepository;

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
    public void testCreatePost() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Post postToSave = new Post();
        postToSave.setContent("Test Post 1");
        postToSave.setVisibleForStudents(true);
        postToSave.setExercise(post.getExercise());

        Post createdPost = request.postWithResponseBody("/api/courses/" + postToSave.getCourse().getId() + "/posts", postToSave, Post.class, HttpStatus.CREATED);

        assertThat(createdPost).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreatePostWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Course dummyCourse = database.createCourse();
        Post postToSave = new Post();
        postToSave.setContent("Test Post 1");
        postToSave.setVisibleForStudents(true);
        postToSave.setExercise(post.getExercise());

        Post createdPost = request.postWithResponseBody("/api/courses/" + dummyCourse.getId() + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);

        assertThat(createdPost).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreatePostWithLectureNotNullAndExerciseNull() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Post postToSave = new Post();
        postToSave.setContent("Test Post 1");
        postToSave.setVisibleForStudents(true);
        Course course = post.getExercise().getCourseViaExerciseGroupOrCourseMember();
        Lecture lecture = new Lecture();
        lectureRepo.save(lecture);
        lecture.setCourse(course);
        course.addLectures(lecture);
        postToSave.setLecture(lecture);

        Post createdPost = request.postWithResponseBody("/api/courses/" + postToSave.getCourse().getId() + "/posts", postToSave, Post.class, HttpStatus.CREATED);

        assertThat(createdPost).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateExistingPost() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);

        request.postWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts", post, Post.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPost_asInstructor() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);

        post.setVisibleForStudents(false);
        post.setContent("New Test Post");

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts", post, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getContent()).isEqualTo("New Test Post");
        assertThat(updatedPost.isVisibleForStudents()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostWithIdIsNull() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        post.setId(null);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts", post, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Course dummyCourse = database.createCourse();

        Post updatedPost = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/posts", post, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testEditPost_asTA() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);

        post.setVisibleForStudents(false);
        post.setContent("New Test Post");

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts", post, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getContent()).isEqualTo("New Test Post");
        assertThat(updatedPost.isVisibleForStudents()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditPost_asStudent() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndPosts();
        Post post_student1 = posts.get(0);
        Post post_student2 = posts.get(1);

        // update own post --> OK
        post_student1.setVisibleForStudents(false);
        post_student1.setContent("New Test Post");
        Post updatedPost1 = request.putWithResponseBody("/api/courses/" + post_student1.getCourse().getId() + "/posts", post_student1, Post.class, HttpStatus.OK);
        assertThat(updatedPost1.getContent()).isEqualTo("New Test Post");
        assertThat(updatedPost1.isVisibleForStudents()).isFalse();

        // update post from another student --> forbidden
        post_student2.setVisibleForStudents(false);
        post_student2.setContent("New Test Post");
        Post updatedPost2 = request.putWithResponseBody("/api/courses/" + post_student2.getCourse().getId() + "/posts", post_student2, Post.class, HttpStatus.FORBIDDEN);
        assertThat(updatedPost2).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllPostsForExercise() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Long exerciseID = post.getExercise().getId();

        List<Post> returnedPosts = request.getList("/api/courses/" + post.getCourse().getId() + "/exercises/" + exerciseID + "/posts", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllPostsForExerciseWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Long exerciseID = post.getExercise().getId();
        Course dummyCourse = database.createCourse();

        List<Post> returnedPosts = request.getList("/api/courses/" + dummyCourse.getId() + "/exercises/" + exerciseID + "/posts", HttpStatus.BAD_REQUEST, Post.class);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllPostsForLecture() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        Lecture lecture1 = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment1 = ModelFactory.generateAttachment(pastTimestamp, lecture1);
        lecture1.addAttachments(attachment1);
        courseRepo.save(course1);
        lectureRepo.save(lecture1);
        attachmentRepo.save(attachment1);

        Post post1 = database.createCourseWithExerciseAndPosts().get(0);
        post1.setLecture(lecture1);
        Post post2 = database.createCourseWithExerciseAndPosts().get(0);
        post2.setLecture(lecture1);
        AnswerPost answerPost1 = new AnswerPost();
        answerPost1.setPost(post1);
        User author = new User();
        author.setFirstName("Test");
        author.setLogin("loginTest");
        answerPost1.setAuthor(author);
        Set<AnswerPost> answers1 = new HashSet<>();
        answers1.add(answerPost1);
        post1.setAnswers(answers1);
        postRepository.save(post1);
        userRepository.save(author);
        postRepository.save(post2);
        answerPostRepository.save(answerPost1);

        List<Post> returnedPosts = request.getList("/api/courses/" + post1.getCourse().getId() + "/lectures/" + lecture1.getId() + "/posts", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllPostsForLectureWithWrongCourseId() throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Course dummyCourse = database.createCourse();
        Lecture lecture1 = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment1 = ModelFactory.generateAttachment(pastTimestamp, lecture1);
        lecture1.addAttachments(attachment1);
        courseRepo.save(course1);
        lectureRepo.save(lecture1);
        attachmentRepo.save(attachment1);

        Post post1 = database.createCourseWithExerciseAndPosts().get(0);
        post1.setLecture(lecture1);
        Post post2 = database.createCourseWithExerciseAndPosts().get(0);
        post2.setLecture(lecture1);
        postRepository.save(post1);
        postRepository.save(post2);

        List<Post> returnedPosts = request.getList("/api/courses/" + dummyCourse.getId() + "/lectures/" + lecture1.getId() + "/posts", HttpStatus.BAD_REQUEST, Post.class);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeletePosts_asInstructor() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndPosts();
        Post post = posts.get(0);
        Post post1 = posts.get(1);

        request.delete("/api/courses/" + post.getCourse().getId() + "/posts/" + post.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(1);

        // try to delete not existing post
        request.delete("/api/courses/" + post.getCourse().getId() + "/posts/999", HttpStatus.NOT_FOUND);

        // delete post with no lecture id --> OK
        post1.setLecture(null);
        postRepository.save(post1);
        request.delete("/api/courses/" + post1.getCourse().getId() + "/posts/" + post1.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeletePostAnswerWithCourseNull() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndPosts();
        Post post = posts.get(0);
        Long courseId = post.getCourse().getId();
        post.setLecture(null);
        post.setExercise(null);
        postRepository.save(post);

        request.delete("/api/courses/" + courseId + "/posts/" + post.getId(), HttpStatus.BAD_REQUEST);
        assertThat(postRepository.findById(post.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeletePostWithLectureNotNullAndExerciseNull() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndPosts();
        Post post = posts.get(0);
        Lecture notNullLecture = new Lecture();
        notNullLecture.setCourse(post.getCourse());
        post.setLecture(notNullLecture);
        lectureRepo.save(notNullLecture);
        post.setExercise(null);
        postRepository.save(post);

        request.delete("/api/courses/" + post.getCourse().getId() + "/posts/" + post.getId(), HttpStatus.OK);
        assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeletePosts_asTA() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndPosts();
        Post post_student1 = posts.get(0);
        Post post1_student2 = posts.get(1);

        // delete own post --> OK
        request.delete("/api/courses/" + post_student1.getCourse().getId() + "/posts/" + post_student1.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(1);

        // delete post from another user --> OK
        request.delete("/api/courses/" + post1_student2.getCourse().getId() + "/posts/" + post1_student2.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeletePosts_asStudent() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndPosts();
        Post post_student1 = posts.get(0);
        Post post1_student2 = posts.get(1);

        // delete own post --> OK
        request.delete("/api/courses/" + post_student1.getCourse().getId() + "/posts/" + post_student1.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(1);

        // delete post from another student --> forbidden
        request.delete("/api/courses/" + post1_student2.getCourse().getId() + "/posts/" + post1_student2.getId(), HttpStatus.FORBIDDEN);
        assertThat(postRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostVotes_asInstructor() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts/" + post.getId() + "/votes", 1, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostVotesToInvalidAmount() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts/" + post.getId() + "/votes", 3, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
        updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts/" + post.getId() + "/votes", -3, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostVotesWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);
        Course dummyCourse = database.createCourse();

        Post updatedPost = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/posts/" + post.getId() + "/votes", 1, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testEditPostVotes_asTA() throws Exception {
        Post post = database.createCourseWithExerciseAndPosts().get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts/" + post.getId() + "/votes", -1, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(-1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditPostVotes_asStudent() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndPosts();
        Post post = posts.get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/posts/" + post.getId() + "/votes", 2, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetPostsForCourse() throws Exception {
        Post post = database.createCourseWithExerciseAndLectureAndPosts().get(0);
        Long courseID = post.getCourse().getId();

        List<Post> returnedPosts = request.getList("/api/courses/" + courseID + "/posts", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(4);
    }
}
