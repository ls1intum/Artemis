import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarEvent, CalendarEventSubtype, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventDetailPopoverComponent } from './calendar-event-detail-popover.component';

describe('CalendarEventDetailPopoverComponent', () => {
    let fixture: ComponentFixture<CalendarEventDetailPopoverComponent>;
    let component: CalendarEventDetailPopoverComponent;

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
        const event = new CalendarEvent(
            CalendarEventType.Lecture,
            CalendarEventSubtype.StartAndEndDate,
            'Lecture 1',
            dayjs('2025-07-05T10:00:00'),
            dayjs('2025-07-05T12:00:00'),
            'Room 42',
            'Dr. Smith',
        );

        setEventInput(event);

        expect(fixture.debugElement.query(By.css('#start-and-end-row'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('#start-row'))).toBeFalsy();
    });

    it('should render only start-row if endDate is missing', () => {
        const event = new CalendarEvent(CalendarEventType.Lecture, CalendarEventSubtype.StartDate, 'Lecture 1', dayjs('2025-07-05T10:00:00'), undefined, 'Room 42', 'Dr. Smith');

        setEventInput(event);

        expect(fixture.debugElement.query(By.css('#start-row'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('#start-and-end-row'))).toBeFalsy();
    });

    it('should render location-row if location is present', () => {
        const event = new CalendarEvent(CalendarEventType.Lecture, CalendarEventSubtype.StartAndEndDate, 'Lecture 2', dayjs(), dayjs(), 'Main Hall', 'Dr. Jane');

        setEventInput(event);

        expect(fixture.debugElement.query(By.css('#location-row'))).toBeTruthy();
    });

    it('should not render location-row if location is missing', () => {
        const event = new CalendarEvent(CalendarEventType.Lecture, CalendarEventSubtype.StartAndEndDate, 'Lecture 2', dayjs(), dayjs(), undefined, 'Dr. Jane');

        setEventInput(event);

        expect(fixture.debugElement.query(By.css('#location-row'))).toBeFalsy();
    });

    it('should render facilitator-row if facilitator is present', () => {
        const event = new CalendarEvent(CalendarEventType.Tutorial, CalendarEventSubtype.StartAndEndDate, 'Tutorial 1', dayjs(), dayjs(), 'Lab 1', 'John Doe');

        setEventInput(event);

        expect(fixture.debugElement.query(By.css('#facilitator-row'))).toBeTruthy();
    });

    it('should not render facilitator-row if facilitator is missing', () => {
        const event = new CalendarEvent(CalendarEventType.Tutorial, CalendarEventSubtype.StartAndEndDate, 'Tutorial 2', dayjs(), dayjs(), 'Lab 2', undefined);

        setEventInput(event);

        expect(fixture.debugElement.query(By.css('#facilitator-row'))).toBeFalsy();
    });

    it('should emit onClosePopover when close button is clicked', () => {
        const event = new CalendarEvent(CalendarEventType.Exam, CalendarEventSubtype.StartAndEndDate, 'Exam 1', dayjs(), dayjs(), 'Auditorium', 'Prof. Exam');

        setEventInput(event);

        const emitSpy = jest.spyOn(component.onClosePopover, 'emit');

        const closeButton = fixture.debugElement.query(By.css('.close-button')).nativeElement;
        closeButton.click();

        expect(emitSpy).toHaveBeenCalledOnce();
    });
});
