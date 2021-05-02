package de.tum.in.www1.artemis.service.connectors.bamboo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BambooProjectDTO {

    private String key;

    private String name;

    private String description;

    private BambooBuildPlansDTO plans;

    /**
     * needed for Jackson
     */
    public BambooProjectDTO() {
    }

    public BambooProjectDTO(String key, String name, String description) {
        this.key = key;
        this.name = name;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BambooBuildPlansDTO getPlans() {
        return plans;
    }

    public void setPlans(BambooBuildPlansDTO plans) {
        this.plans = plans;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BambooBuildPlansDTO {

        private List<BambooBuildPlanDTO> plan;

        public BambooBuildPlansDTO() {
        }

        public BambooBuildPlansDTO(List<BambooBuildPlanDTO> plan) {
            this.plan = plan;
        }

        public List<BambooBuildPlanDTO> getPlan() {
            return plan;
        }

        public void setPlan(List<BambooBuildPlanDTO> plan) {
            this.plan = plan;
        }
    }

}
