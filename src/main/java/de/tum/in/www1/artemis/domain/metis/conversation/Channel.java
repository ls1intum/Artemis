package de.tum.in.www1.artemis.domain.metis.conversation;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;

@Entity
@DiscriminatorValue("C")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Channel extends Conversation {

    /**
     * The name of the channel. Must be unique in the course.
     */
    @Column(name = "name")
    @Size(min = 1, max = 30)
    @NotBlank
    private String name;

    /**
     * What is the purpose of this channel? (not shown in header)
     */
    @Column(name = "description")
    @Size(min = 1, max = 250)
    @Nullable
    private String description;

    /**
     * What is the current topic of this channel? (shown in header)
     */
    @Column(name = "topic")
    @Size(min = 1, max = 250)
    @Nullable
    private String topic;

    /**
     * A channel is either public or private. Users need an invitation to join a private channel. Every user can join a public channel.
     */
    @Column(name = "is_public")
    @NotNull
    private Boolean isPublic;

    /**
     * An announcement channel is a special type of channel where only channel moderators and instructors can start new posts.
     * Answer posts are still possible so that students can ask questions concerning the announcement.
     */
    @Column(name = "is_announcement")
    @NotNull
    private Boolean isAnnouncementChannel;

    /**
     * A channel that is no longer needed can be archived or deleted.
     * Archived channels are closed to new activity, but the message history is retained and searchable.
     * The channel can be unarchived at any time.
     */
    @Column(name = "is_archived")
    @NotNull
    private Boolean isArchived;

    /**
     * Channels, that are meant to be seen by all course members by default, even if they haven't joined the channel yet, can be flagged with is_course_wide=true.
     * A conversation_participant entry will be created on the fly for these channels as soon as an entry is needed.
     */
    @Column(name = "is_course_wide")
    @NotNull
    private boolean isCourseWide = false;

    @OneToOne
    @JoinColumn(unique = true, name = "lecture_id")
    @JsonIgnoreProperties(value = "channel", allowSetters = true)
    private Lecture lecture;

    @OneToOne
    @JoinColumn(unique = true, name = "exercise_id")
    @JsonIgnoreProperties("channel")
    private Exercise exercise;

    @OneToOne
    @JoinColumn(unique = true, name = "exam_id")
    @JsonIgnoreProperties("channel")
    private Exam exam;

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @Nullable
    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(@Nullable Boolean isPublic) {
        this.isPublic = isPublic;
    }

    @Nullable
    public String getTopic() {
        return topic;
    }

    public void setTopic(@Nullable String topic) {
        this.topic = topic;
    }

    public Boolean getIsArchived() {
        return isArchived;
    }

    public void setIsArchived(Boolean archived) {
        isArchived = archived;
    }

    public Boolean getIsAnnouncementChannel() {
        return isAnnouncementChannel;
    }

    public void setIsAnnouncementChannel(Boolean announcementChannel) {
        isAnnouncementChannel = announcementChannel;
    }

    public boolean getIsCourseWide() {
        return isCourseWide;
    }

    public void setIsCourseWide(boolean isCourseWide) {
        this.isCourseWide = isCourseWide;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    @Override
    public String getHumanReadableNameForReceiver(User sender) {
        return getName();
    }

    /**
     * hide the details of the object, can be invoked before sending it as payload in a REST response or websocket message
     */
    @Override
    public void hideDetails() {
        // the following values are sometimes not needed when sending payloads to the client, so we allow to remove them
        setLecture(null);
        setExam(null);
        setExercise(null);
        super.hideDetails();
    }
}
