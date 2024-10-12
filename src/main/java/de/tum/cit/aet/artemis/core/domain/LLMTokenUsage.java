package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;

@Entity
@Table(name = "llm_token_usage")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LLMTokenUsage extends DomainObject {

    @Column(name = "service")
    @Enumerated(EnumType.STRING)
    private LLMServiceType serviceType;

    @Column(name = "model")
    private String model;

    @Column(name = "num_input_tokens")
    private int num_input_tokens;

    @Column(name = "cost_per_input_token")
    private float cost_per_input_token;

    @Column(name = "num_output_tokens")
    private int num_output_tokens;

    @Column(name = "cost_per_output_token")
    private float cost_per_output_token;

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

    @Nullable
    @Column(name = "timestamp")
    private ZonedDateTime timestamp = ZonedDateTime.now();

    @Column(name = "trace_id")
    private Long traceId;

    @Nullable
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "iris_message_id")
    IrisMessage irisMessage;

    public LLMServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(LLMServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public float getCost_per_input_token() {
        return cost_per_input_token;
    }

    public void setCost_per_input_token(float cost_per_input_token) {
        this.cost_per_input_token = cost_per_input_token;
    }

    public float getCost_per_output_token() {
        return cost_per_output_token;
    }

    public void setCost_per_output_token(float cost_per_output_token) {
        this.cost_per_output_token = cost_per_output_token;
    }

    public int getNum_input_tokens() {
        return num_input_tokens;
    }

    public void setNum_input_tokens(int num_input_tokens) {
        this.num_input_tokens = num_input_tokens;
    }

    public int getNum_output_tokens() {
        return num_output_tokens;
    }

    public void setNum_output_tokens(int num_output_tokens) {
        this.num_output_tokens = num_output_tokens;
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

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getTraceId() {
        return traceId;
    }

    public void setTraceId(Long traceId) {
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
        return "LLMTokenUsage{" + "serviceType=" + serviceType + ", model=" + model + ", num_input_tokens=" + num_input_tokens + ", cost_per_input_token=" + cost_per_input_token
                + ", num_output_tokens=" + num_output_tokens + ", cost_per_output_token=" + cost_per_output_token + ", course=" + course + ", exercise=" + exercise + ", userId="
                + userId + ", timestamp=" + timestamp + ", trace_id=" + traceId + ", irisMessage=" + irisMessage + '}';
    }
}
