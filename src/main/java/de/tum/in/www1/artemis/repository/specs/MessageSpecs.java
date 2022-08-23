package de.tum.in.www1.artemis.repository.specs;

import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.metis.Conversation_;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Post_;

public class MessageSpecs {

    /**
     * Creates a specification to limit Message fetching within a Course
     *
     * @param courseId id of course the posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getCourseSpecification(Long courseId) {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true); // get distinct messages
            Predicate coursePosts = criteriaBuilder.equal(root.get(Post_.COURSE), courseId);
            return criteriaBuilder.and(coursePosts);
        };
    }

    /**
     * Specification to fetch Posts belonging to a Conversation
     *
     * @param conversationId id of the conversation the Posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getConversationSpecification(Long conversationId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(Post_.CONVERSATION).get(Conversation_.ID), conversationId);
    }

    /**
     * Specification which sorts Messages by creation date descending
     *
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getSortSpecification() {
        return ((root, query, criteriaBuilder) -> {

            Expression<?> sortCriterion = null;

            // sort by creation date
            sortCriterion = root.get(Post_.CREATION_DATE);

            // descending
            query.orderBy(criteriaBuilder.desc(sortCriterion));
            return null;
        });
    }
}
