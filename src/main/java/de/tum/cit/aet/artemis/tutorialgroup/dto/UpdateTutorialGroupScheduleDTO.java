package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.lang.Nullable;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;

public record UpdateTutorialGroupScheduleDTO(@NotNull LocalDateTime firstSessionStart, @NotNull LocalDateTime firstSessionEnd, @Min(1) int repetitionFrequency,
        @NotNull LocalDate tutorialPeriodEnd, @Nullable String location) {

    public static TutorialGroupSchedule toTutorialGroupSchedule(UpdateTutorialGroupScheduleDTO updateTutorialGroupScheduleDTO) {
        if (updateTutorialGroupScheduleDTO == null)
            return null;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        var schedule = new TutorialGroupSchedule();
        schedule.setDayOfWeek(updateTutorialGroupScheduleDTO.firstSessionStart().getDayOfWeek().getValue());
        schedule.setStartTime(updateTutorialGroupScheduleDTO.firstSessionStart().format(timeFormatter));
        schedule.setEndTime(updateTutorialGroupScheduleDTO.firstSessionEnd().format(timeFormatter));
        schedule.setRepetitionFrequency(updateTutorialGroupScheduleDTO.repetitionFrequency());
        schedule.setValidFromInclusive(updateTutorialGroupScheduleDTO.firstSessionStart().format(dateFormatter));
        schedule.setValidToInclusive(updateTutorialGroupScheduleDTO.tutorialPeriodEnd().toString());
        schedule.setLocation(updateTutorialGroupScheduleDTO.location());
        return schedule;
    }
}
