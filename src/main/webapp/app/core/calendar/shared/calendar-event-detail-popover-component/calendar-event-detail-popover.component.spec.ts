import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventDetailPopoverComponent } from './calendar-event-detail-popover.component';
import { provideTestAnimations } from 'test/helpers/provide-test-animations';

describe('CalendarEventDetailPopoverComponent', () => {
    let fixture: ComponentFixture<CalendarEventDetailPopoverComponent>;
    let component: CalendarEventDetailPopoverComponent;
    let fakeMouseEvent: MouseEvent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarEventDetailPopoverComponent, MockDirective(TranslateDirective)],
            providers: [provideTestAnimations()],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventDetailPopoverComponent);
        component = fixture.componentInstance;
        const anchorElement = document.createElement('div');
        fakeMouseEvent = {
            currentTarget: anchorElement,
            stopPropagation: jest.fn(),
        } as unknown as MouseEvent;
    });

    it('should render start-and-end-row if endDate is provided', async () => {
        const event = new CalendarEvent(CalendarEventType.Lecture, 'Lecture 1', dayjs('2025-07-05T10:00:00'), dayjs('2025-07-05T12:00:00'), 'Room 42', 'Dr. Smith');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.query(By.css('#start-and-end-row'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('#start-row'))).toBeFalsy();
    });

    it('should render only start-row if endDate is missing', async () => {
        const event = new CalendarEvent(CalendarEventType.Lecture, 'Start: Lecture 1', dayjs('2025-07-05T10:00:00'), undefined, 'Room 42', 'Dr. Smith');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.query(By.css('#start-row'))).toBeTruthy();
        expect(fixture.debugElement.query(By.css('#start-and-end-row'))).toBeFalsy();
    });

    it('should render location-row if location is present', async () => {
        const event = new CalendarEvent(CalendarEventType.Lecture, 'Lecture 2', dayjs(), dayjs(), 'Main Hall', 'Dr. Jane');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.query(By.css('#location-row'))).toBeTruthy();
    });

    it('should not render location-row if location is missing', async () => {
        const event = new CalendarEvent(CalendarEventType.Lecture, 'Lecture 2', dayjs(), dayjs(), undefined, 'Dr. Jane');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.query(By.css('#location-row'))).toBeFalsy();
    });

    it('should render facilitator-row if facilitator is present', async () => {
        const event = new CalendarEvent(CalendarEventType.Tutorial, 'Tutorial 1', dayjs(), dayjs(), 'Lab 1', 'John Doe');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.query(By.css('#facilitator-row'))).toBeTruthy();
    });

    it('should not render facilitator-row if facilitator is missing', async () => {
        const event = new CalendarEvent(CalendarEventType.Tutorial, 'Tutorial 2', dayjs(), dayjs(), 'Lab 2', undefined);

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.debugElement.query(By.css('#facilitator-row'))).toBeFalsy();
    });
});
