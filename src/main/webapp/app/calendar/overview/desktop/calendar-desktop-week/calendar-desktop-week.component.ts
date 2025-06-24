import { AfterViewInit, Component, ElementRef, computed, input, viewChild } from '@angular/core';
import { NgStyle } from '@angular/common';
import { Dayjs } from 'dayjs/esm';
import * as Utils from 'app/calendar/shared/util/calendar-util';
import { DayBadgeComponent } from '../../../shared/day-badge/day-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarEventDummyService } from 'app/calendar/shared/service/calendar-event-dummy.service';
import { CalendarEventAndPositioning, PositionInfo } from 'app/calendar/shared/entities/calendar-event-positioning.model';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

// TODO: where to  move this?

@Component({
    selector: 'calendar-desktop-week',
    imports: [DayBadgeComponent, ArtemisTranslatePipe, NgStyle],
    templateUrl: './calendar-desktop-week.component.html',
    styleUrl: './calendar-desktop-week.component.scss',
})
export class CalendarDesktopWeekComponent implements AfterViewInit {
    firstDayOfCurrentMonth = input.required<Dayjs>();
    firstDayOfCurrentWeek = input.required<Dayjs>();

    readonly utils = Utils;
    readonly weekDays = computed(() => this.computeWeekDaysFrom(this.firstDayOfCurrentWeek()));
    readonly scrollContainer = viewChild<ElementRef>('scrollContainer');

    private dayEventMap = computed(() => this.computePositionedEventsFor(this.weekDays()));
    private static HOUR_SEGMENT_HEIGHT = 36;

    constructor(private eventService: CalendarEventDummyService) {}

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = 7.5 * CalendarDesktopWeekComponent.HOUR_SEGMENT_HEIGHT;
        }
    }

    private computeWeekDaysFrom(firstDayOfWeek: Dayjs): Dayjs[] {
        return Array.from({ length: 7 }, (_, i) => firstDayOfWeek.add(i, 'day'));
    }

    getEventsAndPositionings(day: Dayjs): CalendarEventAndPositioning[] {
        return this.dayEventMap().get(day.format('YYYY-MM-DD')) ?? [];
    }

    private computePositionedEventsFor(days: Dayjs[]): Map<string, CalendarEventAndPositioning[]> {
        const pixelsPerMinute = CalendarDesktopWeekComponent.HOUR_SEGMENT_HEIGHT / 60;

        const events = days.flatMap((day) => this.eventService.getEventsOfDay(day));
        const sorted = events.sort((a, b) => a.start.diff(b.start));
        const positionedEvents: CalendarEventAndPositioning[] = [];

        let currentGroup: CalendarEvent[] = [];

        const flushGroup = () => {
            if (currentGroup.length === 0) return;

            const outerPadding = 2;
            const gapBetweenEvents = currentGroup.length > 1 ? outerPadding * (currentGroup.length - 1) : 0;

            const availableWidth = 100 - outerPadding * 2 - gapBetweenEvents;
            const eventWidth = availableWidth / currentGroup.length;

            currentGroup.forEach((event, index) => {
                const top = event.start.diff(event.start.startOf('day'), 'minute') * pixelsPerMinute;
                const height = event.end.diff(event.start, 'minute') * pixelsPerMinute;
                const left = outerPadding + index * (eventWidth + outerPadding);

                const pos: PositionInfo = { top, height, left, width: eventWidth };
                positionedEvents.push({ event, position: pos });
            });

            currentGroup = [];
        };

        for (const event of sorted) {
            if (currentGroup.length === 0 || currentGroup.some((e) => this.overlaps(e, event))) {
                currentGroup.push(event);
            } else {
                flushGroup();
                currentGroup.push(event);
            }
        }

        flushGroup();

        const dayEventMap = new Map<string, CalendarEventAndPositioning[]>();
        for (const item of positionedEvents) {
            const key = item.event.start.format('YYYY-MM-DD');
            if (!dayEventMap.has(key)) {
                dayEventMap.set(key, []);
            }
            dayEventMap.get(key)!.push(item);
        }

        return dayEventMap;
    }

    // TODO: verify that this does what it claims
    private overlaps(a: CalendarEvent, b: CalendarEvent): boolean {
        return a.start.isBefore(b.end) && b.start.isBefore(a.end);
    }
}
