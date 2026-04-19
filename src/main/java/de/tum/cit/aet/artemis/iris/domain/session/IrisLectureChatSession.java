package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Entity
@DiscriminatorValue("LECTURE_CHAT")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisLectureChatSession extends IrisChatSession {

    @JsonIgnore
    private long lectureId;

    @Transient
    private Integer currentPdfPage;

    @Transient
    private Double currentVideoTimestamp;

    public IrisLectureChatSession() {
    }

    public IrisLectureChatSession(Lecture lecture, User user) {
        super(user);
        this.lectureId = lecture.getId();
    }

    public long getLectureId() {
        return lectureId;
    }

    public void setLectureId(long lectureId) {
        this.lectureId = lectureId;
    }

    public Integer getCurrentPdfPage() {
        return currentPdfPage;
    }

    public void setCurrentPdfPage(Integer currentPdfPage) {
        this.currentPdfPage = currentPdfPage;
    }

    public Double getCurrentVideoTimestamp() {
        return currentVideoTimestamp;
    }

    public void setCurrentVideoTimestamp(Double currentVideoTimestamp) {
        this.currentVideoTimestamp = currentVideoTimestamp;
    }

    @Override
    public boolean shouldSelectLLMUsage() {
        return true;
    }

    @Override
    public IrisChatMode getMode() {
        return IrisChatMode.LECTURE_CHAT;
    }
}
