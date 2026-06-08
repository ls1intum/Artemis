package de.tum.cit.aet.artemis.globalsearch.dto.searchableentity;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Snapshot of the data needed to upsert an answer post (reply) into the unified {@code SearchableEntities}
 * Weaviate collection. Only answer posts from public channels are indexed. The answer post content is
 * stored in the {@code description} field for BM25 search.
 */
public record AnswerPostSearchableEntityDTO(@NotNull Long answerPostId, @NotNull Long postId, @NotNull Long courseId, @NotNull Long channelId, @Nullable String content) {

    /**
     * Extracts all required data from an {@link AnswerPost} entity that belongs to a {@link Channel}.
     *
     * @param answerPost the answer post entity (must have post and conversation relationships loaded)
     * @param channel    the channel the parent post belongs to (must have course relationship loaded)
     * @return the extracted data safe to use in an async context
     */
    public static AnswerPostSearchableEntityDTO fromAnswerPost(AnswerPost answerPost, Channel channel) {
        return new AnswerPostSearchableEntityDTO(answerPost.getId(), answerPost.getPost().getId(), channel.getCourse().getId(), channel.getId(), answerPost.getContent());
    }

    /**
     * Produces the Weaviate property map for this answer post row.
     *
     * @return the property map keyed by {@link SearchableEntitySchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.ANSWER_POST);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, answerPostId);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.CHANNEL_ID, channelId);
        properties.put(SearchableEntitySchema.Properties.POST_ID, postId);
        if (content != null) {
            properties.put(SearchableEntitySchema.Properties.DESCRIPTION, content);
        }
        return properties;
    }
}
