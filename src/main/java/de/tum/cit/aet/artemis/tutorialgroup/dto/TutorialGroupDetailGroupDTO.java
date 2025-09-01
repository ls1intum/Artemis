package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.Set;

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
    private Set<TutorialGroupDetailSessionDTO> sessions;

    @NotNull
    private final String teachingAssistantName;

    private final String teachingAssistantImageUrl;

    private final int capacity;

    private final String campus;

    private final TutorialGroupDetailScheduleDTO schedule;

    public TutorialGroupDetailGroupDTO(long id, String title, String language, boolean isOnline, String teachingAssistantName, String teachingAssistantImageUrl, int capacity,
            String campus, TutorialGroupDetailScheduleDTO schedule) {
        this.id = id;
        this.title = title;
        this.language = language;
        this.isOnline = isOnline;
        this.teachingAssistantName = teachingAssistantName;
        this.teachingAssistantImageUrl = teachingAssistantImageUrl;
        this.capacity = capacity;
        this.campus = campus;
        this.schedule = schedule;
        this.sessions = Set.of();
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

    public String getTeachingAssistantName() {
        return teachingAssistantName;
    }

    public String getTeachingAssistantImageUrl() {
        return teachingAssistantImageUrl;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getCampus() {
        return campus;
    }

    @JsonIgnore
    public TutorialGroupDetailScheduleDTO getSchedule() {
        return schedule;
    }

    public Set<TutorialGroupDetailSessionDTO> getSessions() {
        return sessions;
    }

    public void setSessions(Set<TutorialGroupDetailSessionDTO> sessions) {
        this.sessions = sessions;
    }
}
