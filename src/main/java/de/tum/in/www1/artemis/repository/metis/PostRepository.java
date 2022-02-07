package de.tum.in.www1.artemis.repository.metis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Post entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findPostsByAuthorLogin(String login);

    List<Post> findPostsByExerciseId(Long exerciseId);

    @Query("select distinct post from Post post where post.exercise.id = :#{#exerciseId} and not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)")
    List<Post> findUnresolvedPostsByExerciseId(@Param("exerciseId") Long exerciseId);

    List<Post> findPostsByLectureId(Long lectureId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId} and not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id))")
    List<Post> findUnresolvedPostsByLectureId(@Param("lectureId") Long lectureId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( post.courseWideContext = :#{#courseWideContext} and (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ))")
    List<Post> findPostsForCourseWideContext(@Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} )")
    List<Post> findPostsForCourse(@Param("courseId") Long courseId);

    @Query("select distinct tag from Post post left join post.tags tag left join post.lecture lecture left join post.exercise exercise where ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} )")
    List<String> findPostTagsForCourse(@Param("courseId") Long courseId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( post.author.id = :#{#userId} and (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ))")
    List<Post> findOwnPostsForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    List<Post> findUnresolvedPostsForCourse(@Param("courseId") Long courseId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( (post.author.id = :#{#userId}) and ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    List<Post> findOwnAndUnresolvedPostsForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    List<Post> findAnsweredOrReactedPostsByUserForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and post.author.id = :#{#userId} and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId}) or (exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findOwnAndAnsweredOrReactedPostsByUserForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.author.id = :#{#userId}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.author.id = :#{#userId} and post.lecture.id = :#{#lectureId})")
    List<Post> findOwnPostsByLectureId(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    List<Post> findAnsweredOrReactedPostsByUserByLectureId(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.author.id = :#{#userId} and post.lecture.id = :#{#lectureId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    List<Post> findOwnAndUnresolvedPostsForLecture(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId} and post.author.id = :#{#userId}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    List<Post> findOwnAndAnsweredOrReactedPostsByUserForLecture(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForLecture(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId} and post.author.id = :#{#userId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForLecture(@Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.author.id = :#{#userId} and post.exercise.id = :#{#exerciseId})")
    List<Post> findOwnPostsByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.exercise.id = :#{#exerciseId}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    List<Post> findAnsweredOrReactedPostsByUserByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.author.id = :#{#userId} and post.exercise.id = :#{#exerciseId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    List<Post> findOwnAndUnresolvedPostsByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.exercise.id = :#{#exerciseId} and post.author.id = :#{#userId}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    List<Post> findOwnAndAnsweredOrReactedPostsByUserByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.exercise.id = :#{#exerciseId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findUnresolvedAndAnsweredOrReactedPostsByUserByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.exercise.id = :#{#exerciseId} and post.author.id = :#{#userId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserByExerciseId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and (post.courseWideContext = :#{#courseWideContext}))")
    List<Post> findPostsForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.courseWideContext = :#{#courseWideContext} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id))))")
    List<Post> findUnresolvedPostsForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( post.author.id = :#{#userId} and (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and (post.courseWideContext = :#{#courseWideContext}))")
    List<Post> findOwnPostsForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and (post.courseWideContext = :#{#courseWideContext}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    List<Post> findAnsweredOrReactedPostsByUserForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( (post.author.id = :#{#userId}) and ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.courseWideContext = :#{#courseWideContext}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    List<Post> findOwnAndUnresolvedPostsForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and post.author.id = :#{#userId} and (post.courseWideContext = :#{#courseWideContext}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId}) or (exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findOwnAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.courseWideContext = :#{#courseWideContext}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.author.id = :#{#userId}) and (post.courseWideContext = :#{#courseWideContext}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    List<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    default Post findByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
