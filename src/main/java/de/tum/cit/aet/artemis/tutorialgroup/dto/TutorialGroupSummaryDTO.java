package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.dto.ChannelDTO;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupSummaryDTO(Long id, String title, @Nullable Integer capacity, @Nullable String campus, @Nullable String language, @Nullable String additionalInformation,
        Boolean isOnline, @Nullable TutorialGroupSummaryScheduleDTO tutorialGroupSchedule, @Nullable List<TutorialGroupSummarySessionDTO> tutorialGroupSessions,
        @Nullable ChannelDTO channel, @Nullable Boolean isUserRegistered, @Nullable Boolean isUserTutor, @Nullable Integer numberOfRegisteredUsers,
        @Nullable String teachingAssistantName, @Nullable Long teachingAssistantId, @Nullable String teachingAssistantImageUrl, @Nullable String courseTitle,
        @Nullable TutorialGroupSummarySessionDTO nextSession, @Nullable Integer averageAttendance) {

    public static TutorialGroupSummaryDTO from(TutorialGroup tutorialGroup) {
        return new TutorialGroupSummaryDTO(tutorialGroup.getId(), tutorialGroup.getTitle(), tutorialGroup.getCapacity(), tutorialGroup.getCampus(), tutorialGroup.getLanguage(),
                tutorialGroup.getAdditionalInformation(), tutorialGroup.getIsOnline(), TutorialGroupSummaryScheduleDTO.from(tutorialGroup.getTutorialGroupSchedule()),
                tutorialGroup.getTutorialGroupSessions() == null ? null : tutorialGroup.getTutorialGroupSessions().stream().map(TutorialGroupSummarySessionDTO::from).toList(),
                tutorialGroup.getChannel(), tutorialGroup.getIsUserRegistered(), tutorialGroup.getIsUserTutor(), tutorialGroup.getNumberOfRegisteredUsers(),
                tutorialGroup.getTeachingAssistantName(), tutorialGroup.getTeachingAssistantId(), tutorialGroup.getTeachingAssistantImageUrl(), tutorialGroup.getCourseTitle(),
                TutorialGroupSummarySessionDTO.from(tutorialGroup.getNextSession()), tutorialGroup.getAverageAttendance());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSummaryScheduleDTO(Long id, @Nullable Integer dayOfWeek, @Nullable String startTime, @Nullable String endTime, @Nullable Integer repetitionFrequency,
            @Nullable String location, @Nullable String validFromInclusive, @Nullable String validToInclusive) {

        public static TutorialGroupSummaryScheduleDTO from(TutorialGroupSchedule schedule) {
            if (schedule == null) {
                return null;
            }
            return new TutorialGroupSummaryScheduleDTO(schedule.getId(), schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(), schedule.getRepetitionFrequency(),
                    schedule.getLocation(), schedule.getValidFromInclusive(), schedule.getValidToInclusive());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSummarySessionDTO(Long id, @Nullable String start, @Nullable String end, @Nullable String status, @Nullable String statusExplanation,
            @Nullable String location, @Nullable TutorialGroupFreePeriodDTO tutorialGroupFreePeriod, @Nullable Integer attendanceCount) {

        public static TutorialGroupSummarySessionDTO from(TutorialGroupSession session) {
            if (session == null) {
                return null;
            }
            return new TutorialGroupSummarySessionDTO(session.getId(), session.getStart() == null ? null : session.getStart().toString(),
                    session.getEnd() == null ? null : session.getEnd().toString(), session.getStatus() == null ? null : session.getStatus().name(), session.getStatusExplanation(),
                    session.getLocation(), TutorialGroupFreePeriodDTO.from(session.getTutorialGroupFreePeriod()), session.getAttendanceCount());
        }
    }
}
