package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "course_request")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseRequest extends DomainObject {

    public static final String ENTITY_NAME = "courseRequest";

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "short_name", nullable = false, unique = true)
    private String shortName;

    @Column(name = "semester")
    private String semester;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @Column(name = "test_course", nullable = false)
    private boolean testCourse = false;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourseRequestStatus status = CourseRequestStatus.PENDING;

    @Column(name = "created_date", nullable = false)
    private ZonedDateTime createdDate;

    @Column(name = "processed_date")
    private ZonedDateTime processedDate;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    // The admin who processed the request
    @Column(name = "admin")
    private String admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    @JsonIgnoreProperties(value = "courseRequests", allowSetters = true)
    private User requester;

    @Column(name = "created_course_id")
    private Long createdCourseId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public boolean isTestCourse() {
        return testCourse;
    }

    public void setTestCourse(boolean testCourse) {
        this.testCourse = testCourse;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public CourseRequestStatus getStatus() {
        return status;
    }

    public void setStatus(CourseRequestStatus status) {
        this.status = status;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public ZonedDateTime getProcessedDate() {
        return processedDate;
    }

    public void setProcessedDate(ZonedDateTime processedDate) {
        this.processedDate = processedDate;
    }

    public String getAdmin() {
        return admin;
    }

    public void setAdmin(String admin) {
        this.admin = admin;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public User getRequester() {
        return requester;
    }

    public void setRequester(User requester) {
        this.requester = requester;
    }

    public Long getCreatedCourseId() {
        return createdCourseId;
    }

    public void setCreatedCourseId(Long createdCourseId) {
        this.createdCourseId = createdCourseId;
    }
}
