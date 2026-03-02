package de.tum.cit.aet.artemis.tutorialgroup.dto;

import static jakarta.persistence.Persistence.getPersistenceUtil;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.dto.ChannelDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupResponseDTO(@Nullable Long id, @Nullable String title, @Nullable CourseDTO course, @Nullable Integer capacity, @Nullable String campus,
        @Nullable String language, @Nullable String additionalInformation, @Nullable Boolean isOnline, @Nullable UserDTO teachingAssistant,
        @Nullable TutorialGroupScheduleDTO tutorialGroupSchedule, @Nullable List<TutorialGroupSessionDTO> tutorialGroupSessions,
        @Nullable List<TutorialGroupRegistrationDTO> registrations, @Nullable ChannelDTO channel, @Nullable Boolean isUserRegistered, @Nullable Boolean isUserTutor,
        @Nullable Integer numberOfRegisteredUsers, @Nullable String teachingAssistantName, @Nullable Long teachingAssistantId, @Nullable String teachingAssistantImageUrl,
        @Nullable String courseTitle, @Nullable TutorialGroupSessionDTO nextSession, @Nullable Integer averageAttendance) {

    /**
     * Creates a response DTO from a tutorial group entity.
     *
     * @param tutorialGroup the tutorial group entity
     * @return the mapped DTO
     */
    public static TutorialGroupResponseDTO from(TutorialGroup tutorialGroup) {
        return new TutorialGroupResponseDTO(tutorialGroup.getId(), tutorialGroup.getTitle(), CourseDTO.from(tutorialGroup.getCourse()), tutorialGroup.getCapacity(),
                tutorialGroup.getCampus(), tutorialGroup.getLanguage(), tutorialGroup.getAdditionalInformation(), tutorialGroup.getIsOnline(),
                UserDTO.from(tutorialGroup.getTeachingAssistant()), TutorialGroupScheduleDTO.from(tutorialGroup.getTutorialGroupSchedule()), tutorialGroupSessions(tutorialGroup),
                registrations(tutorialGroup), tutorialGroup.getChannel(), tutorialGroup.getIsUserRegistered(), tutorialGroup.getIsUserTutor(),
                tutorialGroup.getNumberOfRegisteredUsers(), tutorialGroup.getTeachingAssistantName(), tutorialGroup.getTeachingAssistantId(),
                tutorialGroup.getTeachingAssistantImageUrl(), tutorialGroup.getCourseTitle(), TutorialGroupSessionDTO.from(tutorialGroup.getNextSession()),
                tutorialGroup.getAverageAttendance());
    }

    private static List<TutorialGroupSessionDTO> tutorialGroupSessions(TutorialGroup tutorialGroup) {
        if (!getPersistenceUtil().isLoaded(tutorialGroup, "tutorialGroupSessions") || tutorialGroup.getTutorialGroupSessions() == null) {
            return null;
        }
        return tutorialGroup.getTutorialGroupSessions().stream().map(TutorialGroupSessionDTO::from).toList();
    }

    private static List<TutorialGroupRegistrationDTO> registrations(TutorialGroup tutorialGroup) {
        if (!getPersistenceUtil().isLoaded(tutorialGroup, "registrations") || tutorialGroup.getRegistrations() == null) {
            return null;
        }
        return tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistrationDTO::from).toList();
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDTO(@Nullable Long id) {

        public static CourseDTO from(@Nullable Course course) {
            if (course == null) {
                return null;
            }
            return new CourseDTO(course.getId());
        }
    }

    /**
     * Minimal user projection for tutorial group responses.
     *
     * Registration numbers are intentionally omitted to avoid exposing personal data.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record UserDTO(@Nullable Long id, @Nullable String login, @Nullable String firstName, @Nullable String lastName, @Nullable String name, @Nullable String imageUrl) {

        public static UserDTO from(@Nullable User user) {
            if (user == null) {
                return null;
            }
            return new UserDTO(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getName(), user.getImageUrl());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupRegistrationDTO(@Nullable Long id, @Nullable TutorialGroupRegistrationType type, @Nullable UserDTO student) {

        public static TutorialGroupRegistrationDTO from(TutorialGroupRegistration tutorialGroupRegistration) {
            return new TutorialGroupRegistrationDTO(tutorialGroupRegistration.getId(), tutorialGroupRegistration.getType(), UserDTO.from(tutorialGroupRegistration.getStudent()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupScheduleDTO(@Nullable Long id, @Nullable Integer dayOfWeek, @Nullable String startTime, @Nullable String endTime,
            @Nullable Integer repetitionFrequency, @Nullable String validFromInclusive, @Nullable String validToInclusive, @Nullable String location) {

        /**
         * Creates a tutorial group schedule DTO from a schedule entity.
         *
         * @param tutorialGroupSchedule the tutorial group schedule entity
         * @return the mapped DTO, or {@code null} when the input is {@code null}
         */
        public static TutorialGroupScheduleDTO from(@Nullable TutorialGroupSchedule tutorialGroupSchedule) {
            if (tutorialGroupSchedule == null) {
                return null;
            }
            return new TutorialGroupScheduleDTO(tutorialGroupSchedule.getId(), tutorialGroupSchedule.getDayOfWeek(), tutorialGroupSchedule.getStartTime(),
                    tutorialGroupSchedule.getEndTime(), tutorialGroupSchedule.getRepetitionFrequency(), tutorialGroupSchedule.getValidFromInclusive(),
                    tutorialGroupSchedule.getValidToInclusive(), tutorialGroupSchedule.getLocation());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSessionScheduleDTO(@Nullable Long id) {

        public static TutorialGroupSessionScheduleDTO from(@Nullable TutorialGroupSchedule tutorialGroupSchedule) {
            if (tutorialGroupSchedule == null) {
                return null;
            }
            return new TutorialGroupSessionScheduleDTO(tutorialGroupSchedule.getId());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupFreePeriodDTO(@Nullable Long id, @Nullable ZonedDateTime start, @Nullable ZonedDateTime end, @Nullable String reason) {

        /**
         * Creates a free period DTO from a free period entity.
         *
         * @param tutorialGroupFreePeriod the free period entity
         * @return the mapped DTO, or {@code null} when the input is {@code null}
         */
        public static TutorialGroupFreePeriodDTO from(@Nullable TutorialGroupFreePeriod tutorialGroupFreePeriod) {
            if (tutorialGroupFreePeriod == null) {
                return null;
            }
            return new TutorialGroupFreePeriodDTO(tutorialGroupFreePeriod.getId(), tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd(),
                    tutorialGroupFreePeriod.getReason());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSessionDTO(@Nullable Long id, @Nullable ZonedDateTime start, @Nullable ZonedDateTime end, @Nullable TutorialGroupSessionStatus status,
            @Nullable String statusExplanation, @Nullable String location, @Nullable Integer attendanceCount, @Nullable TutorialGroupSessionScheduleDTO tutorialGroupSchedule,
            @Nullable TutorialGroupFreePeriodDTO tutorialGroupFreePeriod) {

        /**
         * Creates a tutorial group session DTO from a session entity.
         *
         * @param tutorialGroupSession the session entity
         * @return the mapped DTO, or {@code null} when the input is {@code null}
         */
        public static TutorialGroupSessionDTO from(@Nullable TutorialGroupSession tutorialGroupSession) {
            if (tutorialGroupSession == null) {
                return null;
            }
            return new TutorialGroupSessionDTO(tutorialGroupSession.getId(), tutorialGroupSession.getStart(), tutorialGroupSession.getEnd(), tutorialGroupSession.getStatus(),
                    tutorialGroupSession.getStatusExplanation(), tutorialGroupSession.getLocation(), tutorialGroupSession.getAttendanceCount(),
                    TutorialGroupSessionScheduleDTO.from(tutorialGroupSession.getTutorialGroupSchedule()),
                    TutorialGroupFreePeriodDTO.from(tutorialGroupSession.getTutorialGroupFreePeriod()));
        }
    }
}
