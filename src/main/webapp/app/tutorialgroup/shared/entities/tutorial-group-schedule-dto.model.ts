import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';

/**
 * Data Transfer Object representing the recurring schedule
 * configuration of a tutorial group.
 *
 */
export interface TutorialGroupScheduleDTO {
    id: number;
    dayOfWeek: number;
    startTime: string;
    endTime: string;
    repetitionFrequency: number;
}

/**
 * Converts a {@link TutorialGroupSchedule} entity into a
 * {@link TutorialGroupScheduleDTO}.
 *
 * If the provided entity is undefined, the function returns undefined.
 * This enables safe optional mapping in parent conversion functions.
 *
 * @param entity the schedule entity to convert
 * @returns the corresponding DTO or undefined if the entity is undefined
 */
export const entityToTutorialGroupScheduleDTO = (entity?: TutorialGroupSchedule): TutorialGroupScheduleDTO | undefined => {
    if (!entity) {
        return undefined;
    }
    return {
        id: entity.id!,
        dayOfWeek: entity.dayOfWeek!,
        startTime: entity.startTime!,
        endTime: entity.endTime!,
        repetitionFrequency: entity.repetitionFrequency!,
    };
};
