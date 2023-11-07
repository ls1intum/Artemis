package de.tum.in.www1.artemis.repository.specs;

import java.util.Arrays;

import javax.persistence.criteria.*;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.Course_;
import de.tum.in.www1.artemis.domain.metis.AnswerPost_;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Post_;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel_;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation_;

public class MessageSpecs {

    /**
     * Specification to fetch Posts belonging to a Conversation
     *
     * @param conversationId id of the conversation the Posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getConversationSpecification(Long conversationId) {
        return ((root, query, criteriaBuilder) -> {
            // fetch additional values to avoid subsequent db calls
            var answerFetch = root.fetch(Post_.ANSWERS, JoinType.LEFT);
            answerFetch.fetch(AnswerPost_.REACTIONS, JoinType.LEFT);
            root.fetch(Post_.REACTIONS, JoinType.LEFT);
            root.fetch(Post_.TAGS, JoinType.LEFT);
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
            else if (searchText.startsWith("#") && !searchText.substring(1).isBlank()) {
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
     * Specification to fetch messages belonging to a list of conversations
     *
     * @param conversationIds ids of the conversation messages belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getConversationsSpecification(long[] conversationIds) {
        return ((root, query, criteriaBuilder) -> {
            // fetch additional values to avoid subsequent db calls
            var answerFetch = root.fetch(Post_.ANSWERS, JoinType.LEFT);
            answerFetch.fetch(AnswerPost_.REACTIONS, JoinType.LEFT);
            root.fetch(Post_.REACTIONS, JoinType.LEFT);
            root.fetch(Post_.TAGS, JoinType.LEFT);

            if (conversationIds == null || conversationIds.length == 0) {
                return null;
            }
            else {
                return root.get(Post_.CONVERSATION).get(Conversation_.ID).in(Arrays.stream(conversationIds).boxed().toList());
            }
        });
    }

    /**
     * Creates a specification to fetch messages belonging to a course-wide channels in the course
     *
     * @param courseId id of course the posts belong to
     * @return specification used to chain DB operations
     */
    public static Specification<Post> getCourseWideChannelsSpecification(Long courseId) {
        return (root, query, criteriaBuilder) -> {
            Join<Post, Channel> joinedChannels = criteriaBuilder.treat(root.join(Post_.CONVERSATION, JoinType.LEFT), Channel.class);
            joinedChannels.on(criteriaBuilder.equal(root.get(Post_.CONVERSATION).get(Conversation_.ID), joinedChannels.get(Channel_.ID)));

            Predicate isInCourse = criteriaBuilder.equal(joinedChannels.get(Channel_.COURSE).get(Course_.ID), courseId);
            Predicate isCourseWide = criteriaBuilder.isTrue(joinedChannels.get(Channel_.IS_COURSE_WIDE));
            return criteriaBuilder.and(isInCourse, isCourseWide);
        };
    }
}
