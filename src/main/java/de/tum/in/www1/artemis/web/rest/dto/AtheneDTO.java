package de.tum.in.www1.artemis.web.rest.dto;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.TextBlockType;
import de.tum.in.www1.artemis.domain.TextCluster;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AtheneDTO {

    private List<TextBlockDTO> blocks = new ArrayList<>();

    private Map<Integer, TextCluster> clusters = new HashMap<>();

    public List<TextBlockDTO> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<TextBlockDTO> blocks) {
        this.blocks = blocks;
    }

    public Map<Integer, TextCluster> getClusters() {
        return clusters;
    }

    public void setClusters(Map<Integer, TextCluster> clusters) {
        this.clusters = clusters;
    }

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class TextBlockDTO {

        private String id;

        private long submissionId;

        private String text;

        private int startIndex;

        private int endIndex;

        private TextBlockType type = TextBlockType.AUTOMATIC;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public long getSubmissionId() {
            return submissionId;
        }

        public void setSubmissionId(long submissionId) {
            this.submissionId = submissionId;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public void setStartIndex(int startIndex) {
            this.startIndex = startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        public void setEndIndex(int endIndex) {
            this.endIndex = endIndex;
        }

        public TextBlockType getType() {
            return type;
        }

        public void setType(TextBlockType type) {
            this.type = type;
        }
    }
}
