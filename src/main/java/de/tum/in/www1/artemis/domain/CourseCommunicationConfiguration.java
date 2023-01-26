package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Allows to configure the communication features of a course by the instructor.
 * This is a separate entity to allow for a more fine-grained configuration of the communication features.
 */
@Entity
@Table(name = "course_communication_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseCommunicationConfiguration extends DomainObject {

    public static final String ENTITY_NAME = "courseCommunicationConfiguration";

    @OneToOne(mappedBy = "courseCommunicationConfiguration")
    @JsonIgnore
    private Course course;

    // ============== QUESTION AND ANSWERS ==============

    /**
     * Corresponds to the "Communication" tab in the course management view.
     */
    @Column(name = "questions_and_answers_enabled")
    private Boolean questionsAndAnswersEnabled;

    // ============== MESSAGING ==============

    @Column(name = "channel_messaging_enabled")
    private Boolean channelMessagingEnabled;

    @Column(name = "group_messaging_enabled")
    private Boolean groupMessagingEnabled;

    @Column(name = "one_to_one_messaging_enabled")
    private Boolean oneToOneMessagingEnabled;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Boolean getQuestionsAndAnswersEnabled() {
        return questionsAndAnswersEnabled;
    }

    public void setQuestionsAndAnswersEnabled(Boolean questionsAndAnswersEnabled) {
        this.questionsAndAnswersEnabled = questionsAndAnswersEnabled;
    }

    public Boolean getChannelMessagingEnabled() {
        return channelMessagingEnabled;
    }

    public void setChannelMessagingEnabled(Boolean channelMessagingEnabled) {
        this.channelMessagingEnabled = channelMessagingEnabled;
    }

    public Boolean getGroupMessagingEnabled() {
        return groupMessagingEnabled;
    }

    public void setGroupMessagingEnabled(Boolean groupChatsMessagingEnabled) {
        this.groupMessagingEnabled = groupChatsMessagingEnabled;
    }

    public Boolean getOneToOneMessagingEnabled() {
        return oneToOneMessagingEnabled;
    }

    public void setOneToOneMessagingEnabled(Boolean oneToOneChatMessagingEnabled) {
        this.oneToOneMessagingEnabled = oneToOneChatMessagingEnabled;
    }
}
