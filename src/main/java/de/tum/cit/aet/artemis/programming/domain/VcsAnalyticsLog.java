package de.tum.cit.aet.artemis.programming.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * A Vcs analytics log entry.
 */
@Entity
@Table(name = "vcs_analytics_log")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VcsAnalyticsLog extends DomainObject {

    @Column(name = "masked_user_id", nullable = false)
    private String maskedUserId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "experimental_group", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private ExperimentalGroup experimentalGroup;

    @Column(name = "repository_action_type", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RepositoryActionType repositoryActionType;

    @Column(name = "authentication_mechanism", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private AuthenticationMechanism authenticationMechanism;

    @Column(name = "timestamp")
    private ZonedDateTime timestamp;

    public VcsAnalyticsLog(String maskedUserId, Long courseId, ExperimentalGroup experimentalGroup, RepositoryActionType repositoryActionType,
            AuthenticationMechanism authenticationMechanism) {
        this.maskedUserId = maskedUserId;
        this.courseId = courseId;
        this.experimentalGroup = experimentalGroup;
        this.repositoryActionType = repositoryActionType;
        this.authenticationMechanism = authenticationMechanism;
        this.timestamp = ZonedDateTime.now();
    }

    public VcsAnalyticsLog() {
    }

    public String getMaskedUserId() {
        return maskedUserId;
    }

    public void setMaskedUserId(String maskedUserId) {
        this.maskedUserId = maskedUserId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public ExperimentalGroup getExperimentalGroup() {
        return experimentalGroup;
    }

    public void setExperimentalGroup(ExperimentalGroup experimentalGroup) {
        this.experimentalGroup = experimentalGroup;
    }

    public RepositoryActionType getRepositoryActionType() {
        return repositoryActionType;
    }

    public void setRepositoryActionType(RepositoryActionType repositoryActionType) {
        this.repositoryActionType = repositoryActionType;
    }

    public AuthenticationMechanism getAuthenticationMechanism() {
        return authenticationMechanism;
    }

    public void setAuthenticationMechanism(AuthenticationMechanism authenticationMechanism) {
        this.authenticationMechanism = authenticationMechanism;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

}
