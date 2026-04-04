package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.BadRequestException;

import org.hibernate.validator.constraints.Range;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupScheduleDTO(@NotNull LocalDateTime firstSessionStart, @NotNull LocalDateTime firstSessionEnd,
        @NotNull @Range(min = 1, max = 48) Integer repetitionFrequency, @NotNull LocalDate tutorialPeriodEnd, @NotNull @Size(min = 1, max = 255) String location) {

    /**
     * Validates that the tutorial period end is at most 2 years after the first session start.
     *
     * @throws BadRequestException if the tutorial period end is more than 2 years after the first session start
     */
    public void validateMaximumTutorialPeriodLength() {
        if (firstSessionStart == null || tutorialPeriodEnd == null) {
            return;
        }
        if (tutorialPeriodEnd.isAfter(firstSessionStart.toLocalDate().plusYears(2))) {
            throw new BadRequestException("The end of the teaching period must be at most 2 years after the first session's start.");
        }
    }

    /**
     * Converts a {@link TutorialGroupScheduleDTO} into a {@link TutorialGroupSchedule} entity instance.
     *
     * @param tutorialGroupScheduleDTO the schedule DTO to convert
     * @return the converted entity, or {@code null} if the DTO is {@code null}
     */
    public static TutorialGroupSchedule toTutorialGroupSchedule(TutorialGroupScheduleDTO tutorialGroupScheduleDTO) {
        if (tutorialGroupScheduleDTO == null) {
            return null;
        }
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

    /**
     * Converts a {@link TutorialGroupSchedule} entity into its {@link TutorialGroupScheduleDTO} representation.
     *
     * @param tutorialGroupSchedule the entity to convert
     * @return the converted DTO
     */
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
