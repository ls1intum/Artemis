package de.tum.cit.aet.artemis.globalsearch.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class IndexedEntityDTOTest {

    @Test
    void serializesFalseExpectedFlagWithNonEmptyInclusion() throws Exception {
        IndexedEntityDTO entity = new IndexedEntityDTO("lecture_unit", 42L, "Retained row", 7L, null, false);

        String json = JsonMapper.builder().build().writeValueAsString(entity);

        assertThat(json).contains("\"expected\":false");
    }
}
