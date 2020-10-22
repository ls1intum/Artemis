package de.tum.in.www1.artemis.web.rest.dto;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.TextBlockType;
import de.tum.in.www1.artemis.domain.TextCluster;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AtheneDTO {

    public List<TextBlock> blocks = new ArrayList<>();

    public Map<Integer, TextCluster> clusters = new LinkedHashMap<>();

    // Inner DTO
    public static class TextBlock {

        public String id;

        public long submissionId;

        public String text;

        public int startIndex;

        public int endIndex;

        public TextBlockType type = TextBlockType.AUTOMATIC;

    }

}
