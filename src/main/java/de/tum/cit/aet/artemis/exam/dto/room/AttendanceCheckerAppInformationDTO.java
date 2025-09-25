package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;

record AttendanceCheckerAppInformation() {
}

public record AttendanceCheckerAppInformationDTO(@NotNull Set<ExamRoom> examRoomsUsedInExam, Map<Long, ExamUser> examUsersWithByExamRoomId) {

}
