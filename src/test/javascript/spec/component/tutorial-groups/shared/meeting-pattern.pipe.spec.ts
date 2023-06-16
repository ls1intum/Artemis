import { MeetingPatternPipe } from 'app/course/tutorial-groups/shared/meeting-pattern.pipe';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';

describe('MeetingPatternPipe', () => {
    const translateService = { instant: jest.fn() };

    const pipe = new MeetingPatternPipe(translateService as any);

    beforeEach(() => {
        translateService.instant.mockClear();
    });

    it('should return an empty string if schedule is undefined', () => {
        expect(pipe.transform(undefined)).toBe('');
    });

    it('should return an empty string', () => {
        const schedule = undefined;
        translateService.instant.mockImplementation((key) => key);

        const result = pipe.transform(schedule, true);
        expect(result).toBe('');
        expect(translateService.instant).not.toHaveBeenCalled();
    });

    it('should return a translated meeting pattern for a valid every week schedule', () => {
        const schedule: TutorialGroupSchedule = {
            dayOfWeek: 1,
            repetitionFrequency: 1,
            startTime: '14:00:00',
            endTime: '15:00:00',
        };
        translateService.instant.mockImplementation((key) => key);

        const result = pipe.transform(schedule, true);

        expect(result).toBe('artemisApp.generic.repetitions.everyWeek, artemisApp.generic.weekdays.monday, 14:00 - 15:00');
        expect(translateService.instant).toHaveBeenCalledTimes(2);
    });

    it('should return a translated meeting pattern for a valid every two week schedule', () => {
        const schedule: TutorialGroupSchedule = {
            dayOfWeek: 2,
            repetitionFrequency: 2,
            startTime: '14:00:00',
            endTime: '15:00:00',
        };
        translateService.instant.mockImplementation((key) => key);

        const result = pipe.transform(schedule, true);

        expect(result).toBe('artemisApp.generic.repetitions.everyNWeeks, artemisApp.generic.weekdays.tuesday, 14:00 - 15:00');
        expect(translateService.instant).toHaveBeenCalledTimes(2);
    });

    it('should return a translated meeting pattern without meeting frequency by default', () => {
        const schedule: TutorialGroupSchedule = {
            dayOfWeek: 2,
            repetitionFrequency: 2,
            startTime: '14:00:00',
            endTime: '15:00:00',
        };
        translateService.instant.mockImplementation((key) => key);

        const result = pipe.transform(schedule);

        expect(result).toBe('artemisApp.generic.weekdays.tuesday, 14:00 - 15:00');
        expect(translateService.instant).toHaveBeenCalledOnce();
    });
});
