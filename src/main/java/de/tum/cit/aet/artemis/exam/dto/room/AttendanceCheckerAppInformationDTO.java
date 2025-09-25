package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ExamRoomForAttendanceCheckerDTO(
    long id,
    @NotEmpty String roomNumber,
    @Nullable String alternativeRoomNumber,
    @NotEmpty String name,
    @Nullable String alternativeName,
    @NotEmpty String building,
    @NotNull List<ExamSeatDTO> seats
) {
    static ExamRoomForAttendanceCheckerDTO from(ExamRoom examRoom) {
        return new ExamRoomForAttendanceCheckerDTO(
            examRoom.getId(),
            examRoom.getRoomNumber(),
            examRoom.getAlternativeRoomNumber(),
            examRoom.getName(),
            examRoom.getAlternativeName(),
            examRoom.getBuilding(),
            examRoom.getSeats()
        );
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ExamUserWithExamRoomAndSeatDTO (
    @NotBlank @Size(max = 50) String login,
    @NotBlank @Size(max = 50) String firstName,
    @NotBlank @Size(max = 50) String lastName,
    @NotBlank @Size(max = 10) String registrationNumber,
    @NotBlank String examRoomNumber,
    @NotNull ExamSeatDTO examSeat
) {
    static ExamUserWithExamRoomAndSeatDTO from(ExamUser examUser) {
        return new ExamUserWithExamRoomAndSeatDTO(
            examUser.getUser().getLogin(),
            examUser.getUser().getFirstName(),
            examUser.getUser().getLastName(),
            examUser.getUser().getRegistrationNumber(),
            examUser.getPlannedRoomTransient().getRoomNumber(),
            examUser.getPlannedSeatTransient()
        );
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttendanceCheckerAppInformationDTO(
    @NotNull Set<ExamRoomForAttendanceCheckerDTO> examRoomsUsedInExam,
    @NotNull Set<ExamUserWithExamRoomAndSeatDTO> examUsersWithExamRoomAndSeat
) {
    public static AttendanceCheckerAppInformationDTO from(Set<ExamRoom> examRooms, Set<ExamUser> examUsersWithTransientRoomAndSeat) {
        return new AttendanceCheckerAppInformationDTO(
            examRooms.stream().map(ExamRoomForAttendanceCheckerDTO::from).collect(Collectors.toSet()),
            examUsersWithTransientRoomAndSeat.stream().map(ExamUserWithExamRoomAndSeatDTO::from).collect(Collectors.toSet())
        );
    }
}
// @formatter:on
