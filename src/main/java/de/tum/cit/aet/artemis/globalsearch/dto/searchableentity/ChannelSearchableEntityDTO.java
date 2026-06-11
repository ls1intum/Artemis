package de.tum.cit.aet.artemis.globalsearch.dto.searchableentity;

import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Snapshot of the data needed to upsert a channel into the unified {@code SearchableEntities} Weaviate
 * collection.
 * <p>
 * Only {@link Channel} instances that are either course-wide or public are indexed; private non-course-wide
 * channels (and {@code OneToOneChat} / {@code GroupChat}) are not searchable in this PR. The description
 * is composed from {@link Channel#getTopic() topic} and {@link Channel#getDescription() description}.
 */
public record ChannelSearchableEntityDTO(Long channelId, Long courseId, String name, String description, boolean isCourseWide, boolean isPublic) {

    /**
     * Extracts all required data from a {@link Channel} entity.
     *
     * @param channel the channel entity (must have course relationship loaded)
     * @return the extracted data safe to use in an async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static ChannelSearchableEntityDTO fromChannel(Channel channel) {
        return new ChannelSearchableEntityDTO(channel.getId(), channel.getCourse().getId(), channel.getName(), buildDescription(channel), channel.getIsCourseWide(),
                channel.getIsPublic());
    }

    /**
     * Returns {@code true} iff the supplied channel is eligible for indexing (course-wide or public, and not archived).
     *
     * @param channel the channel to test
     * @return whether the channel should be synchronized to Weaviate
     */
    public static boolean isIndexable(Channel channel) {
        return !channel.getIsArchived() && (channel.getIsCourseWide() || channel.getIsPublic());
    }

    private static String buildDescription(Channel channel) {
        String topic = channel.getTopic();
        String description = channel.getDescription();
        boolean hasTopic = topic != null && !topic.isBlank();
        boolean hasDescription = description != null && !description.isBlank();
        if (hasTopic && hasDescription) {
            return topic + "\n\n" + description;
        }
        if (hasTopic) {
            return topic;
        }
        if (hasDescription) {
            return description;
        }
        return null;
    }

    /**
     * Produces the Weaviate property map for this channel row.
     *
     * @return the property map keyed by {@link SearchableEntitySchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.CHANNEL);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, channelId);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TITLE, name);
        properties.put(SearchableEntitySchema.Properties.CHANNEL_IS_COURSE_WIDE, isCourseWide);
        properties.put(SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC, isPublic);
        if (description != null) {
            properties.put(SearchableEntitySchema.Properties.DESCRIPTION, description);
        }
        return properties;
    }
}
