package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupScheduleDTO(@NotNull LocalDateTime firstSessionStart, @NotNull LocalDateTime firstSessionEnd, @Min(1) int repetitionFrequency,
        @NotNull LocalDate tutorialPeriodEnd, @NotNull String location) {

    public static TutorialGroupSchedule toTutorialGroupSchedule(TutorialGroupScheduleDTO tutorialGroupScheduleDTO) {
        if (tutorialGroupScheduleDTO == null)
            return null;
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        var schedule = new TutorialGroupSchedule();
        schedule.setDayOfWeek(tutorialGroupScheduleDTO.firstSessionStart().getDayOfWeek().getValue());
        schedule.setStartTime(tutorialGroupScheduleDTO.firstSessionStart().format(timeFormatter));
        schedule.setEndTime(tutorialGroupScheduleDTO.firstSessionEnd().format(timeFormatter));
        schedule.setRepetitionFrequency(tutorialGroupScheduleDTO.repetitionFrequency());
        schedule.setValidFromInclusive(tutorialGroupScheduleDTO.firstSessionStart().format(dateFormatter));
        schedule.setValidToInclusive(tutorialGroupScheduleDTO.tutorialPeriodEnd().toString());
        schedule.setLocation(tutorialGroupScheduleDTO.location());
        return schedule;
    }

    public static TutorialGroupScheduleDTO toTutorialGroupScheduleDTO(TutorialGroupSchedule tutorialGroupSchedule) {
        LocalDate tutorialPeriodStart = LocalDate.parse(tutorialGroupSchedule.getValidFromInclusive());
        LocalDate firstSessionDate = tutorialPeriodStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(tutorialGroupSchedule.getDayOfWeek())));
        LocalTime firstSessionStartTime = LocalTime.parse(tutorialGroupSchedule.getStartTime());
        LocalDateTime firstSessionStart = LocalDateTime.of(firstSessionDate, firstSessionStartTime);
        LocalTime firstSessionEndTime = LocalTime.parse(tutorialGroupSchedule.getEndTime());
        LocalDateTime firstSessionEnd = LocalDateTime.of(firstSessionDate, firstSessionEndTime);
        LocalDate tutorialPeriodEnd = LocalDate.parse(tutorialGroupSchedule.getValidToInclusive());
        return new TutorialGroupScheduleDTO(firstSessionStart, firstSessionEnd, tutorialGroupSchedule.getRepetitionFrequency(), tutorialPeriodEnd,
                tutorialGroupSchedule.getLocation());
    }
}
