package de.tum.cit.aet.artemis.globalsearch.dto.searchableentity;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Snapshot of the data needed to upsert a post (message) into the unified {@code SearchableEntities}
 * Weaviate collection. Only posts from public channels are indexed. The post content is stored in the
 * {@code description} field for BM25 search; the optional title goes into {@code title}.
 */
public record PostSearchableEntityDTO(@NotNull Long postId, @NotNull Long courseId, @NotNull Long channelId, @Nullable String title, @Nullable String content) {

    /**
     * Extracts all required data from a {@link Post} entity that belongs to a {@link Channel}.
     *
     * @param post    the post entity (must have conversation relationship loaded)
     * @param channel the channel the post belongs to (must have course relationship loaded)
     * @return the extracted data safe to use in an async context
     */
    public static PostSearchableEntityDTO fromPost(Post post, Channel channel) {
        return new PostSearchableEntityDTO(post.getId(), channel.getCourse().getId(), channel.getId(), post.getTitle(), post.getContent());
    }

    /**
     * Returns {@code true} iff the post is eligible for indexing: it must belong to a public,
     * non-archived channel.
     *
     * @param channel the channel to test
     * @return whether the post should be synchronized to Weaviate
     */
    public static boolean isIndexable(Channel channel) {
        return !channel.getIsArchived() && channel.getIsPublic();
    }

    /**
     * Produces the Weaviate property map for this post row.
     *
     * @return the property map keyed by {@link SearchableEntitySchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.POST);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, postId);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.CHANNEL_ID, channelId);
        if (title != null) {
            properties.put(SearchableEntitySchema.Properties.TITLE, title);
        }
        if (content != null) {
            properties.put(SearchableEntitySchema.Properties.DESCRIPTION, content);
        }
        return properties;
    }
}
