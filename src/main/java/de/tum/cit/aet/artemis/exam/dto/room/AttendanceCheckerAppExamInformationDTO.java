package de.tum.cit.aet.artemis.exam.dto.room;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ExamRoomForAttendanceCheckerDTO(
    long id,
    @NotBlank String roomNumber,
    @Nullable String alternativeRoomNumber,
    @NotBlank String name,
    @Nullable String alternativeName,
    @NotBlank String building,
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
record ExamUserLocationDTO(
    @Nullable Long roomId,  // null if legacy version
    @NotBlank String roomNumber,  // examUser.plannedRoom if legacy version
    @NotBlank String seatName  // examUser.plannedSeat if legacy version
) {
    static ExamUserLocationDTO plannedFrom(ExamUser examUser) {
        final boolean isLegacy = examUser.getPlannedRoomTransient() == null || examUser.getPlannedSeatTransient() == null;

        return new ExamUserLocationDTO(
            isLegacy ? null : examUser.getPlannedRoomTransient().getId(),
            isLegacy ? examUser.getPlannedRoom() : examUser.getPlannedRoomTransient().getRoomNumber(),
            isLegacy ? examUser.getPlannedSeat() : examUser.getPlannedSeatTransient().name()
        );
    }

    static ExamUserLocationDTO actualFrom(ExamUser examUser) {
        if (examUser.getActualRoom() == null || examUser.getActualSeat() == null) {
            // examUser has not been moved
            return null;
        }

        final boolean useLegacyFields = examUser.getActualRoomTransient() == null || examUser.getActualSeatTransient() == null;

        return new ExamUserLocationDTO(
            useLegacyFields ? null : examUser.getActualRoomTransient().getId(),
            useLegacyFields ? examUser.getActualRoom() : examUser.getActualRoomTransient().getRoomNumber(),
            useLegacyFields ? examUser.getActualSeat() : examUser.getActualSeatTransient().name()
        );
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ExamUserWithExamRoomAndSeatDTO (
    @NotBlank @Size(max = 50) String login,
    // Names are nullable because not everyone has a first and/or last name
    @Nullable @Size(max = 50) String firstName,
    @Nullable @Size(max = 50) String lastName,
    @NotBlank @Size(max = 10) String registrationNumber,
    @Nullable @Email @Size(max = 100) String email,
    @Nullable String imageUrl,
    boolean didCheckImage,
    boolean didCheckName,
    boolean didCheckRegistrationNumber,
    boolean didCheckLogin,
    @Nullable String signingImagePath,
    @NotNull ExamUserLocationDTO plannedLocation,
    @Nullable ExamUserLocationDTO actualLocation
) {
    static ExamUserWithExamRoomAndSeatDTO from(ExamUser examUser) {
        return new ExamUserWithExamRoomAndSeatDTO(
            examUser.getUser().getLogin(),
            examUser.getUser().getFirstName(),
            examUser.getUser().getLastName(),
            examUser.getUser().getRegistrationNumber(),
            examUser.getUser().getEmail(),
            examUser.getStudentImagePath(),
            examUser.getDidCheckImage(),
            examUser.getDidCheckName(),
            examUser.getDidCheckRegistrationNumber(),
            examUser.getDidCheckLogin(),
            examUser.getSigningImagePath(),
            ExamUserLocationDTO.plannedFrom(examUser),
            ExamUserLocationDTO.actualFrom(examUser)
        );
    }
}

/**
 * DTO containing all relevant information for the attendance checker app.
 * This DTO and all its children are exclusively sent from the server to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AttendanceCheckerAppExamInformationDTO(
    long examId,
    @NotBlank String examTitle,
    @NotNull ZonedDateTime startDate,
    @NotNull ZonedDateTime endDate,
    boolean isTestExam,
    long courseId,
    @NotBlank String courseTitle,
    @NotNull Set<ExamRoomForAttendanceCheckerDTO> examRoomsUsedInExam,  // empty if legacy version
    @NotEmpty Set<ExamUserWithExamRoomAndSeatDTO> examUsersWithExamRoomAndSeat
) {
    /**
     * Create an AttendanceCheckerAppExamInformationDTO from the given exam and its rooms
     *
     * @param exam the exam
     * @param examRooms the rooms used in the exam
     * @return information for the attendance checker app
     *
     * @implNote To avoid deep copies this operation mutates the given exam's {@link Exam#getExamUsers()}.
     * Re-fetch the {@link ExamUser}s if you want to perform additional operations on or with them afterward.
     *
     */
    public static AttendanceCheckerAppExamInformationDTO from(Exam exam, Set<ExamRoom> examRooms) {
        Set<ExamUser> examUsersWithUndistributedUsers = exam.getExamUsers().stream()
            .peek(examUser -> {
                    if (!StringUtils.hasText(examUser.getPlannedRoom())) {
                        examUser.setPlannedRoom("No Room set");
                    }

                    if (!StringUtils.hasText(examUser.getPlannedSeat())) {
                        examUser.setPlannedSeat("No Seat set");
                    }
                }
            ).collect(Collectors.toSet());

        return new AttendanceCheckerAppExamInformationDTO(
            exam.getId(),
            exam.getTitle(),
            exam.getStartDate(),
            exam.getEndDate(),
            exam.isTestExam(),
            exam.getCourse().getId(),
            exam.getCourse().getTitle(),
            examRooms.stream().map(ExamRoomForAttendanceCheckerDTO::from).collect(Collectors.toSet()),
            examUsersWithUndistributedUsers.stream().map(ExamUserWithExamRoomAndSeatDTO::from).collect(Collectors.toSet())
        );
    }
}
// @formatter:on
