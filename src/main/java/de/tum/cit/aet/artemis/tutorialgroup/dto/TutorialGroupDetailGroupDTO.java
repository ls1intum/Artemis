package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupDetailGroupDTO {

    private final long id;

    @NotNull
    private final String title;

    @NotNull
    private final String language;

    private final boolean isOnline;

    @NotNull
    private List<TutorialGroupDetailSessionDTO> sessions;

    @NotNull
    private final String teachingAssistantName;

    @NotNull
    private final String teachingAssistantLogin;

    private final String teachingAssistantImageUrl;

    private final Integer capacity;

    private final String campus;

    private final Long groupChannelId;

    private Long tutorChatId;

    @NotNull
    private final TutorialGroupDetailGroupDTOMetaData metaData;

    public TutorialGroupDetailGroupDTO(long id, String title, String language, boolean isOnline, String teachingAssistantName, String teachingAssistantLogin,
            String teachingAssistantImageUrl, Integer capacity, String campus, Long groupChannelId, TutorialGroupDetailGroupDTOMetaData metaData) {
        this.id = id;
        this.title = title;
        this.language = language;
        this.isOnline = isOnline;
        this.teachingAssistantName = teachingAssistantName;
        this.teachingAssistantLogin = teachingAssistantLogin;
        this.teachingAssistantImageUrl = teachingAssistantImageUrl;
        this.capacity = capacity;
        this.campus = campus;
        this.groupChannelId = groupChannelId;
        this.metaData = metaData;
        this.sessions = List.of();
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public List<TutorialGroupDetailSessionDTO> getSessions() {
        return sessions;
    }

    public void setSessions(List<TutorialGroupDetailSessionDTO> sessions) {
        this.sessions = sessions;
    }

    public String getTeachingAssistantName() {
        return teachingAssistantName;
    }

    public String getTeachingAssistantLogin() {
        return teachingAssistantLogin;
    }

    public String getTeachingAssistantImageUrl() {
        return teachingAssistantImageUrl;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public String getCampus() {
        return campus;
    }

    public Long getGroupChannelId() {
        return groupChannelId;
    }

    public Long getTutorChatId() {
        return tutorChatId;
    }

    public void setTutorChatId(Long tutorChatId) {
        this.tutorChatId = tutorChatId;
    }

    @JsonIgnore
    public TutorialGroupDetailGroupDTOMetaData getMetaData() {
        return metaData;
    }
}
