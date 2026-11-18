import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { MockDirective } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { CalendarEventDetailPopoverComponent } from './calendar-event-detail-popover.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
describe('CalendarEventDetailPopoverComponent', () => {
    let fixture: ComponentFixture<CalendarEventDetailPopoverComponent>;
    let component: CalendarEventDetailPopoverComponent;
    let fakeMouseEvent: MouseEvent;

    afterEach(() => {
        component.close();
        fixture.destroy();
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarEventDetailPopoverComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventDetailPopoverComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        const anchorElement = document.createElement('div');
        document.body.appendChild(anchorElement);
        fakeMouseEvent = {
            currentTarget: anchorElement,
            stopPropagation: vi.fn(),
        } as unknown as MouseEvent;
    });

    it('should render time-row if endDate is provided', async () => {
        const event = new IdentifiableCalendarEvent('LECTURE', 'Lecture 1', dayjs('2025-07-05T10:00:00'), dayjs('2025-07-05T12:00:00'), 'Room 42', 'Dr. Smith');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(document.querySelector('#time-row')).toBeTruthy();
    });

    it('should render only time-row if endDate is missing', async () => {
        const event = new IdentifiableCalendarEvent('LECTURE', 'Start: Lecture 1', dayjs('2025-07-05T10:00:00'), undefined, 'Room 42', 'Dr. Smith');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(document.querySelector('#time-row')).toBeTruthy();
    });

    it('should render location-row if location is present', async () => {
        const event = new IdentifiableCalendarEvent('LECTURE', 'Lecture 2', dayjs(), dayjs(), 'Main Hall', 'Dr. Jane');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(document.querySelector('#location-row')).toBeTruthy();
    });

    it('should not render location-row if location is missing', async () => {
        const event = new IdentifiableCalendarEvent('LECTURE', 'Lecture 2', dayjs(), dayjs(), undefined, 'Dr. Jane');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(document.querySelector('#location-row')).toBeFalsy();
    });

    it('should render facilitator-row if facilitator is present', async () => {
        const event = new IdentifiableCalendarEvent('TUTORIAL', 'Tutorial 1', dayjs(), dayjs(), 'Lab 1', 'John Doe');

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(document.querySelector('#facilitator-row')).toBeTruthy();
    });

    it('should close and forget the event when the close button is used', async () => {
        const event = new IdentifiableCalendarEvent('LECTURE', 'Lecture 1', dayjs(), dayjs(), 'Room 42', 'Dr. Smith');
        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.isOpen()).toBe(true);

        (document.querySelector('[data-testid="event-detail-close-button"]') as HTMLElement).click();
        fixture.detectChanges();

        expect(component.isOpen()).toBe(false);
        expect(component.event()).toBeUndefined();
    });

    it('should not render facilitator-row if facilitator is missing', async () => {
        const event = new IdentifiableCalendarEvent('TUTORIAL', 'Tutorial 2', dayjs(), dayjs(), 'Lab 2', undefined);

        component.open(fakeMouseEvent, event);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(document.querySelector('#facilitator-row')).toBeFalsy();
    });
});
