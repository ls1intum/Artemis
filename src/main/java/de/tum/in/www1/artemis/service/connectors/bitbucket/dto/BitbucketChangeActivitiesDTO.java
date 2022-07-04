package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a Bitbucket change activity response
 * Not all possible values are included. If you want to extend this DTO for new functionality, consult the Bitbucket documentation.
 */
public class BitbucketChangeActivitiesDTO {

    private Long size;

    private Long limit;

    private Long start;

    @JsonProperty("isLastPage")
    private Boolean isLastPage;

    private List<ValuesDTO> values;

    public static class ValuesDTO {

        private Long id;

        private Long createdDate;

        private RefChangeDTO refChange;

        private String trigger;

        public static class RefChangeDTO {

            private String fromHash;

            private String toHash;

            private String refId;

            public String getFromHash() {
                return fromHash;
            }

            public void setFromHash(String fromHash) {
                this.fromHash = fromHash;
            }

            public String getToHash() {
                return toHash;
            }

            public void setToHash(String toHash) {
                this.toHash = toHash;
            }

            public String getRefId() {
                return refId;
            }

            public void setRefId(String refId) {
                this.refId = refId;
            }
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getCreatedDate() {
            return createdDate;
        }

        public void setCreatedDate(Long createdDate) {
            this.createdDate = createdDate;
        }

        public RefChangeDTO getRefChange() {
            return refChange;
        }

        public void setRefChange(RefChangeDTO refChange) {
            this.refChange = refChange;
        }

        public String getTrigger() {
            return trigger;
        }

        public void setTrigger(String trigger) {
            this.trigger = trigger;
        }
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getLimit() {
        return limit;
    }

    public void setLimit(Long limit) {
        this.limit = limit;
    }

    public Long getStart() {
        return start;
    }

    public void setStart(Long start) {
        this.start = start;
    }

    public Boolean getLastPage() {
        return isLastPage;
    }

    public void setLastPage(Boolean lastPage) {
        isLastPage = lastPage;
    }

    public List<ValuesDTO> getValues() {
        return values;
    }

    public void setValues(List<ValuesDTO> values) {
        this.values = values;
    }
}
