import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';

export interface TutorialGroupScheduleDTO {
    id: number;
    dayOfWeek: number;
    startTime: string;
    endTime: string;
    repetitionFrequency: number;
}

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
