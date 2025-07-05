import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockCalendarEventService } from 'test/helpers/mocks/service/mock-calendar-event.service';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarMonthPresentationComponent } from './calendar-month-presentation.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';

describe('CalendarDesktopMonthComponent', () => {
    let fixture: ComponentFixture<CalendarMonthPresentationComponent>;
    let component: CalendarMonthPresentationComponent;
    let mockMap: Map<string, CalendarEvent[]>;

    beforeAll(() => {
        const referenceDate = dayjs('2025-05-15 10:30');
        mockMap = new Map();

        const events = [
            new CalendarEvent('exam-3-startAndEndDate', 'Exam', referenceDate, referenceDate.add(1, 'hour'), undefined, 'Marlon Nienaber'),
            new CalendarEvent('lecture-1-startAndEndDate', 'Object Design', referenceDate.subtract(4, 'hour'), referenceDate.subtract(2, 'hour'), undefined, undefined),
            new CalendarEvent('lecture-2-startAndEndDate', 'Object Design 2', referenceDate.subtract(2), referenceDate, undefined, undefined),
            new CalendarEvent('lecture-3-startAndEndDate', 'Object Design 3', referenceDate, referenceDate.add(2, 'hour'), undefined, undefined),
            new CalendarEvent('tutorial-1', 'Tutorial', referenceDate.add(1, 'day'), referenceDate.add(1, 'day').add(1, 'hour'), 'Zoom', 'Marlon Nienaber'),
            new CalendarEvent('tutorial-2', 'Tutorial', referenceDate.add(1, 'day').add(2, 'hour'), referenceDate.add(1, 'day').add(3, 'hour'), 'Zoom', 'Marlon Nienaber'),
            new CalendarEvent('tutorial-3', 'Tutorial', referenceDate.add(1, 'day').add(3, 'hour'), referenceDate.add(1, 'day').add(4, 'hour'), 'Zoom', 'Marlon Nienaber'),
            new CalendarEvent('textExercise-31-startDate', 'Your aspirations as a programmer', referenceDate.add(2, 'day'), undefined, undefined, undefined),
        ];

        for (const event of events) {
            const key = event.startDate.format('YYYY-MM-DD');
            const current = mockMap.get(key) ?? [];
            current.push(event);
            mockMap.set(key, current);
        }
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarMonthPresentationComponent, CalendarEventDetailPopoverComponent],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(CalendarDayBadgeComponent), MockDirective(TranslateDirective)],
            providers: [
                {
                    provide: CalendarEventService,
                    useFactory: () => new MockCalendarEventService(mockMap),
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarMonthPresentationComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('firstDayOfCurrentMonth', dayjs('2025-05-01'));
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should compute correct number of weeks and days', () => {
        const weeks = component.weeks();
        expect(weeks).toHaveLength(5);
        expect(weeks.every((w) => w.length === 7)).toBeTrue();
    });

    it('should display correct events', async () => {
        const eventCells = fixture.debugElement.queryAll(By.css('.event-cell'));
        expect(eventCells).toHaveLength(6);

        const examEventCell = fixture.debugElement.query(By.css('[data-testid="exam-3-startAndEndDate"]'));
        const lecture1EventCell = fixture.debugElement.query(By.css('[data-testid="lecture-1-startAndEndDate"]'));
        const lecture2EventCell = fixture.debugElement.query(By.css('[data-testid="lecture-2-startAndEndDate"]'));
        const lecture3EventCell = fixture.debugElement.query(By.css('[data-testid="lecture-3-startAndEndDate"]'));
        const tutorial1EventCell = fixture.debugElement.query(By.css('[data-testid="tutorial-1"]'));
        const tutorial2EventCell = fixture.debugElement.query(By.css('[data-testid="tutorial-1"]'));
        const tutorial3EventCell = fixture.debugElement.query(By.css('[data-testid="tutorial-1"]'));
        const textExerciseEventCell = fixture.debugElement.query(By.css('[data-testid="textExercise-31-startDate"]'));

        expect(examEventCell).toBeTruthy();
        expect(lecture1EventCell).toBeTruthy();
        expect(tutorial1EventCell).toBeTruthy();
        expect(tutorial2EventCell).toBeTruthy();
        expect(tutorial3EventCell).toBeTruthy();
        expect(textExerciseEventCell).toBeTruthy();

        expect(lecture2EventCell).toBeFalsy();
        expect(lecture3EventCell).toBeFalsy();
    });

    it('should open popover', () => {
        const eventCell = fixture.debugElement.query(By.css('[data-testid="exam-3-startAndEndDate"]'));
        expect(eventCell).toBeTruthy();

        eventCell.nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedEvent()?.id).toBe('exam-3-startAndEndDate');

        const popoverContent = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        expect(popoverContent).toBeTruthy();
    });

    it('should close popover only when close button used', () => {
        const examEventCell = fixture.debugElement.query(By.css('[data-testid="exam-3-startAndEndDate"]'));
        expect(examEventCell).toBeTruthy();

        examEventCell.nativeElement.click();
        fixture.detectChanges();

        let popoverComponent = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        expect(component.selectedEvent()?.id).toBe('exam-3-startAndEndDate');
        expect(popoverComponent).toBeTruthy();

        const emptyDayCell = fixture.debugElement.queryAll(By.css('.day-cell')).find((cell) => cell.queryAll(By.css('.event-cell')).length === 0);
        expect(emptyDayCell).toBeTruthy();

        emptyDayCell!.nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedEvent()?.id).toBe('exam-3-startAndEndDate');
        popoverComponent = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        expect(popoverComponent).toBeTruthy();

        const closeButton = popoverComponent.query(By.css('.close-button'));
        expect(closeButton).toBeTruthy();

        closeButton.nativeElement.click();
        fixture.detectChanges();

        expect(component.selectedEvent()).toBeUndefined();
        popoverComponent = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        expect(popoverComponent).toBeFalsy();
    });

    it('should replace popover when other event selected', () => {
        const firstEventCell = fixture.debugElement.query(By.css('[data-testid="exam-3-startAndEndDate"]'));
        firstEventCell.nativeElement.click();
        fixture.detectChanges();

        let popoverComponent = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        expect(component.selectedEvent()?.id).toBe('exam-3-startAndEndDate');
        expect(popoverComponent).toBeTruthy();

        const secondEventCell = fixture.debugElement.query(By.css('[data-testid="lecture-1-startAndEndDate"]'));
        secondEventCell.nativeElement.click();
        fixture.detectChanges();

        popoverComponent = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        expect(popoverComponent).toBeTruthy();
        expect(component.selectedEvent()?.id).toBe('lecture-1-startAndEndDate');
    });
});
