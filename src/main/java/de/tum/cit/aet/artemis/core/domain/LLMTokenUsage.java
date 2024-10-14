package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;

@Entity
@Table(name = "llm_token_usage")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LLMTokenUsage extends DomainObject {

    @Column(name = "service")
    private String serviceType;

    @Column(name = "model")
    private String model;

    @Column(name = "num_input_tokens")
    private int numInputTokens;

    @Column(name = "cost_per_million_input_tokens")
    private float costPerMillionInputTokens;

    @Column(name = "num_output_tokens")
    private int numOutputTokens;

    @Column(name = "cost_per_million_output_tokens")
    private float costPerMillionOutputTokens;

    @Nullable
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "course_id")
    private Course course;

    @Nullable
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(name = "user_id")
    private long userId;

    @Column(name = "time")
    private ZonedDateTime time = ZonedDateTime.now();

    @Column(name = "trace_id")
    private String traceId;

    @Nullable
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "iris_message_id")
    private IrisMessage irisMessage;

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public void setTime(ZonedDateTime time) {
        this.time = time;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public IrisMessage getIrisMessage() {
        return irisMessage;
    }

    public void setIrisMessage(IrisMessage message) {
        this.irisMessage = message;
    }

    @Override
    public String toString() {
        return "LLMTokenUsage{" + "serviceType=" + serviceType + ", model=" + model + ", numInputTokens=" + numInputTokens + ", costPerMillionInputTokens="
                + costPerMillionInputTokens + ", numOutputTokens=" + numOutputTokens + ", costPerMillionOutputTokens=" + costPerMillionOutputTokens + ", course=" + course
                + ", exercise=" + exercise + ", userId=" + userId + ", timestamp=" + time + ", traceId=" + traceId + ", irisMessage=" + irisMessage + '}';
    }
}
