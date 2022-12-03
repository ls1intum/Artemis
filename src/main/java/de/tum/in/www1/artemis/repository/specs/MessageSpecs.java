package de.tum.in.www1.artemis.repository.specs;

import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.metis.Conversation_;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Post_;

public class MessageSpecs {

    /**
     * Specification to fetch Posts belonging to a Conversation
     *
     * @param conversationId id of the conversation the Posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getConversationSpecification(Long conversationId) {
        return ((root, query, criteriaBuilder) -> {
            query.distinct(true); // get distinct messages
            return criteriaBuilder.equal(root.get(Post_.CONVERSATION).get(Conversation_.ID), conversationId);
        });
    }

    /**
     * Specification which filters Messages according to a search string in a match-all-manner
     * message is only kept if the search string (which is not a #id pattern) is included in the message content (all strings lowercased)
     *
     * @param searchText Text to be searched within messages
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getSearchTextSpecification(String searchText) {
        return ((root, query, criteriaBuilder) -> {
            if (searchText == null || searchText.isBlank()) {
                return null;
            }
            // search by text or #message
            else if (searchText.startsWith("#") && (searchText.substring(1) != null && !searchText.substring(1).isBlank())) {
                // if searchText starts with a # and is followed by a message id, filter for message with id
                return criteriaBuilder.equal(root.get(Post_.ID), Integer.parseInt(searchText.substring(1)));
            }
            else {
                // regular search on content
                Expression<String> searchTextLiteral = criteriaBuilder.literal("%" + searchText.toLowerCase() + "%");

                Predicate searchInMessageContent = criteriaBuilder.like(criteriaBuilder.lower(root.get(Post_.CONTENT)), searchTextLiteral);

                return criteriaBuilder.and(searchInMessageContent);
            }
        });
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
