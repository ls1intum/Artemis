package de.tum.cit.aet.artemis.core.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.COMPLAINT_RESPONSE_TEXT_LIMIT;
import static de.tum.cit.aet.artemis.core.config.Constants.COMPLAINT_TEXT_LIMIT;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;

/**
 * Configuration entity for course complaint and feedback request settings.
 * <p>
 * This entity is lazily loaded from the Course entity to reduce database load
 * when courses are frequently fetched but complaint settings are rarely needed.
 */
@Entity
@Table(name = "course_complaint_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseComplaintConfiguration extends DomainObject {

    private static final int DEFAULT_COMPLAINT_TEXT_LIMIT = 2000;

    @OneToOne(mappedBy = "complaintConfiguration")
    @JsonIgnore
    private Course course;

    @Column(name = "max_complaints", nullable = false)
    private Integer maxComplaints = 3;

    @Column(name = "max_team_complaints", nullable = false)
    private Integer maxTeamComplaints = 3;

    @Column(name = "max_complaint_time_days", nullable = false)
    private int maxComplaintTimeDays = 7;

    @Column(name = "max_request_more_feedback_time_days", nullable = false)
    private int maxRequestMoreFeedbackTimeDays = 7;

    @Column(name = "max_complaint_text_limit", nullable = false)
    private int maxComplaintTextLimit = DEFAULT_COMPLAINT_TEXT_LIMIT;

    @Column(name = "max_complaint_response_text_limit", nullable = false)
    private int maxComplaintResponseTextLimit = DEFAULT_COMPLAINT_TEXT_LIMIT;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Integer getMaxComplaints() {
        return maxComplaints;
    }

    public void setMaxComplaints(Integer maxComplaints) {
        this.maxComplaints = maxComplaints;
    }

    public Integer getMaxTeamComplaints() {
        return maxTeamComplaints;
    }

    public void setMaxTeamComplaints(Integer maxTeamComplaints) {
        this.maxTeamComplaints = maxTeamComplaints;
    }

    public int getMaxComplaintTimeDays() {
        return maxComplaintTimeDays;
    }

    public void setMaxComplaintTimeDays(int maxComplaintTimeDays) {
        this.maxComplaintTimeDays = maxComplaintTimeDays;
    }

    public int getMaxRequestMoreFeedbackTimeDays() {
        return maxRequestMoreFeedbackTimeDays;
    }

    public void setMaxRequestMoreFeedbackTimeDays(int maxRequestMoreFeedbackTimeDays) {
        this.maxRequestMoreFeedbackTimeDays = maxRequestMoreFeedbackTimeDays;
    }

    public int getMaxComplaintTextLimit() {
        return maxComplaintTextLimit;
    }

    public void setMaxComplaintTextLimit(int maxComplaintTextLimit) {
        this.maxComplaintTextLimit = maxComplaintTextLimit;
    }

    public int getMaxComplaintResponseTextLimit() {
        return maxComplaintResponseTextLimit;
    }

    public void setMaxComplaintResponseTextLimit(int maxComplaintResponseTextLimit) {
        this.maxComplaintResponseTextLimit = maxComplaintResponseTextLimit;
    }

    /**
     * @return true if complaints are enabled for this course (maxComplaintTimeDays > 0)
     */
    public boolean getComplaintsEnabled() {
        return this.maxComplaintTimeDays > 0;
    }

    /**
     * @return true if request more feedback is enabled for this course (maxRequestMoreFeedbackTimeDays > 0)
     */
    public boolean getRequestMoreFeedbackEnabled() {
        return maxRequestMoreFeedbackTimeDays > 0;
    }

    /**
     * Get the max complaint text limit for a specific exercise, considering exam exercises
     * which have a minimum limit of 2000 characters.
     *
     * @param exercise the exercise to get the limit for
     * @return the max complaint text limit
     */
    @JsonIgnore
    public int getMaxComplaintTextLimitForExercise(Exercise exercise) {
        if (exercise.isExamExercise()) {
            return Math.max(DEFAULT_COMPLAINT_TEXT_LIMIT, getMaxComplaintTextLimit());
        }
        return getMaxComplaintTextLimit();
    }

    /**
     * Get the max complaint response text limit for a specific exercise, considering exam exercises
     * which have a minimum limit of 2000 characters.
     *
     * @param exercise the exercise to get the limit for
     * @return the max complaint response text limit
     */
    @JsonIgnore
    public int getMaxComplaintResponseTextLimitForExercise(Exercise exercise) {
        if (exercise.isExamExercise()) {
            return Math.max(DEFAULT_COMPLAINT_TEXT_LIMIT, getMaxComplaintResponseTextLimit());
        }
        return getMaxComplaintResponseTextLimit();
    }

    /**
     * Get the maximum number of complaints per participant based on whether they are a team or individual.
     *
     * @param participant the participant (individual or team)
     * @return the max complaints allowed for this participant type
     */
    @JsonIgnore
    public Integer getMaxComplaintsPerParticipant(Participant participant) {
        return participant instanceof Team ? getMaxTeamComplaints() : getMaxComplaints();
    }

    /**
     * Validates the configuration for complaints and more feedback requests.
     *
     * @throws BadRequestAlertException if the configuration is invalid
     */
    public void validateComplaintsAndRequestMoreFeedbackConfig() {
        if (maxComplaints == null) {
            setMaxComplaints(3);
        }
        if (maxTeamComplaints == null) {
            setMaxTeamComplaints(3);
        }
        if (maxComplaints < 0) {
            throw new BadRequestAlertException("Max Complaints cannot be negative", Course.ENTITY_NAME, "maxComplaintsInvalid", true);
        }
        if (maxTeamComplaints < 0) {
            throw new BadRequestAlertException("Max Team Complaints cannot be negative", Course.ENTITY_NAME, "maxTeamComplaintsInvalid", true);
        }
        if (maxComplaintTimeDays < 0) {
            throw new BadRequestAlertException("Max Complaint Days cannot be negative", Course.ENTITY_NAME, "maxComplaintDaysInvalid", true);
        }
        if (maxComplaintTextLimit < 0) {
            throw new BadRequestAlertException("Max Complaint text limit cannot be negative", Course.ENTITY_NAME, "maxComplaintTextLimitInvalid", true);
        }
        if (maxComplaintTextLimit > COMPLAINT_TEXT_LIMIT) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be above " + COMPLAINT_TEXT_LIMIT + " characters.", Course.ENTITY_NAME,
                    "maxComplaintTextLimitInvalid", true);
        }
        if (maxComplaintResponseTextLimit < 0) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be negative", Course.ENTITY_NAME, "maxComplaintResponseTextLimitInvalid", true);
        }
        if (maxComplaintResponseTextLimit > COMPLAINT_RESPONSE_TEXT_LIMIT) {
            throw new BadRequestAlertException("Max Complaint response text limit cannot be above " + COMPLAINT_RESPONSE_TEXT_LIMIT + " characters.", Course.ENTITY_NAME,
                    "maxComplaintResponseTextLimitInvalid", true);
        }
        if (maxRequestMoreFeedbackTimeDays < 0) {
            throw new BadRequestAlertException("Max Request More Feedback Days cannot be negative", Course.ENTITY_NAME, "maxRequestMoreFeedbackDaysInvalid", true);
        }
        if (maxComplaintTimeDays == 0 && (maxComplaints != 0 || maxTeamComplaints != 0)) {
            throw new BadRequestAlertException("If complaints or more feedback requests are allowed, the complaint time in days must be positive.", Course.ENTITY_NAME,
                    "complaintsConfigInvalid", true);
        }
        if (maxComplaintTimeDays != 0 && maxComplaints == 0 && maxTeamComplaints == 0) {
            throw new BadRequestAlertException("If no complaints or more feedback requests are allowed, the complaint time in days should be set to zero.", Course.ENTITY_NAME,
                    "complaintsConfigInvalid", true);
        }
    }
}
