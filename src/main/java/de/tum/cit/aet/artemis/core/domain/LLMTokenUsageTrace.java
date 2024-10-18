package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "llm_token_usage_trace")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LLMTokenUsageTrace extends DomainObject {

    @Column(name = "service")
    private LLMServiceType serviceType;

    @Nullable
    @Column(name = "course_id")
    private Long courseId;

    @Nullable
    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "time")
    private ZonedDateTime time = ZonedDateTime.now();

    @Nullable
    @Column(name = "iris_message_id")
    private Long irisMessageId;

    @OneToMany(mappedBy = "trace", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LLMTokenUsageRequest> llmRequests = new HashSet<>();

    public LLMServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(LLMServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
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

    public Set<LLMTokenUsageRequest> getLLMRequests() {
        return llmRequests;
    }

    public void setLlmRequests(Set<LLMTokenUsageRequest> llmRequests) {
        this.llmRequests = llmRequests;
    }

    public Long getIrisMessageId() {
        return irisMessageId;
    }

    public void setIrisMessageId(Long messageId) {
        this.irisMessageId = messageId;
    }
}
