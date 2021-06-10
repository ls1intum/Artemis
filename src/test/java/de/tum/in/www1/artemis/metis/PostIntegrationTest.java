package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.metis.PostRepository;

public class PostIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PostRepository postRepository;

    private List<Post> existingPosts;

    private List<Post> existingExercisePosts;

    private List<Post> existingLecturePosts;

    private List<Post> existingCoursePosts;

    private Long courseId;

    private Long exerciseId;

    private Long lectureId;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(5, 5, 0, 1);

        // get all existing posts
        existingPosts = database.createPostsWithinCourse();

        // filter existing posts with exercise context
        existingExercisePosts = existingPosts.stream().filter(coursePost -> (coursePost.getExercise() != null)).collect(Collectors.toList());

        // filter existing posts with lecture context
        existingLecturePosts = existingPosts.stream().filter(coursePost -> (coursePost.getLecture() != null)).collect(Collectors.toList());

        // filter existing posts with course-wide context
        existingCoursePosts = existingPosts.stream().filter(coursePost -> (coursePost.getCourseWideContext() != null)).collect(Collectors.toList());

        courseId = existingPosts.get(0).getCourse().getId();

        exerciseId = existingExercisePosts.get(0).getExercise().getId();

        lectureId = existingLecturePosts.get(0).getLecture().getId();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateExercisePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setExercise(existingExercisePosts.get(0).getExercise());

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        checkPost(postToSave, createdPost);

        assertThat(existingExercisePosts.size() + 1).isEqualTo(postRepository.findPostsByExercise_Id(exerciseId).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateLecturePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setLecture(existingLecturePosts.get(0).getLecture());

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        checkPost(postToSave, createdPost);

        assertThat(existingLecturePosts.size() + 1).isEqualTo(postRepository.findPostsForLecture(lectureId).size());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateCoursePost() throws Exception {
        Post postToSave = createPostWithoutContext();
        postToSave.setCourse(existingCoursePosts.get(0).getCourse());
        postToSave.setCourseWideContext(existingCoursePosts.get(0).getCourseWideContext());

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        checkPost(postToSave, createdPost);

        List<Post> updatedCoursePosts = postRepository.findPostsForCourse(courseId).stream().filter(post -> post.getCourseWideContext() != null).collect(Collectors.toList());

        assertThat(existingCoursePosts.size() + 1).isEqualTo(updatedCoursePosts.size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreatePostWithWrongCourseId() throws Exception {
        Course dummyCourse = database.createCourse();
        Post postToSave = createPostWithoutContext();
        postToSave.setExercise(existingPosts.get(0).getExercise());

        Post createdPost = request.postWithResponseBody("/api/courses/" + dummyCourse.getId() + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);

        assertThat(createdPost).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateExistingPost() throws Exception {
        Post postToSave = existingPosts.get(0);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostWithIdIsNull() throws Exception {
        Post postToUpdate = existingPosts.get(0);
        postToUpdate.setId(null);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToUpdate, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostWithWrongCourseId() throws Exception {
        Post postToUpdate = existingPosts.get(0);
        Course dummyCourse = database.createCourse();

        Post updatedPost = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/posts", postToUpdate, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testEditPost_asTA() throws Exception {
        // update post of student1 (index 0)--> OK
        Post postToUpdate = editExistingPost(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToUpdate, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getContent()).isEqualTo("New Test Post");
        assertThat(postToUpdate).isEqualTo(postToUpdate);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditPost_asStudent() throws Exception {
        // update own post (index 0)--> OK
        Post postToUpdate = editExistingPost(0);
        Post updatedPost1 = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToUpdate, Post.class, HttpStatus.OK);
        assertThat(updatedPost1).isEqualTo(postToUpdate);

        // update post from another student (index 1)--> forbidden
        Post postToNotUpdate = editExistingPost(1);
        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts", postToNotUpdate, Post.class, HttpStatus.FORBIDDEN);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllPostsForExercise() throws Exception {
        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/exercises/" + exerciseId + "/posts", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(existingExercisePosts.size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllPostsForExerciseWithWrongCourseId() throws Exception {
        Course dummyCourse = database.createCourse();

        List<Post> returnedPosts = request.getList("/api/courses/" + dummyCourse.getId() + "/exercises/" + exerciseId + "/posts", HttpStatus.BAD_REQUEST, Post.class);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllPostsForLecture() throws Exception {
        Post post = existingLecturePosts.get(0);
        Long lectureId = post.getLecture().getId();

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/lectures/" + lectureId + "/posts", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(existingLecturePosts.size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllPostsForLectureWithWrongCourseId() throws Exception {
        Course dummyCourse = database.createCourse();

        List<Post> returnedPosts = request.getList("/api/courses/" + dummyCourse.getId() + "/lectures/" + lectureId + "/posts", HttpStatus.BAD_REQUEST, Post.class);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeletePosts_asTA() throws Exception {
        Post postToDelete = existingLecturePosts.get(0);

        request.delete("/api/courses/" + courseId + "/posts/" + postToDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 1);

        postToDelete = existingExercisePosts.get(0);
        request.delete("/api/courses/" + courseId + "/posts/" + postToDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 2);

        postToDelete = existingCoursePosts.get(0);
        request.delete("/api/courses/" + courseId + "/posts/" + postToDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 3);

        // try to delete not existing post
        request.delete("/api/courses/" + courseId + "/posts/999", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeletePosts_asStudent() throws Exception {
        Post post_student1 = existingPosts.get(0);
        Post post1_student2 = existingPosts.get(1);

        // delete own post --> OK
        request.delete("/api/courses/" + courseId + "/posts/" + post_student1.getId(), HttpStatus.OK);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 1);

        // delete post from another student --> forbidden
        request.delete("/api/courses/" + courseId + "/posts/" + post1_student2.getId(), HttpStatus.FORBIDDEN);
        assertThat(postRepository.count()).isEqualTo(existingPosts.size() - 1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostVotes_asInstructor() throws Exception {
        Post post = existingPosts.get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + post.getId() + "/votes", 1, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostVotesToInvalidAmount() throws Exception {
        Post post = existingPosts.get(0);
        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + post.getId() + "/votes", 3, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
        updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + post.getId() + "/votes", -3, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testEditPostVotesWithWrongCourseId() throws Exception {
        Post post = existingPosts.get(0);
        Course dummyCourse = database.createCourse();

        Post updatedPost = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/posts/" + post.getId() + "/votes", 1, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testEditPostVotes_asTA() throws Exception {
        Post post = existingExercisePosts.get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + post.getId() + "/votes", 2, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testEditPostVotes_asStudent() throws Exception {
        Post post = existingLecturePosts.get(0);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + post.getId() + "/votes", 2, Post.class, HttpStatus.OK);
        assertThat(updatedPost.getVotes()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetPostsForCourse() throws Exception {
        Long courseId = existingPosts.get(0).getCourse().getId();

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class);
        assertThat(returnedPosts.size()).isEqualTo(existingPosts.size());
    }

    private Post createPostWithoutContext() {
        Post post = new Post();
        post.setTitle("Title Post");
        post.setContent("Content Post");
        post.setVisibleForStudents(true);
        post.setAuthor(database.getUserByLoginWithoutAuthorities("student1"));
        String tag = "Tag";
        Set<String> tags = new HashSet<>();
        tags.add(tag);
        post.setTags(tags);
        return post;
    }

    private Post editExistingPost(Integer index) {
        Post postToUpdate = existingPosts.get(index);
        postToUpdate.setTitle("New Title");
        postToUpdate.setContent("New Test Post");
        postToUpdate.setVisibleForStudents(false);
        Reaction newReaction = new Reaction();
        newReaction.setEmojiId("apple");
        List<Reaction> reactionsToUpdate = postToUpdate.getReactions();
        reactionsToUpdate.add(newReaction);
        postToUpdate.setReactions(reactionsToUpdate);
        String newTag = "New Tag";
        Set<String> tagsToUpdate = postToUpdate.getTags();
        tagsToUpdate.add(newTag);
        postToUpdate.setTags(tagsToUpdate);
        return postToUpdate;
    }

    private void checkPost(Post expectedPost, Post createdPost) {
        // check if post was created with id
        assertThat(createdPost).isNotNull();
        assertThat(createdPost.getId()).isNotNull();

        // check if title, content, author are correct on creation
        assertThat(createdPost.getTitle()).isEqualTo(expectedPost.getTitle());
        assertThat(createdPost.getContent()).isEqualTo(expectedPost.getContent());
        assertThat(createdPost.getTags()).isEqualTo(expectedPost.getTags());

        // check default values after creation
        assertThat(createdPost.getAnswers()).isEmpty();
        assertThat(createdPost.getVotes()).isEqualTo(0);
        assertThat(createdPost.getReactions()).isEmpty();

        // check context, i.e. either correct lecture, exercise or course-wide context
        assertThat(createdPost.getExercise()).isEqualTo(expectedPost.getExercise());
        assertThat(createdPost.getLecture()).isEqualTo(expectedPost.getLecture());
        assertThat(createdPost.getCourse()).isEqualTo(expectedPost.getCourse());
        assertThat(createdPost.getCourseWideContext()).isEqualTo(expectedPost.getCourseWideContext());
    }
}
