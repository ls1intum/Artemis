package de.tum.in.www1.artemis.repository.metis;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

import static de.tum.in.www1.artemis.repository.specs.PostSpecs.*;

/**
 * Spring Data repository for the Post entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    List<Post> findPostsByAuthorLogin(String login);

    /**
     * Generates SQL Query via specifications to filter and sort Posts
     * @param postContextFilter filtering and sorting properties for Posts
     * @param userId            id of the user performing the call, needed on certain filters
     * @param pagingEnabled     whether a page of posts or all posts will be fetched
     * @param pageable          paging object which contains the page number and number of records to fetch
     * @return  returns a Page of Posts or all Posts within a Page, which is treated as a List by the client.
     */
    default Page<Post> findPosts(PostContextFilter postContextFilter, Long userId, boolean pagingEnabled, Pageable pageable) {
        Specification<Post> specification = Specification.where(distinct())
                .and(getCourseSpecification(postContextFilter.getCourseId(), postContextFilter.getLectureId(), postContextFilter.getExerciseId())
                        .and(getLectureSpecification(postContextFilter.getLectureId()).and(getExerciseSpecification(postContextFilter.getExerciseId()))
                                .and(getSearchTextSpecification(postContextFilter.getSearchText())).and(getCourseWideContextSpecification(postContextFilter.getCourseWideContext()))
                                .and(getOwnSpecification(postContextFilter.getFilterToOwn(), userId)))
                        .and(getAnsweredOrReactedSpecification(postContextFilter.getFilterToAnsweredOrReacted(), userId))
                        .and(getUnresolvedSpecification(postContextFilter.getFilterToUnresolved()))
                        .and(getSortSpecification(pagingEnabled, postContextFilter.getPostSortCriterion(), postContextFilter.getSortingOrder())));

        if (pagingEnabled) {
            return findAll(specification, pageable);
        }
        else {
            return new PageImpl<>(findAll(specification));
        }
    }

    default Page<Post> findMessages(PostContextFilter postContextFilter, Pageable pageable){
        Specification<Post> specification = Specification.where(distinct())
                .and(getCourseSpecification(postContextFilter.getCourseId(), postContextFilter.getLectureId(), postContextFilter.getExerciseId())
                        .and(getConversationSpecification(postContextFilter.getConversationId())
                        .and(getSortSpecification(true, postContextFilter.getPostSortCriterion(), postContextFilter.getSortingOrder()))));

                        return findAll(specification, pageable);
    }

    @Query("""
            SELECT DISTINCT tag FROM Post post
            LEFT JOIN post.tags tag LEFT JOIN post.lecture lecture LEFT JOIN post.exercise exercise
            WHERE (lecture.course.id = :#{#courseId}
            OR exercise.course.id = :#{#courseId}
            OR post.course.id = :#{#courseId})
            """)
    List<String> findPostTagsForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT post FROM Post post
            LEFT JOIN post.answers answer LEFT JOIN post.reactions reaction
            WHERE post.plagiarismCase.id = :#{#plagiarismCaseId}
            """)
    List<Post> findPostsByPlagiarismCaseId(@Param("plagiarismCaseId") Long plagiarismCaseId);

    @Query("""
             SELECT DISTINCT post FROM Post post
             LEFT JOIN post.conversation conversation
             WHERE conversation.id = :#{#conversationId}
            """)
    List<Post> findPostsByConversationId(@Param("conversationId") Long conversationId);

    default Post findPostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).filter(post -> post.getConversation() == null).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }

    default Post findMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).filter(post -> post.getConversation() != null).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }

    default Post findPostOrMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).orElseThrow(() -> new EntityNotFoundException("Post", postId));
    }
}
