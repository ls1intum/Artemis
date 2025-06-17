import { AfterViewInit, Component, ElementRef, Input, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
import { CalendarEvent } from '../../entities/calendar-event.model';
import { CalendarEventPositioning, PositionInfo } from '../../entities/calendar-event-positioning.model';
import { NgStyle } from '@angular/common';

@Component({
    selector: 'timed-event-section',
    imports: [NgStyle],
    templateUrl: './timed-event-section.component.html',
    styleUrl: './timed-event-section.component.scss',
})
export class TimedEventSectionComponent implements OnInit, AfterViewInit {
    @Input() days: dayjs.Dayjs[] = [];

    dayEventMap = new Map<string, CalendarEventPositioning[]>();
    hours = Array.from({ length: 23 }, (_, i) => `${(i + 1).toString().padStart(2, '0')}:00`);

    private events: CalendarEvent[] = [
        {
            id: '1',
            name: 'Team Meeting',
            start: dayjs().hour(9).minute(0),
            end: dayjs().hour(10).minute(30),
        },
        {
            id: '2',
            name: 'Design Review',
            start: dayjs().hour(10).minute(0),
            end: dayjs().hour(11).minute(0),
        },
        {
            id: '3',
            name: 'Design Review 2',
            start: dayjs().hour(10).minute(0),
            end: dayjs().hour(11).minute(0),
        },
        {
            id: '4',
            name: 'Lunch Break',
            start: dayjs().hour(12).minute(0),
            end: dayjs().hour(14).minute(30),
        },
    ];
    private static HOUR_SEGMENT_HIGHT = 40;

    constructor(private hostRef: ElementRef<HTMLElement>) {}

    ngOnInit(): void {
        this.computePositionedEvents();
    }

    ngAfterViewInit(): void {
        const scrollTop = 7.5 * TimedEventSectionComponent.HOUR_SEGMENT_HIGHT;
        this.hostRef.nativeElement.scrollTop = scrollTop;
    }

    private computePositionedEvents(): void {
        const pixelsPerMinute = TimedEventSectionComponent.HOUR_SEGMENT_HIGHT / 60;

        const sorted = [...this.events].sort((a, b) => a.start.diff(b.start));
        const positionedEvents: CalendarEventPositioning[] = [];

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

        this.dayEventMap.clear();
        for (const item of positionedEvents) {
            const key = item.event.start.format('YYYY-MM-DD');
            if (!this.dayEventMap.has(key)) {
                this.dayEventMap.set(key, []);
            }
            this.dayEventMap.get(key)!.push(item);
        }
    }

    private overlaps(a: CalendarEvent, b: CalendarEvent): boolean {
        return a.start.isBefore(b.end) && b.start.isBefore(a.end);
    }

    range(n: number): number[] {
        return Array.from({ length: n }, (_, i) => i);
    }
}
