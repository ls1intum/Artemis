package de.tum.cit.aet.artemis.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "llm_token_usage_request")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LLMTokenUsageRequest extends DomainObject {

    @Column(name = "model")
    private String model;

    @Column(name = "service_pipeline_id")
    private String servicePipelineId;

    @Column(name = "num_input_tokens")
    private int numInputTokens;

    @Column(name = "cost_per_million_input_tokens")
    private float costPerMillionInputTokens;

    @Column(name = "num_output_tokens")
    private int numOutputTokens;

    @Column(name = "cost_per_million_output_tokens")
    private float costPerMillionOutputTokens;

    @ManyToOne
    private LLMTokenUsageTrace trace;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getServicePipelineId() {
        return servicePipelineId;
    }

    public void setServicePipelineId(String servicePipelineId) {
        this.servicePipelineId = servicePipelineId;
    }

    public float getCostPerMillionInputTokens() {
        return costPerMillionInputTokens;
    }

    public void setCostPerMillionInputTokens(float costPerMillionInputToken) {
        this.costPerMillionInputTokens = costPerMillionInputToken;
    }

    public float getCostPerMillionOutputTokens() {
        return costPerMillionOutputTokens;
    }

    public void setCostPerMillionOutputTokens(float costPerMillionOutputToken) {
        this.costPerMillionOutputTokens = costPerMillionOutputToken;
    }

    public int getNumInputTokens() {
        return numInputTokens;
    }

    public void setNumInputTokens(int numInputTokens) {
        this.numInputTokens = numInputTokens;
    }

    public int getNumOutputTokens() {
        return numOutputTokens;
    }

    public void setNumOutputTokens(int numOutputTokens) {
        this.numOutputTokens = numOutputTokens;
    }

    public LLMTokenUsageTrace getTrace() {
        return trace;
    }

    public void setTrace(LLMTokenUsageTrace trace) {
        this.trace = trace;
    }
}
