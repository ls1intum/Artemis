package de.tum.cit.aet.artemis.lti.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.course.domain.Course;

@Entity
@Table(name = "online_course_configuration")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OnlineCourseConfiguration extends DomainObject {

    public static final String ENTITY_NAME = "onlineCourseConfiguration";

    /**
     * The course this configuration belongs to. The key lives here rather than on the course: the course carries no
     * association to its configuration, so loading a course can never pull this row in, and the configuration cannot
     * outlive the course. Read the configuration through {@code OnlineCourseConfigurationRepository} where it is needed.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, unique = true)
    @JsonIgnore
    @Parent
    private Course course;

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
