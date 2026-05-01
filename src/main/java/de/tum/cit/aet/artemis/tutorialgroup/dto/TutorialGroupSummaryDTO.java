package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.dto.ChannelDTO;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

/**
 * REST response payload for the tutorial group overview list of a course.
 * <p>
 * Contains the public attributes of a tutorial group plus flattened transient fields populated by
 * {@code TutorialGroupService.setTransientPropertiesForUser}. Privacy-sensitive fields are cleared
 * by {@code TutorialGroup.hidePrivacySensitiveInformation} for users who do not have managing
 * rights for the tutorial group.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupSummaryDTO(Long id, String title, @Nullable Integer capacity, @Nullable String campus, @Nullable String language, @Nullable String additionalInformation,
        Boolean isOnline, @Nullable TutorialGroupSummaryScheduleDTO tutorialGroupSchedule, @Nullable List<TutorialGroupSummarySessionDTO> tutorialGroupSessions,
        @Nullable ChannelDTO channel, @Nullable Boolean isUserRegistered, @Nullable Boolean isUserTutor, @Nullable Integer numberOfRegisteredUsers,
        @Nullable String teachingAssistantName, @Nullable Long teachingAssistantId, @Nullable String teachingAssistantImageUrl, @Nullable String courseTitle,
        @Nullable TutorialGroupSummarySessionDTO nextSession, @Nullable Integer averageAttendance) {

    /**
     * Builds a {@link TutorialGroupSummaryDTO} from a tutorial group entity enriched with summary information.
     *
     * @param tutorialGroup the tutorial group entity to convert
     * @return the converted tutorial group summary DTO
     */
    public static TutorialGroupSummaryDTO from(TutorialGroup tutorialGroup) {
        return new TutorialGroupSummaryDTO(tutorialGroup.getId(), tutorialGroup.getTitle(), tutorialGroup.getCapacity(), tutorialGroup.getCampus(), tutorialGroup.getLanguage(),
                tutorialGroup.getAdditionalInformation(), tutorialGroup.getIsOnline(), TutorialGroupSummaryScheduleDTO.from(tutorialGroup.getTutorialGroupSchedule()),
                tutorialGroup.getTutorialGroupSessions() == null ? null : tutorialGroup.getTutorialGroupSessions().stream().map(TutorialGroupSummarySessionDTO::from).toList(),
                tutorialGroup.getChannel(), tutorialGroup.getIsUserRegistered(), tutorialGroup.getIsUserTutor(), tutorialGroup.getNumberOfRegisteredUsers(),
                tutorialGroup.getTeachingAssistantName(), tutorialGroup.getTeachingAssistantId(), tutorialGroup.getTeachingAssistantImageUrl(), tutorialGroup.getCourseTitle(),
                TutorialGroupSummarySessionDTO.from(tutorialGroup.getNextSession()), tutorialGroup.getAverageAttendance());
    }

    /**
     * The recurring schedule a tutorial group's sessions are generated from.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSummaryScheduleDTO(Long id, @Nullable Integer dayOfWeek, @Nullable String startTime, @Nullable String endTime, @Nullable Integer repetitionFrequency,
            @Nullable String location, @Nullable String validFromInclusive, @Nullable String validToInclusive) {

        /**
         * Builds a {@link TutorialGroupSummaryScheduleDTO} from a persisted tutorial group schedule.
         *
         * @param schedule the schedule entity to convert, may be {@code null}
         * @return the converted schedule DTO, or {@code null} when no schedule is present
         */
        public static TutorialGroupSummaryScheduleDTO from(TutorialGroupSchedule schedule) {
            if (schedule == null) {
                return null;
            }
            return new TutorialGroupSummaryScheduleDTO(schedule.getId(), schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime(), schedule.getRepetitionFrequency(),
                    schedule.getLocation(), schedule.getValidFromInclusive(), schedule.getValidToInclusive());
        }
    }

    /**
     * A single tutorial group session as exposed in the overview list, optionally annotated with the
     * free period that cancelled it.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSummarySessionDTO(Long id, @Nullable ZonedDateTime start, @Nullable ZonedDateTime end, @Nullable TutorialGroupSessionStatus status,
            @Nullable String statusExplanation, @Nullable String location, @Nullable TutorialGroupFreePeriodDTO tutorialGroupFreePeriod, @Nullable Integer attendanceCount) {

        /**
         * Builds a {@link TutorialGroupSummarySessionDTO} from a persisted tutorial group session.
         *
         * @param session the session entity to convert, may be {@code null}
         * @return the converted session DTO, or {@code null} when no session is present
         */
        public static TutorialGroupSummarySessionDTO from(TutorialGroupSession session) {
            if (session == null) {
                return null;
            }
            return new TutorialGroupSummarySessionDTO(session.getId(), session.getStart(), session.getEnd(), session.getStatus(), session.getStatusExplanation(),
                    session.getLocation(), TutorialGroupFreePeriodDTO.from(session.getTutorialGroupFreePeriod()), session.getAttendanceCount());
        }
    }
}
