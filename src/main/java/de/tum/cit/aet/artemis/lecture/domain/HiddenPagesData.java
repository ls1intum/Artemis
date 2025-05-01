package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;

/**
 * Class to encapsulate the collection of hidden pages.
 */
public class HiddenPagesData {

    private static final Logger log = LoggerFactory.getLogger(HiddenPagesData.class);

    private final Map<String, HiddenPageInfo> hiddenPagesMap;

    /**
     * Creates a new hidden pages data with the given map.
     *
     * @param hiddenPagesMap The map of slide ID to hidden page info
     */
    public HiddenPagesData(Map<String, HiddenPageInfo> hiddenPagesMap) {
        this.hiddenPagesMap = hiddenPagesMap;
    }

    /**
     * Factory method to parse the JSON hidden pages string into a HiddenPagesData instance.
     *
     * @param json The JSON string containing hidden pages information
     * @return A HiddenPagesData object
     */
    public static HiddenPagesData fromJson(String json) {
        Map<String, HiddenPageInfo> map = new HashMap<>();

        if (json != null && !json.isEmpty()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, Object>> hiddenPagesList = objectMapper.readValue(json, new TypeReference<>() {
                });

                for (Map<String, Object> page : hiddenPagesList) {
                    String slideId = String.valueOf(page.get("slideId"));
                    String dateStr = (String) page.get("date");
                    ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateStr);

                    Long exerciseId = null;
                    if (page.get("exerciseId") != null) {
                        exerciseId = ((Number) page.get("exerciseId")).longValue();
                    }

                    map.put(slideId, new HiddenPageInfo(zonedDateTime, exerciseId));
                }
            }
            catch (Exception e) {
                log.error("Failed to parse hidden pages data: {}", e.getMessage(), e);
                throw new InternalServerErrorException("Could not parse hidden pages data: " + e.getMessage());
            }
        }

        return new HiddenPagesData(map);
    }

    /**
     * Gets hidden page info for a slide.
     *
     * @param slideId The ID of the slide
     * @return The hidden page info, or null if not found
     */
    public HiddenPageInfo getHiddenPageInfo(String slideId) {
        return hiddenPagesMap.get(slideId);
    }
}
