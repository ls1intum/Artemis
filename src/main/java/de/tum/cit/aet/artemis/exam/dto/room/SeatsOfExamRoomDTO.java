package de.tum.cit.aet.artemis.exam.dto.room;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SeatsOfExamRoomDTO(@NotNull List<String> seats) {

    public static SeatsOfExamRoomDTO from(ExamRoom examRoom) {
        return new SeatsOfExamRoomDTO(examRoom.getSeats().stream().map(ExamSeatDTO::name).toList());
    }
}
