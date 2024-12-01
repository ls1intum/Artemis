package de.tum.cit.aet.artemis.communication.domain.notification;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This helper class is used to store transient information related to the notification,
 * especially regarding the creation of valid URLs/Links on the server side for emails
 * NotificationTargets are intended to have different attributes, i.e. many will be null
 */
@JsonInclude(NON_EMPTY) // needed for ObjectMapper to ignore null values
public class NotificationTarget {

    private static final Logger log = LoggerFactory.getLogger(NotificationTarget.class);

    private String message;

    private Long identifier; // based on the notification subject e.g. programmingExercise.getId()

    private String entity; // infix of the URL e.g. programming-exercises

    private Long courseId; // will be "course" in toJsonString()

    private String mainPage; // infix of the URL e.g. course-management

    private String problemStatement;

    private Long exerciseId; // will be "exercise" in toJsonString()

    private Long examId; // will be "exam" in toJsonString()

    private Long lectureId; // will stay "lectureId" in toJsonString()

    private Long conversationId; // will stay "conversationId" in toJsonString()

    public NotificationTarget() {
        // intentionally empty. e.g. used for cases without courseId
    }

    public NotificationTarget(Long identifier, Long courseId) {
        this.identifier = identifier;
        this.courseId = courseId;
    }

    public NotificationTarget(String entity, Long courseId, String mainPage) {
        this.entity = entity;
        this.courseId = courseId;
        this.mainPage = mainPage;
    }

    public NotificationTarget(String message, Long identifier, String entity, Long courseId, String mainPage) {
        this.message = message;
        this.identifier = identifier;
        this.entity = entity;
        this.courseId = courseId;
        this.mainPage = mainPage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonProperty("id")
    public Long getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Long identifier) {
        this.identifier = identifier;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    @JsonProperty("course")
    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getMainPage() {
        return mainPage;
    }

    public void setMainPage(String mainPage) {
        this.mainPage = mainPage;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    @JsonProperty("exercise")
    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    @JsonProperty("exam")
    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }

    @JsonProperty("lecture")
    public Long getLectureId() {
        return lectureId;
    }

    public void setLectureId(Long lectureId) {
        this.lectureId = lectureId;
    }

    @JsonProperty("conversation")
    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    /**
     * @return the NotificationTarget as a JSON String
     *         This is needed to stay consistent with the legacy implementation & data in the DB
     */
    public String toJsonString() {
        String result = null;
        try {
            result = new ObjectMapper().writeValueAsString(this);
        }
        catch (JsonProcessingException exception) {
            log.error(exception.getMessage(), exception);
        }
        return result;
    }
}
