import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import {
    ExamLiveEvent,
    ExamLiveEventType,
    ExamWideAnnouncementEvent,
    ProblemStatementUpdateEvent,
    WorkingTimeUpdateEvent,
} from 'app/exam/participate/exam-participation-live-events.service';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { input } from '@angular/core';

describe('ExamLiveEventComponent', () => {
    let component: ExamLiveEventComponent;
    let fixture: ComponentFixture<ExamLiveEventComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveEventComponent);
        component = fixture.componentInstance;
    });

    it('should display the correct event type and author', () => {
        const event = {
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
        } as ExamLiveEvent;

        TestBed.runInInjectionContext(() => {
            component.event = input(event);
        });

        fixture.detectChanges();

        const typeElement = fixture.debugElement.query(By.css('.type')).nativeElement;

        expect(typeElement.textContent).toContain('artemisApp.exam.events.type.examWideAnnouncement');
    });

    it('should display the attendance check event', () => {
        const event = {
            eventType: ExamLiveEventType.EXAM_ATTENDANCE_CHECK,
        } as ExamLiveEvent;

        TestBed.runInInjectionContext(() => {
            component.event = input(event);
        });

        fixture.detectChanges();

        const typeElement = fixture.debugElement.query(By.css('.type')).nativeElement;
        expect(typeElement.textContent).toContain('artemisApp.exam.events.type.examAttendanceCheck');
    });

    it('should display exam-wide announcement text when event is of type EXAM_WIDE_ANNOUNCEMENT', () => {
        const event = {
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            text: 'This is an announcement',
        } as ExamWideAnnouncementEvent;

        TestBed.runInInjectionContext(() => {
            component.event = input(event);
        });

        fixture.detectChanges();

        const contentElement = fixture.debugElement.query(By.css('.content > div')).nativeElement;
        expect(contentElement.innerHTML).toContain('This is an announcement');
    });

    it('should display working time update when event is of type WORKING_TIME_UPDATE', () => {
        const event = {
            eventType: ExamLiveEventType.WORKING_TIME_UPDATE,
            oldWorkingTime: 300,
            newWorkingTime: 600,
            courseWide: true,
        } as WorkingTimeUpdateEvent;

        TestBed.runInInjectionContext(() => {
            component.event = input(event);
        });

        fixture.detectChanges();

        const previousTimeElement = fixture.debugElement.query(By.css('[data-testid="old-time"]')).nativeElement;
        const newTimeElement = fixture.debugElement.query(By.css('[data-testid="new-time"]')).nativeElement;
        const titleElement = fixture.debugElement.query(By.css('.wt-title')).nativeElement;

        expect(previousTimeElement.textContent).toContain('5min');
        expect(newTimeElement.textContent).toContain('10min');
        expect(titleElement.getAttribute('jhiTranslate')).toBe('artemisApp.exam.events.messages.workingTimeUpdate.titleEveryone');
    });

    it('should display problem statement update when event is of type PROBLEM_STATEMENT_UPDATE', () => {
        const event = {
            eventType: ExamLiveEventType.PROBLEM_STATEMENT_UPDATE,
            text: 'Dear students, the problem statement of the exercise was changed',
            problemStatement: 'New problem statement',
            exerciseId: 1,
            exerciseName: 'Programming Exercise',
        } as ProblemStatementUpdateEvent;

        TestBed.runInInjectionContext(() => {
            component.event = input(event);
        });

        fixture.detectChanges();

        const typeElement = fixture.debugElement.query(By.css('.type')).nativeElement;
        const contentElement = fixture.debugElement.query(By.css('.content > div')).nativeElement;

        expect(typeElement.textContent).toContain('artemisApp.exam.events.type.problemStatementUpdate');
        expect(contentElement.innerHTML).toContain('Dear students, the problem statement of the exercise was changed');
        expect(contentElement.innerHTML).toContain('artemisApp.exam.events.messages.problemStatementUpdate.description');
    });

    it('should emit event when acknowledge button is clicked', () => {
        const mockEvent: ExamLiveEvent = {
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
        } as any as ExamLiveEvent;

        TestBed.runInInjectionContext(() => {
            component.event = input(mockEvent);
            component.showAcknowledge = input(true);
        });

        fixture.detectChanges();

        const acknowledgeSpy = jest.spyOn(component.onAcknowledge, 'emit');
        const button = fixture.debugElement.query(By.css('button'));
        button.nativeElement.click();

        expect(acknowledgeSpy).toHaveBeenCalledWith(mockEvent);
    });

    it('should emit event when navigate to exercise button is clicked', () => {
        const mockEvent: ExamLiveEvent = {
            eventType: ExamLiveEventType.PROBLEM_STATEMENT_UPDATE,
        } as any as ExamLiveEvent;

        TestBed.runInInjectionContext(() => {
            component.event = input(mockEvent);
            component.showAcknowledge = input(true);
        });

        fixture.detectChanges();

        const acknowledgeSpy = jest.spyOn(component.onNavigate, 'emit');
        const buttons = fixture.debugElement.queryAll(By.css('button'));
        expect(buttons).toHaveLength(2);
        // Navigate to exercise is the second button
        buttons[1].nativeElement.click();

        expect(acknowledgeSpy).toHaveBeenCalledWith(mockEvent);
    });
});
