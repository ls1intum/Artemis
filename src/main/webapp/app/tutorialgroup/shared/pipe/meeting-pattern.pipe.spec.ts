import { TestBed } from '@angular/core/testing';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TranslateService } from '@ngx-translate/core';
import { MeetingPatternPipe } from 'app/tutorialgroup/shared/pipe/meeting-pattern.pipe';
import { RemoveSecondsPipe } from 'app/tutorialgroup/shared/pipe/remove-seconds.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('MeetingPatternPipe', () => {
    let pipe: MeetingPatternPipe;
    let translateService: TranslateService;
    let translateSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RemoveSecondsPipe],
            providers: [MeetingPatternPipe, { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                translateService = TestBed.inject(TranslateService);
                pipe = TestBed.inject(MeetingPatternPipe);
                translateSpy = jest.spyOn(translateService, 'instant');
            });
    });

    it('should return an empty string if schedule is undefined', () => {
        expect(pipe.transform(undefined)).toBe('');
    });

    it('should return an empty string', () => {
        const schedule = undefined;
        translateSpy.mockImplementation((key) => key);

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
        translateSpy.mockImplementation((key) => key);

        const result = pipe.transform(schedule, true);

        expect(result).toBe('artemisApp.generic.repetitions.everyWeek, global.weekdays.monday, 14:00 - 15:00');
        expect(translateSpy).toHaveBeenCalledTimes(2);
    });

    it('should return a translated meeting pattern for a valid every two week schedule', () => {
        const schedule: TutorialGroupSchedule = {
            dayOfWeek: 2,
            repetitionFrequency: 2,
            startTime: '14:00:00',
            endTime: '15:00:00',
        };
        translateSpy.mockImplementation((key) => key);

        const result = pipe.transform(schedule, true);

        expect(result).toBe('artemisApp.generic.repetitions.everyNWeeks, global.weekdays.tuesday, 14:00 - 15:00');
        expect(translateSpy).toHaveBeenCalledTimes(2);
    });

    it('should return a translated meeting pattern without meeting frequency by default', () => {
        const schedule: TutorialGroupSchedule = {
            dayOfWeek: 2,
            repetitionFrequency: 2,
            startTime: '14:00:00',
            endTime: '15:00:00',
        };
        translateSpy.mockImplementation((key) => key);

        const result = pipe.transform(schedule);

        expect(result).toBe('global.weekdays.tuesday, 14:00 - 15:00');
        expect(translateSpy).toHaveBeenCalledOnce();
    });
});
