package de.tum.cit.aet.artemis.globalsearch.dto.searchableitem;

import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableItemSchema;

/**
 * Snapshot of the data needed to upsert an FAQ into the unified {@code SearchableItems} Weaviate
 * collection. The {@code faqState} is stored as a filterable text property so students (who may only
 * see {@code ACCEPTED} entries) can be filtered out from staff-visible entries in a single query.
 */
public record FaqSearchableItemDTO(Long faqId, Long courseId, String questionTitle, String questionAnswer, String faqState) {

    /**
     * Extracts all required data from a {@link Faq} entity.
     *
     * @param faq the FAQ entity (must have course relationship loaded)
     * @return the extracted data safe to use in an async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static FaqSearchableItemDTO fromFaq(Faq faq) {
        return new FaqSearchableItemDTO(faq.getId(), faq.getCourse().getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(),
                faq.getFaqState() != null ? faq.getFaqState().name() : null);
    }

    /**
     * Produces the Weaviate property map for this FAQ row.
     *
     * @return the property map keyed by {@link SearchableItemSchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableItemSchema.Properties.TYPE, SearchableItemSchema.TypeValues.FAQ);
        properties.put(SearchableItemSchema.Properties.ENTITY_ID, faqId);
        properties.put(SearchableItemSchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableItemSchema.Properties.TITLE, questionTitle);
        if (questionAnswer != null) {
            properties.put(SearchableItemSchema.Properties.DESCRIPTION, questionAnswer);
        }
        if (faqState != null) {
            properties.put(SearchableItemSchema.Properties.FAQ_STATE, faqState);
        }
        return properties;
    }
}
