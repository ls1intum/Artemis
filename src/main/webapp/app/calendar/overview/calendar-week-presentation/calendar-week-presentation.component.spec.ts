import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarWeekPresentationComponent } from './calendar-week-presentation.component';
import { CalendarEventDetailPopoverComponent } from 'app/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';
import { CalendarDayBadgeComponent } from 'app/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarEventService } from 'app/calendar/shared/service/calendar-event.service';
import { MockCalendarEventService } from 'test/helpers/mocks/service/mock-calendar-event.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

describe('CalendarDesktopWeekComponent', () => {
    let component: CalendarWeekPresentationComponent;
    let fixture: ComponentFixture<CalendarWeekPresentationComponent>;
    const firstDayOfTestWeek = dayjs('2025-05-05');

    let mockMap: Map<string, CalendarEvent[]>;

    beforeAll(() => {
        const startOfMonday = firstDayOfTestWeek;
        const startOfTuesday = startOfMonday.add(1, 'day');
        const startOfWednesday = startOfTuesday.add(1, 'day');
        mockMap = new Map();

        const events = [
            new CalendarEvent('exam-3-startAndEndDate', 'Exam', startOfTuesday.add(12, 'hour'), startOfTuesday.add(13, 'hour'), undefined, 'Marlon Nienaber'),
            new CalendarEvent('lecture-1-startAndEndDate', 'Object Design', startOfWednesday.add(10, 'hour'), startOfWednesday.add(12, 'hour'), undefined, undefined),
            new CalendarEvent('tutorial-1', 'Tutorial', startOfWednesday.add(11, 'hour'), startOfWednesday.add(13, 'hour'), 'Zoom', 'Marlon Nienaber'),
            new CalendarEvent('textExercise-31-startDate', 'Your aspirations as a programmer', startOfWednesday.add(12, 'hour'), undefined, undefined),
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
            imports: [CalendarWeekPresentationComponent, CalendarEventDetailPopoverComponent],
            declarations: [MockComponent(CalendarDayBadgeComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                {
                    provide: CalendarEventService,
                    useFactory: () => new MockCalendarEventService(mockMap),
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarWeekPresentationComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('firstDayOfCurrentWeek', firstDayOfTestWeek);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should compute correct number of days', () => {
        const weekDays = component.weekDays();
        expect(weekDays).toHaveLength(7);

        const start = firstDayOfTestWeek;
        for (let i = 0; i < 7; i++) {
            expect(weekDays[i].isSame(start.add(i, 'day'), 'day')).toBeTrue();
        }
    });

    it('should display correct events', async () => {
        const examEventEl = fixture.debugElement.query(By.css('[data-testid="exam-3-startAndEndDate"]'))?.nativeElement;
        const lectureEventEl = fixture.debugElement.query(By.css('[data-testid="lecture-1-startAndEndDate"]'))?.nativeElement;
        const tutorialEventEl = fixture.debugElement.query(By.css('[data-testid="tutorial-1"]'))?.nativeElement;
        const textExerciseEventEl = fixture.debugElement.query(By.css('[data-testid="textExercise-31-startDate"]'))?.nativeElement;

        expect(examEventEl).toBeTruthy();
        expect(lectureEventEl).toBeTruthy();
        expect(tutorialEventEl).toBeTruthy();
        expect(textExerciseEventEl).toBeTruthy();

        const getStyle = (el: HTMLElement) => {
            return {
                top: parseFloat(el.style.top),
                height: parseFloat(el.style.height),
                left: parseFloat(el.style.left),
                width: parseFloat(el.style.width),
            };
        };

        const examStyle = getStyle(examEventEl);
        const lectureStyle = getStyle(lectureEventEl);
        const tutorialStyle = getStyle(tutorialEventEl);
        const textExerciseStyle = getStyle(textExerciseEventEl);

        const pixelsPerMinute = (16 * 3.5) / 60;

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

    it('should open popover', () => {
        const examEventCell = fixture.debugElement.query(By.css('[data-testid="exam-3-startAndEndDate"]'));
        expect(examEventCell).toBeTruthy();

        examEventCell.nativeElement.click();
        fixture.detectChanges();

        const popover = document.querySelector('jhi-calendar-event-detail-popover');
        expect(popover).toBeTruthy();
    });

    it('should close popover only when close button used', () => {
        const examEventCell = fixture.debugElement.query(By.css('[data-testid="exam-3-startAndEndDate"]'));
        examEventCell.nativeElement.click();
        fixture.detectChanges();

        let popover = document.querySelector('jhi-calendar-event-detail-popover');
        expect(popover).toBeTruthy();

        const scrollContainer = fixture.debugElement.query(By.css('.scroll-container'));
        scrollContainer.nativeElement.click();
        fixture.detectChanges();

        popover = document.querySelector('jhi-calendar-event-detail-popover');
        expect(popover).toBeTruthy();

        const closeButton = popover!.querySelector('.close-button') as HTMLElement;
        expect(closeButton).toBeTruthy();

        closeButton.click();
        fixture.detectChanges();

        popover = document.querySelector('jhi-calendar-event-detail-popover');
        expect(popover).toBeNull();
    });

    it('should replace popover when other event selected', () => {
        const examEventCell = fixture.debugElement.query(By.css('[data-testid="exam-3-startAndEndDate"]'));
        const lectureEventCell = fixture.debugElement.query(By.css('[data-testid="lecture-1-startAndEndDate"]'));

        examEventCell.nativeElement.click();
        fixture.detectChanges();

        let popover = document.querySelector('jhi-calendar-event-detail-popover');
        expect(popover).toBeTruthy();
        expect(component.selectedEvent()?.id).toBe('exam-3-startAndEndDate');

        lectureEventCell.nativeElement.click();
        fixture.detectChanges();

        popover = document.querySelector('jhi-calendar-event-detail-popover');
        expect(popover).toBeTruthy();
        expect(component.selectedEvent()?.id).toBe('lecture-1-startAndEndDate');
    });
});
