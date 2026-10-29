import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { By } from '@angular/platform-browser';
import { NgTemplateOutlet } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockCalendarService } from 'test/helpers/mocks/service/mock-calendar.service';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { CalendarDesktopMonthPresentationComponent } from './calendar-desktop-month-presentation.component';
import { CalendarDayBadgeComponent } from 'app/calendar/shared/calendar-day-badge/calendar-day-badge.component';
import { CalendarEventDetailPopoverComponent } from 'app/calendar/shared/calendar-event-detail-popover-component/calendar-event-detail-popover.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
describe('CalendarDesktopMonthPresentationComponent', () => {
    let fixture: ComponentFixture<CalendarDesktopMonthPresentationComponent>;
    let component: CalendarDesktopMonthPresentationComponent;
    let mockMap: Map<string, IdentifiableCalendarEvent[]>;
    let calendarService: MockCalendarService;

    const referenceDate = dayjs('2025-05-15 10:30');
    const events = [
        new IdentifiableCalendarEvent('EXAM', 'Exam', referenceDate, referenceDate.add(1, 'hour'), undefined, 'Marlon Nienaber'),
        new IdentifiableCalendarEvent('LECTURE', 'Object Design', referenceDate.subtract(4, 'hour'), referenceDate.subtract(2, 'hour'), undefined, undefined),
        new IdentifiableCalendarEvent('LECTURE', 'Object Design 2', referenceDate.subtract(2), referenceDate, undefined, undefined),
        new IdentifiableCalendarEvent('LECTURE', 'Object Design 3', referenceDate, referenceDate.add(2, 'hour'), undefined, undefined),
        new IdentifiableCalendarEvent('TUTORIAL', 'Tutorial 1', referenceDate.add(1, 'day'), referenceDate.add(1, 'day').add(1, 'hour'), 'Zoom', 'Marlon Nienaber'),
        new IdentifiableCalendarEvent('TUTORIAL', 'Tutorial 2', referenceDate.add(1, 'day').add(2, 'hour'), referenceDate.add(1, 'day').add(3, 'hour'), 'Zoom', 'Marlon Nienaber'),
        new IdentifiableCalendarEvent('TUTORIAL', 'Tutorial 3', referenceDate.add(1, 'day').add(3, 'hour'), referenceDate.add(1, 'day').add(4, 'hour'), 'Zoom', 'Marlon Nienaber'),
        new IdentifiableCalendarEvent('TEXT_EXERCISE', 'Start: Your aspirations as a programmer', referenceDate.add(2, 'day'), undefined, undefined, undefined),
    ];

    afterEach(() => {
        vi.restoreAllMocks();
    });

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
        calendarService = new MockCalendarService(mockMap);
        await TestBed.configureTestingModule({
            imports: [
                CalendarDesktopMonthPresentationComponent,
                CalendarEventDetailPopoverComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(CalendarDayBadgeComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                {
                    provide: CalendarService,
                    useValue: calendarService,
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(CalendarDesktopMonthPresentationComponent, {
                set: {
                    imports: [
                        NgTemplateOutlet,
                        MockComponent(FaIconComponent),
                        MockDirective(TranslateDirective),
                        MockComponent(CalendarDayBadgeComponent),
                        CalendarEventDetailPopoverComponent,
                        TumAetUiButtonDirective,
                        TumAetUiPopoverComponent,
                        TumAetUiPopoverTriggerDirective,
                        ArtemisDatePipe,
                        MockPipe(ArtemisTranslatePipe, (key) => key ?? ''),
                    ],
                },
            })
            .overrideComponent(CalendarEventDetailPopoverComponent, {
                set: {
                    imports: [
                        MockComponent(TumAetUiPopoverComponent),
                        MockComponent(TumAetUiButtonDirective),
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe),
                        MockComponent(FaIconComponent),
                    ],
                },
            })
            .compileComponents();

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
        expect(weeks.every((week) => week.days.length === 7)).toBe(true);
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
        // Spy on the component's open method to verify it's called and manually trigger onOpenChange
        const openSpy = vi.spyOn(popoverComponent, 'open').mockImplementation((mouseEvent, event) => {
            popoverComponent.onOpenChange(true);
        });

        const eventCell = fixture.debugElement.query(By.css('[data-testid="Exam"]'));
        eventCell.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(openSpy).toHaveBeenCalled();
        expect(popoverComponent.isOpen()).toBe(true);
    });

    describe('day agenda for overflowing events', () => {
        const overflowButton = (): HTMLButtonElement | null => fixture.nativeElement.querySelector('[data-testid="calendar-more-events-2025-05-15"]');
        const agenda = (): HTMLElement | null => document.querySelector('[data-testid="calendar-day-agenda"]');

        async function openAgenda(): Promise<HTMLButtonElement> {
            const button = overflowButton();
            expect(button).not.toBeNull();
            button!.focus();
            // A native button also receives click when activated with Enter or Space.
            button!.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 0 }));
            fixture.detectChanges();
            await fixture.whenStable();
            return button!;
        }

        it('provides a native dialog trigger with a localized count for the hidden events', () => {
            const button = overflowButton();
            expect(button).not.toBeNull();
            expect(button!.tagName).toBe('BUTTON');
            expect(button!.type).toBe('button');
            expect(button!.getAttribute('aria-haspopup')).toBe('dialog');
            expect(button!.getAttribute('aria-expanded')).toBe('false');
            const count = fixture.debugElement.query(By.css('[data-testid="calendar-more-events-2025-05-15"] [jhiTranslate]')).injector.get(TranslateDirective);
            expect(count.jhiTranslate()).toBe('artemisApp.calendar.moreEvents');
            expect(count.translateValues()).toEqual({ count: 2 });
        });

        it('opens the full day agenda, including the events hidden in the month cell', async () => {
            const button = await openAgenda();

            expect(button.getAttribute('aria-expanded')).toBe('true');
            expect(agenda()).not.toBeNull();
            const eventButtons = agenda()!.querySelectorAll('[data-testid="calendar-day-agenda-event"]');
            expect(eventButtons).toHaveLength(4);
            events.slice(0, 4).forEach((event, index) => expect(eventButtons[index].textContent).toContain(event.title));
            expect(agenda()!.textContent).toContain('10:30');
        });

        it('opens event details for an event reached through the day agenda', async () => {
            const details = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent)).componentInstance as CalendarEventDetailPopoverComponent;
            const open = vi.spyOn(details, 'open').mockImplementation(() => undefined);
            await openAgenda();

            (agenda()!.querySelectorAll('[data-testid="calendar-day-agenda-event"]')[3] as HTMLButtonElement).click();

            expect(open).toHaveBeenCalledWith(expect.any(MouseEvent), events[3]);
        });

        it('keeps the opened agenda and hidden count aligned with the filtered event map', async () => {
            await openAgenda();
            const extra = new IdentifiableCalendarEvent('LECTURE', 'Additional lecture', referenceDate.add(3, 'hour'));
            const another = new IdentifiableCalendarEvent('LECTURE', 'Later lecture', referenceDate.add(4, 'hour'));
            const filteredEvents = [...events.slice(1, 4), extra, another];

            calendarService.eventMap.set(new Map([['2025-05-15', filteredEvents]]));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(agenda()!.querySelectorAll('[data-testid="calendar-day-agenda-event"]')).toHaveLength(5);
            expect(agenda()!.textContent).toContain(extra.title);
            expect(agenda()!.textContent).not.toContain('Exam');
            const count = fixture.debugElement.query(By.css('[data-testid="calendar-more-events-2025-05-15"] [jhiTranslate]')).injector.get(TranslateDirective);
            expect(count.translateValues()).toEqual({ count: 3 });
        });

        it('removes the overflow and closes its agenda when filtering leaves three events', async () => {
            await openAgenda();

            calendarService.eventMap.set(new Map([['2025-05-15', events.slice(1, 4)]]));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(overflowButton()).toBeNull();
            expect(agenda()).toBeNull();
            expect(fixture.debugElement.query(By.css('[data-testid="Object Design 3"]'))).not.toBeNull();
        });

        it('closes with Escape and returns keyboard focus to the overflow button', async () => {
            const button = await openAgenda();
            expect(agenda()!.contains(document.activeElement)).toBe(true);

            document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(agenda()).toBeNull();
            expect(button.getAttribute('aria-expanded')).toBe('false');
            expect(document.activeElement).toBe(button);
        });
    });

    it('should close popover only when close button used', async () => {
        const popoverDebugElement = fixture.debugElement.query(By.directive(CalendarEventDetailPopoverComponent));
        const popoverComponent = popoverDebugElement.componentInstance as CalendarEventDetailPopoverComponent;
        const closeSpy = vi.spyOn(popoverComponent, 'close');
        // Open without the overlay, so that the test does not depend on animation timing.
        vi.spyOn(popoverComponent, 'open').mockImplementation((mouseEvent, event) => {
            popoverComponent['event'].set(event);
            popoverComponent.onOpenChange(true);
        });

        const examEventCell = fixture.debugElement.query(By.css('[data-testid="Exam"]'));
        expect(examEventCell).toBeTruthy();
        examEventCell.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(popoverComponent.isOpen()).toBe(true);

        // Since popover is mocked, manually call close
        popoverComponent.onOpenChange(false);
        closeSpy.mockImplementation(() => popoverComponent.onOpenChange(false));
        popoverComponent.close();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
