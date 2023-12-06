package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "online_course_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OnlineCourseConfiguration extends DomainObject {

    public static final String ENTITY_NAME = "onlineCourseConfiguration";

    @OneToOne(mappedBy = "onlineCourseConfiguration")
    @JsonIgnore
    private Course course;

    @Column(name = "lti_key", nullable = false)
    private String ltiKey;

    @Column(name = "lti_secret", nullable = false)
    private String ltiSecret;

    @Column(name = "user_prefix", nullable = false)
    private String userPrefix;

    @Column(name = "require_existing_user")
    private boolean requireExistingUser;

    @ManyToOne
    @JoinColumn(name = "lti_platform_id", referencedColumnName = "id")
    private LtiPlatformConfiguration ltiPlatformConfiguration;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
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

    public boolean isRequireExistingUser() {
        return requireExistingUser;
    }

    public void setRequireExistingUser(boolean requireExistingUser) {
        this.requireExistingUser = requireExistingUser;
    }

    public LtiPlatformConfiguration getLtiPlatformConfiguration() {
        return ltiPlatformConfiguration;
    }

    public void setLtiPlatformConfiguration(LtiPlatformConfiguration ltiPlatformConfiguration) {
        this.ltiPlatformConfiguration = ltiPlatformConfiguration;
    }

}
