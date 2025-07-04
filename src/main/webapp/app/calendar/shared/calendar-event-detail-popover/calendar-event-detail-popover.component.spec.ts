import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarEventDetailPopoverComponent } from './calendar-event-detail-popover.component';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CalendarEventDetailPopoverComponent', () => {
    let fixture: ComponentFixture<CalendarEventDetailPopoverComponent>;
    let component: CalendarEventDetailPopoverComponent;

    const baseEvent: CalendarEvent = {
        id: 'event1_dueDate',
        title: 'Lecture 1',
        startDate: dayjs('2025-07-05T10:00:00'),
        endDate: dayjs('2025-07-05T12:00:00'),
        location: 'Room 42',
        facilitator: 'Dr. Smith',
        isLectureEvent: () => true,
        isExamEvent: () => false,
        isTutorialEvent: () => false,
        isExerciseEvent: () => false,
        isProgrammingExercise: () => false,
        isTextExerciseEvent: () => false,
        isModelingExerciseEvent: () => false,
        isQuizExerciseEvent: () => false,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarEventDetailPopoverComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventDetailPopoverComponent);
        component = fixture.componentInstance;
    });

    function setEventInput(event: CalendarEvent) {
        fixture.componentRef.setInput('event', event);
        fixture.detectChanges();
    }

    it('should render start-and-end-row if endDate is provided', () => {
        setEventInput(baseEvent);
        const row = fixture.debugElement.query(By.css('#start-and-end-row'));
        expect(row).toBeTruthy();
        expect(fixture.debugElement.query(By.css('#start-row'))).toBeFalsy();
    });

    it('should render only start-row if endDate is missing', () => {
        const event = { ...baseEvent, endDate: undefined };
        fixture.componentRef.setInput('event', event);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#start-row'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('#start-and-end-row'))).toBeFalsy();
    });

    it('should render location-row if location is present', () => {
        setEventInput(baseEvent);
        expect(fixture.debugElement.query(By.css('#location-row'))).toBeTruthy();
    });

    it('should not render location-row if location is missing', () => {
        const event = { ...baseEvent, location: undefined };
        fixture.componentRef.setInput('event', event);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#location-row'))).toBeFalsy();
    });

    it('should render facilitator-row if facilitator is present', () => {
        setEventInput(baseEvent);
        expect(fixture.debugElement.query(By.css('#facilitator-row'))).toBeTruthy();
    });

    it('should not render facilitator-row if facilitator is missing', () => {
        const event = { ...baseEvent, facilitator: undefined };
        fixture.componentRef.setInput('event', event);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#facilitator-row'))).toBeFalsy();
    });

    it('should emit onClosePopover when close button is clicked', () => {
        const emitSpy = jest.spyOn(component.onClosePopover, 'emit');
        setEventInput(baseEvent);

        const closeButton = fixture.debugElement.query(By.css('.close-button')).nativeElement;
        closeButton.click();

        expect(emitSpy).toHaveBeenCalledOnce();
    });
});
