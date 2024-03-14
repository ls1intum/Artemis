package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Deprecated(forRemoval = true) // will be removed in 7.0.0
public class BambooChangesDTO {

    private int size;

    private String expand;

    @JsonProperty("change")
    private List<BambooChangeDTO> changes = new ArrayList<>();

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getExpand() {
        return expand;
    }

    public void setExpand(String expand) {
        this.expand = expand;
    }

    public List<BambooChangeDTO> getChanges() {
        return changes;
    }

    public void setChanges(List<BambooChangeDTO> changes) {
        this.changes = changes;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static final class BambooChangeDTO {

        private String author;

        private String userName;

        private String fullName;

        private String changesetId;

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getChangesetId() {
            return changesetId;
        }

        public void setChangesetId(String changesetId) {
            this.changesetId = changesetId;
        }
    }
}
