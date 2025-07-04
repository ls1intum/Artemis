import { AfterViewInit, Component, ElementRef, computed, input, signal, viewChild } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { Dayjs } from 'dayjs/esm';
import * as Utils from 'app/calendar/shared/util/calendar-util';
import { DayBadgeComponent } from '../../shared/day-badge/day-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarEventAndPositioning, PositionInfo } from 'app/calendar/shared/entities/calendar-event-positioning.model';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { CalendarEventService } from 'app/calendar/shared/service/calendar-event.service';
import { CalendarEventDetailPopoverComponent } from 'app/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';

@Component({
    selector: 'calendar-desktop-week',
    imports: [DayBadgeComponent, ArtemisTranslatePipe, NgbPopover, NgStyle, NgClass, CalendarEventDetailPopoverComponent],
    templateUrl: './calendar-week-presentation.component.html',
    styleUrl: './calendar-week-presentation.component.scss',
})
export class CalendarWeekPresentationComponent implements AfterViewInit {
    firstDayOfCurrentWeek = input.required<Dayjs>();
    selectedEvent = signal<CalendarEvent | undefined>(undefined);

    readonly utils = Utils;
    readonly weekDays = computed(() => this.computeWeekDaysFrom(this.firstDayOfCurrentWeek()));

    scrollContainer = viewChild<ElementRef>('scrollContainer');
    private popover?: NgbPopover;
    private dayToEventAndPositioningMap = computed(() => this.computeDayToEventAndPositioningMap(this.eventService.eventMap(), this.weekDays()));
    private static HOUR_SEGMENT_HEIGHT = 16 * 3.5;

    constructor(private eventService: CalendarEventService) {}

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = 7.5 * CalendarWeekPresentationComponent.HOUR_SEGMENT_HEIGHT;
        }
    }

    getEventsAndPositioningsFor(day: Dayjs): CalendarEventAndPositioning[] {
        return this.dayToEventAndPositioningMap().get(day.format('YYYY-MM-DD')) ?? [];
    }

    openPopover(event: CalendarEvent, popover: NgbPopover) {
        if (this.selectedEvent() === event) {
            this.closePopover();
            return;
        }
        this.selectedEvent.set(event);
        this.popover?.close();
        this.popover = popover;
        popover.open();
    }

    closePopover() {
        this.popover?.close();
        this.popover = undefined;
        this.selectedEvent.set(undefined);
    }

    private computeWeekDaysFrom(firstDayOfWeek: Dayjs): Dayjs[] {
        return Array.from({ length: 7 }, (_, i) => firstDayOfWeek.add(i, 'day'));
    }

    private computeDayToEventAndPositioningMap(eventMap: Map<string, CalendarEvent[]>, days: Dayjs[]): Map<string, CalendarEventAndPositioning[]> {
        const allowedKeys = new Set(days.map((day) => day.format('YYYY-MM-DD')));
        return new Map(
            Array.from(eventMap)
                .filter(([key]) => allowedKeys.has(key))
                .map(([key, events]) => {
                    const positioned = this.addPositioningsToCalendarEvents(events);
                    return [key, positioned];
                }),
        );
    }

    private addPositioningsToCalendarEvents(calendarEvents: CalendarEvent[]): CalendarEventAndPositioning[] {
        if (calendarEvents.length === 0) {
            return [];
        }

        const sorted = [...calendarEvents].sort((a, b) => a.startDate.diff(b.startDate));
        const positionedEvents: CalendarEventAndPositioning[] = [];

        let currentGroup: CalendarEvent[] = [];

        for (const event of sorted) {
            if (currentGroup.length === 0 || currentGroup.some((e) => this.overlaps(e, event))) {
                currentGroup.push(event);
            } else {
                positionedEvents.push(...this.calculatePositioningsForEventGroup(currentGroup));
                currentGroup = [event];
            }
        }
        positionedEvents.push(...this.calculatePositioningsForEventGroup(currentGroup));

        return positionedEvents;
    }

    private calculatePositioningsForEventGroup(currentGroup: CalendarEvent[]): CalendarEventAndPositioning[] {
        const pixelsPerMinute = CalendarWeekPresentationComponent.HOUR_SEGMENT_HEIGHT / 60;

        const horizontalMargin = 1.5;
        const gapBetweenEvents = 1.5;
        const totalGapBetweenEvents = currentGroup.length > 1 ? gapBetweenEvents * (currentGroup.length - 1) : 0;

        const availableWidth = 100 - totalGapBetweenEvents - 2 * horizontalMargin;
        const eventWidth = availableWidth / currentGroup.length;

        return currentGroup.map((event, index) => {
            const top = event.startDate.diff(event.startDate.startOf('day'), 'minute') * pixelsPerMinute;
            const height = event.endDate ? event.endDate.diff(event.startDate, 'minute') * pixelsPerMinute : 24;
            const left = horizontalMargin + index * (eventWidth + gapBetweenEvents);

            const pos: PositionInfo = { top, height, left, width: eventWidth };
            return { event, position: pos };
        });
    }

    private overlaps(firstEvent: CalendarEvent, secondEvent: CalendarEvent): boolean {
        const firstStartDate = firstEvent.startDate;
        const firstEndDate = firstEvent.endDate;
        const secondStartDate = secondEvent.startDate;
        const secondEndDate = secondEvent.endDate;

        if (!firstEndDate && !secondEndDate) {
            return firstStartDate.isSame(secondStartDate, 'minute');
        } else if (!firstEndDate) {
            return firstStartDate.isSameOrBefore(secondEndDate!, 'minute') && secondStartDate.isSameOrBefore(firstStartDate, 'minute');
        } else if (!secondEndDate) {
            return secondStartDate.isSameOrBefore(firstEndDate, 'minute') && firstStartDate.isSameOrBefore(secondStartDate, 'minute');
        } else {
            const firstStartFallsInSecondRange = firstStartDate.isSameOrBefore(secondEndDate, 'minute') && secondStartDate.isSameOrBefore(firstStartDate, 'minute');
            const firstEndFallsInSecondRange = firstEndDate.isSameOrBefore(secondEndDate, 'minute') && secondStartDate.isSameOrBefore(firstEndDate, 'minute');
            const firstEventHugsSecondEvent = firstStartDate.isSameOrBefore(secondStartDate, 'minute') && secondEndDate.isSameOrBefore(firstEndDate, 'minute');
            return firstStartFallsInSecondRange || firstEndFallsInSecondRange || firstEventHugsSecondEvent;
        }
    }
}
