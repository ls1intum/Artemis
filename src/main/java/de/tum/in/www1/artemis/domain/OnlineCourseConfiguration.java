package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.LOGIN_REGEX;

import javax.persistence.*;

import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

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

    @Column(name = "original_url")
    private String originalUrl;

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

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    /**
     * Validates the online course configuration
     */
    public void validate() {
        if (StringUtils.isBlank(ltiKey) || StringUtils.isBlank(ltiSecret)) {
            throw new BadRequestAlertException("Invalid online course configuration", ENTITY_NAME, "invalidOnlineCourseConfiguration");
        }
        if (StringUtils.isBlank(userPrefix) || !userPrefix.matches(LOGIN_REGEX)) {
            throw new BadRequestAlertException("Invalid user prefix, must match login regex defined in Constants.java", ENTITY_NAME, "invalidUserPrefix");
        }
    }
}
