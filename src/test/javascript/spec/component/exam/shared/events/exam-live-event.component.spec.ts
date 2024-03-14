import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import { ExamLiveEvent, ExamLiveEventType, ExamWideAnnouncementEvent, WorkingTimeUpdateEvent } from 'app/exam/participate/exam-participation-live-events.service';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { WorkingTimeChangeComponent } from 'app/exam/shared/working-time-change/working-time-change.component';

describe('ExamLiveEventComponent', () => {
    let component: ExamLiveEventComponent;
    let fixture: ComponentFixture<ExamLiveEventComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExamLiveEventComponent, WorkingTimeChangeComponent, ArtemisTranslatePipe, ArtemisDatePipe, HtmlForMarkdownPipe, ArtemisDurationFromSecondsPipe],
            imports: [FontAwesomeModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveEventComponent);
        component = fixture.componentInstance;
    });

    it('should display the correct event type and author', () => {
        component.event = {
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            createdBy: 'John Doe',
        } as ExamLiveEvent;

        fixture.detectChanges();

        const typeElement = fixture.debugElement.query(By.css('.type')).nativeElement;
        const authorElement = fixture.debugElement.query(By.css('.author > span:last-child')).nativeElement;

        expect(typeElement.textContent).toContain('artemisApp.exam.events.type.examWideAnnouncement');
        expect(authorElement.textContent).toBe('John Doe');
    });

    it('should display the attendance check event', () => {
        component.event = {
            eventType: ExamLiveEventType.EXAM_ATTENDANCE_CHECK,
        } as ExamLiveEvent;

        fixture.detectChanges();

        const typeElement = fixture.debugElement.query(By.css('.type')).nativeElement;
        expect(typeElement.textContent).toContain('artemisApp.exam.events.type.examAttendanceCheck');
    });

    it('should display exam-wide announcement text when event is of type EXAM_WIDE_ANNOUNCEMENT', () => {
        component.event = {
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            text: 'This is an announcement',
        } as ExamWideAnnouncementEvent;

        fixture.detectChanges();

        const contentElement = fixture.debugElement.query(By.css('.content > div')).nativeElement;
        expect(contentElement.innerHTML).toContain('This is an announcement');
    });

    it('should display working time update when event is of type WORKING_TIME_UPDATE', () => {
        component.event = {
            eventType: ExamLiveEventType.WORKING_TIME_UPDATE,
            oldWorkingTime: 300,
            newWorkingTime: 600,
            courseWide: true,
        } as WorkingTimeUpdateEvent;

        fixture.detectChanges();

        const previousTimeElement = fixture.debugElement.query(By.css('[data-testid="old-time"]')).nativeElement;
        const newTimeElement = fixture.debugElement.query(By.css('[data-testid="new-time"]')).nativeElement;
        const titleElement = fixture.debugElement.query(By.css('.wt-title')).nativeElement;

        expect(previousTimeElement.textContent).toContain('5min');
        expect(newTimeElement.textContent).toContain('10min');
        expect(titleElement.getAttribute('jhiTranslate')).toBe('artemisApp.exam.events.messages.workingTimeUpdate.titleEveryone');
    });

    it('should emit event when acknowledge button is clicked', () => {
        const mockEvent: ExamLiveEvent = {
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            createdBy: 'John Doe',
        } as any as ExamLiveEvent;
        component.event = mockEvent;
        component.showAcknowledge = true;

        fixture.detectChanges();

        const acknowledgeSpy = jest.spyOn(component.onAcknowledge, 'emit');
        const button = fixture.debugElement.query(By.css('button'));
        button.nativeElement.click();

        expect(acknowledgeSpy).toHaveBeenCalledWith(mockEvent);
    });
});
