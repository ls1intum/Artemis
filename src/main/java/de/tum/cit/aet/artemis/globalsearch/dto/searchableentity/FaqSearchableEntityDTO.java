package de.tum.cit.aet.artemis.globalsearch.dto.searchableentity;

import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Snapshot of the data needed to upsert an FAQ into the unified {@code SearchableItems} Weaviate
 * collection. The {@code faqState} is stored as a filterable text property so students (who may only
 * see {@code ACCEPTED} entries) can be filtered out from staff-visible entries in a single query.
 */
public record FaqSearchableEntityDTO(Long faqId, Long courseId, String questionTitle, String questionAnswer, String faqState) {

    /**
     * Extracts all required data from a {@link Faq} entity.
     *
     * @param faq the FAQ entity (must have course relationship loaded)
     * @return the extracted data safe to use in an async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static FaqSearchableEntityDTO fromFaq(Faq faq) {
        return new FaqSearchableEntityDTO(faq.getId(), faq.getCourse().getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(),
                faq.getFaqState() != null ? faq.getFaqState().name() : null);
    }

    /**
     * Produces the Weaviate property map for this FAQ row.
     *
     * @return the property map keyed by {@link SearchableEntitySchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.FAQ);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, faqId);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TITLE, questionTitle);
        if (questionAnswer != null) {
            properties.put(SearchableEntitySchema.Properties.DESCRIPTION, questionAnswer);
        }
        if (faqState != null) {
            properties.put(SearchableEntitySchema.Properties.FAQ_STATE, faqState);
        }
        return properties;
    }
}
