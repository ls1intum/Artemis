package de.tum.cit.aet.artemis.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * Represents the token usage details of a single LLM request, including model, service pipeline, token counts, and costs.
 */
@Entity
@Table(name = "llm_token_usage_request")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LLMTokenUsageRequest extends DomainObject {

    /**
     * LLM model (e.g. gpt-4o)
     */
    @Column(name = "model")
    private String model;

    /**
     * pipeline that was called (e.g. IRIS_COURSE_CHAT_PIPELINE)
     */
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

    /**
     * part of the input tokens the provider read from its prompt cache (included in numInputTokens)
     */
    @Column(name = "num_cached_input_tokens")
    private int numCachedInputTokens;

    @Column(name = "cost_per_million_cached_input_tokens")
    private float costPerMillionCachedInputTokens;

    /**
     * part of the input tokens the provider wrote to its prompt cache (included in numInputTokens)
     */
    @Column(name = "num_cache_write_input_tokens")
    private int numCacheWriteInputTokens;

    @Column(name = "cost_per_million_cache_write_input_tokens")
    private float costPerMillionCacheWriteInputTokens;

    @ManyToOne
    @JoinColumn(nullable = false)
    @Parent
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

    public int getNumCachedInputTokens() {
        return numCachedInputTokens;
    }

    public void setNumCachedInputTokens(int numCachedInputTokens) {
        this.numCachedInputTokens = numCachedInputTokens;
    }

    public float getCostPerMillionCachedInputTokens() {
        return costPerMillionCachedInputTokens;
    }

    public void setCostPerMillionCachedInputTokens(float costPerMillionCachedInputTokens) {
        this.costPerMillionCachedInputTokens = costPerMillionCachedInputTokens;
    }

    public int getNumCacheWriteInputTokens() {
        return numCacheWriteInputTokens;
    }

    public void setNumCacheWriteInputTokens(int numCacheWriteInputTokens) {
        this.numCacheWriteInputTokens = numCacheWriteInputTokens;
    }

    public float getCostPerMillionCacheWriteInputTokens() {
        return costPerMillionCacheWriteInputTokens;
    }

    public void setCostPerMillionCacheWriteInputTokens(float costPerMillionCacheWriteInputTokens) {
        this.costPerMillionCacheWriteInputTokens = costPerMillionCacheWriteInputTokens;
    }

    public LLMTokenUsageTrace getTrace() {
        return trace;
    }

    public void setTrace(LLMTokenUsageTrace trace) {
        this.trace = trace;
    }
}
