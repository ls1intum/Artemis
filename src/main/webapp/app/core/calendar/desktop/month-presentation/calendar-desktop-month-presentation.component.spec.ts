import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockCalendarService } from 'test/helpers/mocks/service/mock-calendar.service';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarDesktopMonthPresentationComponent } from './calendar-desktop-month-presentation.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover-component/calendar-event-detail-popover.component';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('CalendarDesktopMonthPresentationComponent', () => {
    let fixture: ComponentFixture<CalendarDesktopMonthPresentationComponent>;
    let component: CalendarDesktopMonthPresentationComponent;
    let mockMap: Map<string, CalendarEvent[]>;

    const referenceDate = dayjs('2025-05-15 10:30');
    const events = [
        new CalendarEvent(CalendarEventType.Exam, 'Exam', referenceDate, referenceDate.add(1, 'hour'), undefined, 'Marlon Nienaber'),
        new CalendarEvent(CalendarEventType.Lecture, 'Object Design', referenceDate.subtract(4, 'hour'), referenceDate.subtract(2, 'hour'), undefined, undefined),
        new CalendarEvent(CalendarEventType.Lecture, 'Object Design 2', referenceDate.subtract(2), referenceDate, undefined, undefined),
        new CalendarEvent(CalendarEventType.Lecture, 'Object Design 3', referenceDate, referenceDate.add(2, 'hour'), undefined, undefined),
        new CalendarEvent(CalendarEventType.Tutorial, 'Tutorial 1', referenceDate.add(1, 'day'), referenceDate.add(1, 'day').add(1, 'hour'), 'Zoom', 'Marlon Nienaber'),
        new CalendarEvent(
            CalendarEventType.Tutorial,
            'Tutorial 2',
            referenceDate.add(1, 'day').add(2, 'hour'),
            referenceDate.add(1, 'day').add(3, 'hour'),
            'Zoom',
            'Marlon Nienaber',
        ),
        new CalendarEvent(
            CalendarEventType.Tutorial,
            'Tutorial 3',
            referenceDate.add(1, 'day').add(3, 'hour'),
            referenceDate.add(1, 'day').add(4, 'hour'),
            'Zoom',
            'Marlon Nienaber',
        ),
        new CalendarEvent(CalendarEventType.TextExercise, 'Start: Your aspirations as a programmer', referenceDate.add(2, 'day'), undefined, undefined, undefined),
    ];

    beforeAll(() => {
        mockMap = new Map();
        for (const event of events) {
            const key = event.startDate.format('YYYY-MM-DD');
            const current = mockMap.get(key) ?? [];
            current.push(event);
            mockMap.set(key, current);
        }
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarDesktopMonthPresentationComponent, CalendarEventDetailPopoverComponent],
            declarations: [MockPipe(ArtemisTranslatePipe), MockComponent(CalendarDayBadgeComponent), MockDirective(TranslateDirective)],
            providers: [
                {
                    provide: CalendarService,
                    useFactory: () => new MockCalendarService(mockMap),
                },
                provideNoopAnimations(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarDesktopMonthPresentationComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('firstDateOfCurrentMonth', dayjs('2025-05-01'));
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should compute correct number of weeks and days', () => {
        const weeks = component.weeks();
        expect(weeks).toHaveLength(5);
        expect(weeks.every((week) => week.days.length === 7)).toBeTrue();
    });

    it('should display correct events', async () => {
        const eventCells = fixture.debugElement.queryAll(By.css('.event-cell'));
        expect(eventCells).toHaveLength(6);

        const examEventCell = fixture.debugElement.query(By.css('[data-testid="Exam"]'));
        const lecture1EventCell = fixture.debugElement.query(By.css('[data-testid="Object Design"]'));
        const lecture2EventCell = fixture.debugElement.query(By.css('[data-testid="Object Design 2"]'));
        const lecture3EventCell = fixture.debugElement.query(By.css('[data-testid="Object Design 3"]'));
        const tutorial1EventCell = fixture.debugElement.query(By.css('[data-testid="Tutorial 1"]'));
        const tutorial2EventCell = fixture.debugElement.query(By.css('[data-testid="Tutorial 2"]'));
        const tutorial3EventCell = fixture.debugElement.query(By.css('[data-testid="Tutorial 3"]'));
        const textExerciseEventCell = fixture.debugElement.query(By.css('[data-testid="Start: Your aspirations as a programmer"]'));

        expect(examEventCell).toBeTruthy();
        expect(lecture1EventCell).toBeTruthy();
        expect(tutorial1EventCell).toBeTruthy();
        expect(tutorial2EventCell).toBeTruthy();
        expect(tutorial3EventCell).toBeTruthy();
        expect(textExerciseEventCell).toBeTruthy();

        expect(lecture2EventCell).toBeFalsy();
        expect(lecture3EventCell).toBeFalsy();
    });

    it('should open popover', async () => {
        const popoverDebugElement = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        const popoverComponent = popoverDebugElement.componentInstance as CalendarEventDetailPopoverComponent;

        const eventCell = fixture.debugElement.query(By.css('[data-testid="Exam"]'));
        eventCell.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(popoverComponent.isOpen()).toBeTrue();
    });

    it('should close popover only when close button used', async () => {
        const popoverDebugElement = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        const popoverComponent = popoverDebugElement.componentInstance as CalendarEventDetailPopoverComponent;
        const closeSpy = jest.spyOn(popoverComponent, 'close');

        const examEventCell = fixture.debugElement.query(By.css('[data-testid="Exam"]'));
        expect(examEventCell).toBeTruthy();
        examEventCell.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(popoverComponent.isOpen()).toBeTrue();

        const emptyDayCell = fixture.debugElement.queryAll(By.css('.day-cell')).find((cell) => cell.queryAll(By.css('.event-cell')).length === 0);
        expect(emptyDayCell).toBeTruthy();
        emptyDayCell!.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(popoverComponent.isOpen()).toBeFalse();

        examEventCell.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(popoverComponent.isOpen()).toBeTrue();

        const closeButton = document.querySelector('.close-button') as HTMLElement;
        expect(closeButton).toBeTruthy();
        closeButton.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
