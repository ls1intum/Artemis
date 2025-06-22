import { AfterViewInit, Component, ElementRef, computed, input } from '@angular/core';
import { Dayjs } from 'dayjs/esm';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { CalendarEventAndPositioning, PositionInfo } from 'app/calendar/shared/entities/calendar-event-positioning.model';
import { NgStyle } from '@angular/common';
import { CalendarEventDummyService } from 'app/calendar/shared/service/calendar-event-dummy.service';
import * as Utils from 'app/calendar/shared/util/calendar-util';

@Component({
    selector: 'timed-event-section',
    imports: [NgStyle],
    templateUrl: './timed-event-section.component.html',
    styleUrl: './timed-event-section.component.scss',
})
export class TimedEventSectionComponent implements AfterViewInit {
    days = input.required<Dayjs[]>();

    readonly utils = Utils;
    private dayEventMap = computed(() => this.computePositionedEventsFor(this.days()));

    private static HOUR_SEGMENT_HEIGHT = 36;

    constructor(
        private eventService: CalendarEventDummyService,
        private hostRef: ElementRef<HTMLElement>,
    ) {}

    ngAfterViewInit(): void {
        this.hostRef.nativeElement.scrollTop = 7.5 * TimedEventSectionComponent.HOUR_SEGMENT_HEIGHT;
    }

    getEventsAndPositionings(day: Dayjs): CalendarEventAndPositioning[] {
        return this.dayEventMap().get(day.format('YYYY-MM-DD')) ?? [];
    }

    private computePositionedEventsFor(days: Dayjs[]): Map<string, CalendarEventAndPositioning[]> {
        const pixelsPerMinute = TimedEventSectionComponent.HOUR_SEGMENT_HEIGHT / 60;

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
