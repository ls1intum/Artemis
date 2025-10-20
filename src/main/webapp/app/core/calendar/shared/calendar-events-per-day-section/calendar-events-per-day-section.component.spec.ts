import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { MockCalendarService } from 'test/helpers/mocks/service/mock-calendar.service';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover-component/calendar-event-detail-popover.component';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('CalendarEventsPerDaySectionComponent', () => {
    let component: CalendarEventsPerDaySectionComponent;
    let fixture: ComponentFixture<CalendarEventsPerDaySectionComponent>;

    let mockMap: Map<string, CalendarEvent[]>;

    const startOfMonday = dayjs('2025-05-05');
    const startOfTuesday = startOfMonday.add(1, 'day');
    const startOfWednesday = startOfTuesday.add(1, 'day');
    const week = Array.from({ length: 7 }, (_, i) => startOfMonday.add(i, 'day'));
    const events = [
        new CalendarEvent(CalendarEventType.Exam, 'Exam', startOfTuesday.add(12, 'hour'), startOfTuesday.add(13, 'hour'), undefined, 'Marlon Nienaber'),
        new CalendarEvent(CalendarEventType.Lecture, 'Object Design', startOfWednesday.add(10, 'hour'), startOfWednesday.add(12, 'hour'), undefined, undefined),
        new CalendarEvent(CalendarEventType.Tutorial, 'Tutorial', startOfWednesday.add(11, 'hour'), startOfWednesday.add(13, 'hour'), 'Zoom', 'Marlon Nienaber'),
        new CalendarEvent(CalendarEventType.TextExercise, 'Start: Your aspirations as a programmer', startOfWednesday.add(12, 'hour'), undefined, undefined),
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
            imports: [CalendarEventsPerDaySectionComponent, CalendarEventDetailPopoverComponent],
            declarations: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                {
                    provide: CalendarService,
                    useFactory: () => new MockCalendarService(mockMap),
                },
                provideNoopAnimations(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventsPerDaySectionComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('dates', week);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display events correctly', async () => {
        const examEventCell = fixture.debugElement.query(By.css('[data-testid="Exam"]'))?.nativeElement;
        const lectureEventCell = fixture.debugElement.query(By.css('[data-testid="Object Design"]'))?.nativeElement;
        const tutorialEventCell = fixture.debugElement.query(By.css('[data-testid="Tutorial"]'))?.nativeElement;
        const textExerciseEventCell = fixture.debugElement.query(By.css('[data-testid="Start: Your aspirations as a programmer"]'))?.nativeElement;

        expect(examEventCell).toBeTruthy();
        expect(lectureEventCell).toBeTruthy();
        expect(tutorialEventCell).toBeTruthy();
        expect(textExerciseEventCell).toBeTruthy();

        const getStyle = (element: HTMLElement) => {
            return {
                top: parseFloat(element.style.top),
                height: parseFloat(element.style.height),
                left: parseFloat(element.style.left),
                width: parseFloat(element.style.width),
            };
        };

        const examStyle = getStyle(examEventCell);
        const lectureStyle = getStyle(lectureEventCell);
        const tutorialStyle = getStyle(tutorialEventCell);
        const textExerciseStyle = getStyle(textExerciseEventCell);

        const pixelsPerRem = 16;
        const hourSegmentHeightInPixel = 3.5 * pixelsPerRem;
        const pixelsPerMinute = hourSegmentHeightInPixel / 60;

        expect(examStyle.top).toBeCloseTo(12 * 60 * pixelsPerMinute, 0);
        expect(lectureStyle.top).toBeCloseTo(10 * 60 * pixelsPerMinute, 0);
        expect(tutorialStyle.top).toBeCloseTo(11 * 60 * pixelsPerMinute, 0);
        expect(textExerciseStyle.top).toBeCloseTo(12 * 60 * pixelsPerMinute, 0);

        expect(examStyle.height).toBeCloseTo(60 * pixelsPerMinute, 0);
        expect(lectureStyle.height).toBeCloseTo(120 * pixelsPerMinute, 0);
        expect(tutorialStyle.height).toBeCloseTo(120 * pixelsPerMinute, 0);
        expect(textExerciseStyle.height).toBeCloseTo(24, 0);

        expect(examStyle.width).toBeCloseTo(97, 1);
        expect(lectureStyle.width).toBeCloseTo(31.33, 1);
        expect(tutorialStyle.width).toBeCloseTo(31.33, 1);
        expect(textExerciseStyle.width).toBeCloseTo(31.33, 1);

        expect(examStyle.left).toBeCloseTo(1.5, 1);
        expect(lectureStyle.left).toBeCloseTo(1.5, 1);
        expect(tutorialStyle.left).toBeCloseTo(34.33, 1);
        expect(textExerciseStyle.left).toBeCloseTo(67.17, 1);
    });

    it('should open popover', async () => {
        const examEventCell = fixture.debugElement.query(By.css('[data-testid="Exam"]'));
        expect(examEventCell).toBeTruthy();

        const popoverDebugElement = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        const popoverComponent = popoverDebugElement.componentInstance as CalendarEventDetailPopoverComponent;

        examEventCell.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(popoverComponent.isOpen()).toBeTrue();
    });

    it('should close popover', () => {
        const popoverDebugElement = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        const popoverComponent = popoverDebugElement.componentInstance as CalendarEventDetailPopoverComponent;
        const closeSpy = jest.spyOn(popoverComponent, 'close');

        const examEventCell = fixture.debugElement.query(By.css('[data-testid="Exam"]'));
        examEventCell.nativeElement.click();
        fixture.detectChanges();

        const infoColumn = fixture.debugElement.query(By.css('.info-column'));
        infoColumn.nativeElement.click();
        fixture.detectChanges();
        expect(popoverComponent.isOpen()).toBeFalse();

        examEventCell.nativeElement.click();
        fixture.detectChanges();

        const closeButton = document.querySelector('.close-button') as HTMLElement;
        expect(closeButton).toBeTruthy();
        closeButton.click();
        fixture.detectChanges();
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
