package de.tum.in.www1.artemis;

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
    public void createStudentQuestion() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Post postToSave = new Post();
        postToSave.setContent("Test Student Question 1");
        postToSave.setVisibleForStudents(true);
        postToSave.setExercise(post.getExercise());

        Post createdPost = request.postWithResponseBody("/api/courses/" + postToSave.getCourse().getId() + "/student-questions", postToSave, Post.class, HttpStatus.CREATED);

        assertThat(createdPost).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createStudentQuestionWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Course dummyCourse = database.createCourse();
        Post postToSave = new Post();
        postToSave.setContent("Test Student Question 1");
        postToSave.setVisibleForStudents(true);
        postToSave.setExercise(post.getExercise());

        Post createdPost = request.postWithResponseBody("/api/courses/" + dummyCourse.getId() + "/student-questions", postToSave, Post.class, HttpStatus.BAD_REQUEST);

        assertThat(createdPost).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createStudentQuestionWithLectureNotNullAndExerciseNull() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Post postToSave = new Post();
        postToSave.setContent("Test Student Question 1");
        postToSave.setVisibleForStudents(true);
        Course course = post.getExercise().getCourseViaExerciseGroupOrCourseMember();
        Lecture lecture = new Lecture();
        lectureRepo.save(lecture);
        lecture.setCourse(course);
        course.addLectures(lecture);
        postToSave.setLecture(lecture);

        Post createdPost = request.postWithResponseBody("/api/courses/" + postToSave.getCourse().getId() + "/student-questions", postToSave, Post.class, HttpStatus.CREATED);

        assertThat(createdPost).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExistingStudentQuestion() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);

        request.postWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions", post, Post.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestion_asInstructor() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);

        post.setVisibleForStudents(false);
        post.setContent("New Test Student Question");

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions", post, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getContent()).isEqualTo("New Test Student Question");
        assertThat(updatedPost.isVisibleForStudents()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestionWithIdIsNull() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        post.setId(null);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions", post, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestionWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Course dummyCourse = database.createCourse();

        Post updatedPost = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/student-questions", post, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void editStudentQuestion_asTA() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);

        post.setVisibleForStudents(false);
        post.setContent("New Test Student Question");

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions", post, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getContent()).isEqualTo("New Test Student Question");
        assertThat(updatedPost.isVisibleForStudents()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void editStudentQuestion_asStudent() throws Exception {
        List<Post> questions = database.createCourseWithExerciseAndStudentQuestions();
        Post studentQuestion_student1 = questions.get(0);
        Post studentQuestion_student2 = questions.get(1);

        // update own question --> OK
        studentQuestion_student1.setVisibleForStudents(false);
        studentQuestion_student1.setContent("New Test Student Question");
        Post updatedPost1 = request.putWithResponseBody("/api/courses/" + studentQuestion_student1.getCourse().getId() + "/student-questions", studentQuestion_student1, Post.class,
                HttpStatus.OK);
        assertThat(updatedPost1.getContent()).isEqualTo("New Test Student Question");
        assertThat(updatedPost1.isVisibleForStudents()).isFalse();

        // update question from another student --> forbidden
        studentQuestion_student2.setVisibleForStudents(false);
        studentQuestion_student2.setContent("New Test Student Question");
        Post updatedPost2 = request.putWithResponseBody("/api/courses/" + studentQuestion_student2.getCourse().getId() + "/student-questions", studentQuestion_student2, Post.class,
                HttpStatus.FORBIDDEN);
        assertThat(updatedPost2).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllStudentQuestionsForExercise() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Long exerciseID = post.getExercise().getId();

        List<Post> returnedPosts = request.getList("/api/courses/" + post.getCourse().getId() + "/exercises/" + exerciseID + "/student-questions", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllStudentQuestionsForExerciseWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Long exerciseID = post.getExercise().getId();
        Course dummyCourse = database.createCourse();

        List<Post> returnedPosts = request.getList("/api/courses/" + dummyCourse.getId() + "/exercises/" + exerciseID + "/student-questions", HttpStatus.BAD_REQUEST, Post.class);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllStudentQuestionsForLecture() throws Exception {
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

        Post post1 = database.createCourseWithExerciseAndStudentQuestions().get(0);
        post1.setLecture(lecture1);
        Post post2 = database.createCourseWithExerciseAndStudentQuestions().get(0);
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

        List<Post> returnedPosts = request.getList("/api/courses/" + post1.getCourse().getId() + "/lectures/" + lecture1.getId() + "/student-questions", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllStudentQuestionsForLectureWithWrongCourseId() throws Exception {
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

        Post post1 = database.createCourseWithExerciseAndStudentQuestions().get(0);
        post1.setLecture(lecture1);
        Post post2 = database.createCourseWithExerciseAndStudentQuestions().get(0);
        post2.setLecture(lecture1);
        postRepository.save(post1);
        postRepository.save(post2);

        List<Post> returnedPosts = request.getList("/api/courses/" + dummyCourse.getId() + "/lectures/" + lecture1.getId() + "/student-questions", HttpStatus.BAD_REQUEST,
                Post.class);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteStudentQuestions_asInstructor() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndStudentQuestions();
        Post post = posts.get(0);
        Post post1 = posts.get(1);

        request.delete("/api/courses/" + post.getCourse().getId() + "/student-questions/" + post.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(1);

        // try to delete not existing question
        request.delete("/api/courses/" + post.getCourse().getId() + "/student-questions/999", HttpStatus.NOT_FOUND);

        // delete question with no lecture id --> OK
        post1.setLecture(null);
        postRepository.save(post1);
        request.delete("/api/courses/" + post1.getCourse().getId() + "/student-questions/" + post1.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteStudentQuestionAnswerWithCourseNull() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndStudentQuestions();
        Post post = posts.get(0);
        Long courseId = post.getCourse().getId();
        post.setLecture(null);
        post.setExercise(null);
        postRepository.save(post);

        request.delete("/api/courses/" + courseId + "/student-questions/" + post.getId(), HttpStatus.BAD_REQUEST);
        assertThat(postRepository.findById(post.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteStudentQuestionAnswerWithLectureNotNullAndExerciseNull() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndStudentQuestions();
        Post post = posts.get(0);
        Lecture notNullLecture = new Lecture();
        notNullLecture.setCourse(post.getCourse());
        post.setLecture(notNullLecture);
        lectureRepo.save(notNullLecture);
        post.setExercise(null);
        postRepository.save(post);

        request.delete("/api/courses/" + post.getCourse().getId() + "/student-questions/" + post.getId(), HttpStatus.OK);
        assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteStudentQuestions_asTA() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndStudentQuestions();
        Post studentQuestion_student1 = posts.get(0);
        Post studentQuestion1_student2 = posts.get(1);

        // delete own question --> OK
        request.delete("/api/courses/" + studentQuestion_student1.getCourse().getId() + "/student-questions/" + studentQuestion_student1.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(1);

        // delete question from another user --> OK
        request.delete("/api/courses/" + studentQuestion1_student2.getCourse().getId() + "/student-questions/" + studentQuestion1_student2.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteStudentQuestions_asStudent() throws Exception {
        List<Post> posts = database.createCourseWithExerciseAndStudentQuestions();
        Post studentQuestion_student1 = posts.get(0);
        Post studentQuestion1_student2 = posts.get(1);

        // delete own question --> OK
        request.delete("/api/courses/" + studentQuestion_student1.getCourse().getId() + "/student-questions/" + studentQuestion_student1.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(1);

        // delete question from another student --> forbidden
        request.delete("/api/courses/" + studentQuestion1_student2.getCourse().getId() + "/student-questions/" + studentQuestion1_student2.getId(), HttpStatus.FORBIDDEN);
        assertThat(postRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestionVotes_asInstructor() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions/" + post.getId() + "/votes", 1, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestionVotesToInvalidAmount() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions/" + post.getId() + "/votes", 3, Post.class,
                HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
        updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions/" + post.getId() + "/votes", -3, Post.class,
                HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void editStudentQuestionVotesWithWrongCourseId() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);
        Course dummyCourse = database.createCourse();

        Post updatedPost = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/student-questions/" + post.getId() + "/votes", 1, Post.class,
                HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void editStudentQuestionVotes_asTA() throws Exception {
        Post post = database.createCourseWithExerciseAndStudentQuestions().get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions/" + post.getId() + "/votes", -1, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(-1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void editStudentQuestionVotes_asStudent() throws Exception {
        List<Post> questions = database.createCourseWithExerciseAndStudentQuestions();
        Post post = questions.get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + post.getCourse().getId() + "/student-questions/" + post.getId() + "/votes", 2, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAllStudentQuestionsForCourse() throws Exception {
        Post post = database.createCourseWithExerciseAndLectureAndStudentQuestions().get(0);
        Long courseID = post.getCourse().getId();

        List<Post> returnedPosts = request.getList("/api/courses/" + courseID + "/student-questions", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(4);
    }
}
