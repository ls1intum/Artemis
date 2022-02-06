package de.tum.in.www1.artemis.repository.metis;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<Post> findPostsByExerciseId(Pageable pageable, Long exerciseId);

    @Query("select distinct post from Post post where post.exercise.id = :#{#exerciseId} and not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)")
    Page<Post> findUnresolvedPostsByExerciseId(Pageable pageable, @Param("exerciseId") Long exerciseId);

    List<Post> findPostsByLectureId(Long lectureId);

    Page<Post> findPostsByLectureId(Pageable pageable, Long lectureId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId} and not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id))")
    Page<Post> findUnresolvedPostsByLectureId(Pageable pageable, @Param("lectureId") Long lectureId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( post.courseWideContext = :#{#courseWideContext} and (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ))")
    Page<Post> findPostsForCourseWideContext(Pageable pageable, @Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} )")
    List<Post> findPostsForCourse(@Param("courseId") Long courseId);

    @Query("select distinct tag from Post post left join post.tags tag left join post.lecture lecture left join post.exercise exercise where ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} )")
    List<String> findPostTagsForCourse(@Param("courseId") Long courseId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} )")
    Page<Post> findPostsForCourse(Pageable pageable, @Param("courseId") Long courseId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( post.author.id = :#{#userId} and (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ))")
    Page<Post> findOwnPostsForCourse(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    Page<Post> findUnresolvedPostsForCourse(Pageable pageable, @Param("courseId") Long courseId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( (post.author.id = :#{#userId}) and ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    Page<Post> findOwnAndUnresolvedPostsForCourse(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    Page<Post> findAnsweredOrReactedPostsByUserForCourse(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and post.author.id = :#{#userId} and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId}) or (exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findOwnAndAnsweredOrReactedPostsByUserForCourse(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForCourse(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.author.id = :#{#userId}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForCourse(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.author.id = :#{#userId} and post.lecture.id = :#{#lectureId})")
    Page<Post> findOwnPostsByLectureId(Pageable pageable, @Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    Page<Post> findAnsweredOrReactedPostsByUserByLectureId(Pageable pageable, @Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.author.id = :#{#userId} and post.lecture.id = :#{#lectureId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    Page<Post> findOwnAndUnresolvedPostsForLecture(Pageable pageable, @Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId} and post.author.id = :#{#userId}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    Page<Post> findOwnAndAnsweredOrReactedPostsByUserForLecture(Pageable pageable, @Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForLecture(Pageable pageable, @Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.lecture.id = :#{#lectureId} and post.author.id = :#{#userId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForLecture(Pageable pageable, @Param("lectureId") Long lectureId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.author.id = :#{#userId} and post.exercise.id = :#{#exerciseId})")
    Page<Post> findOwnPostsByExerciseId(Pageable pageable, @Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.exercise.id = :#{#exerciseId}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    Page<Post> findAnsweredOrReactedPostsByUserByExerciseId(Pageable pageable, @Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.author.id = :#{#userId} and post.exercise.id = :#{#exerciseId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    Page<Post> findOwnAndUnresolvedPostsByExerciseId(Pageable pageable, @Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.exercise.id = :#{#exerciseId} and post.author.id = :#{#userId}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    Page<Post> findOwnAndAnsweredOrReactedPostsByUserByExerciseId(Pageable pageable, @Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.exercise.id = :#{#exerciseId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findUnresolvedAndAnsweredOrReactedPostsByUserByExerciseId(Pageable pageable, @Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post where (post.exercise.id = :#{#exerciseId} and post.author.id = :#{#userId} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserByExerciseId(Pageable pageable, @Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and (post.courseWideContext = :#{#courseWideContext}))")
    Page<Post> findPostsForCourseByCourseWideContext(Pageable pageable, @Param("courseId") Long courseId, @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.courseWideContext = :#{#courseWideContext} and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id))))")
    Page<Post> findUnresolvedPostsForCourseByCourseWideContext(Pageable pageable, @Param("courseId") Long courseId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( post.author.id = :#{#userId} and (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and (post.courseWideContext = :#{#courseWideContext}))")
    Page<Post> findOwnPostsForCourseByCourseWideContext(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and (post.courseWideContext = :#{#courseWideContext}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId}) )")
    Page<Post> findAnsweredOrReactedPostsByUserForCourseByCourseWideContext(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where ( (post.author.id = :#{#userId}) and ( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.courseWideContext = :#{#courseWideContext}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)))")
    Page<Post> findOwnAndUnresolvedPostsForCourseByCourseWideContext(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId} ) and post.author.id = :#{#userId} and (post.courseWideContext = :#{#courseWideContext}) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId}) or (exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findOwnAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.courseWideContext = :#{#courseWideContext}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findUnresolvedAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    @Query("select distinct post from Post post left join post.lecture lecture left join post.exercise exercise where (( lecture.course.id = :#{#courseId} or exercise.course.id = :#{#courseId} or post.course.id = :#{#courseId}) and (post.author.id = :#{#userId}) and (post.courseWideContext = :#{#courseWideContext}) and (not exists (select answerPost from AnswerPost answerPost where answerPost.resolvesPost = true and answerPost.post.id = post.id)) and (exists (select answerPost from AnswerPost answerPost where answerPost.post.id = post.id and answerPost.author.id = :#{#userId} ) or exists (select reaction from Reaction reaction where reaction.post.id = post.id and reaction.user.id = :#{#userId})))")
    Page<Post> findUnresolvedAndOwnAndAnsweredOrReactedPostsByUserForCourseByCourseWideContext(Pageable pageable, @Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("courseWideContext") CourseWideContext courseWideContext);

    default Post findByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
