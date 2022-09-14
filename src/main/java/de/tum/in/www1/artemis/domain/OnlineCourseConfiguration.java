package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "online_course_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class OnlineCourseConfiguration extends DomainObject {

    @OneToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "lti_id", nullable = false)
    private String ltiId;

    @Column(name = "lti_key", nullable = false)
    private String ltiKey;

    @Column(name = "lti_secret", nullable = false)
    private String ltiSecret;

    @Column(name = "user_prefix", nullable = false)
    private String userPrefix;

    @Column(name = "original_url")
    private String originalUrl;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getLtiId() {
        return ltiId;
    }

    public void setLtiId(String ltiId) {
        this.ltiId = ltiId;
    }

    public String getLtiKey() {
        return ltiKey;
    }

    public void setLtiKey(String ltiKey) {
        this.ltiKey = ltiKey;
    }

    public String getLtiSecret() {
        return ltiSecret;
    }

    public void setLtiSecret(String ltiSecret) {
        this.ltiSecret = ltiSecret;
    }

    public String getUserPrefix() {
        return userPrefix;
    }

    public void setUserPrefix(String userPrefix) {
        this.userPrefix = userPrefix;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }
}
