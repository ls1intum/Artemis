import { AfterViewInit, Component, ElementRef, computed, input, viewChild } from '@angular/core';
import { NgStyle } from '@angular/common';
import { Dayjs } from 'dayjs/esm';
import * as Utils from 'app/calendar/shared/util/calendar-util';
import { DayBadgeComponent } from '../../shared/day-badge/day-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CalendarEventDummyService } from 'app/calendar/shared/service/calendar-event-dummy.service';
import { CalendarEventAndPositioning, PositionInfo } from 'app/calendar/shared/entities/calendar-event-positioning.model';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

@Component({
    selector: 'calendar-desktop-week',
    imports: [DayBadgeComponent, ArtemisTranslatePipe, NgStyle],
    templateUrl: './calendar-week-presentation.component.html',
    styleUrl: './calendar-week-presentation.component.scss',
})
export class CalendarWeekPresentationComponent implements AfterViewInit {
    firstDayOfCurrentMonth = input.required<Dayjs>();
    firstDayOfCurrentWeek = input.required<Dayjs>();

    readonly utils = Utils;
    readonly weekDays = computed(() => this.computeWeekDaysFrom(this.firstDayOfCurrentWeek()));
    readonly scrollContainer = viewChild<ElementRef>('scrollContainer');

    private dayToEventAndPositioningMap = computed(() => this.computeDayToEventAndPositioningMap(this.weekDays()));
    private static HOUR_SEGMENT_HEIGHT = 16 * 3.5;

    constructor(private eventService: CalendarEventDummyService) {}

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = 7.5 * CalendarWeekPresentationComponent.HOUR_SEGMENT_HEIGHT;
        }
    }

    getEventsAndPositioningsFor(day: Dayjs): CalendarEventAndPositioning[] {
        return this.dayToEventAndPositioningMap().get(day.format('YYYY-MM-DD')) ?? [];
    }

    private computeWeekDaysFrom(firstDayOfWeek: Dayjs): Dayjs[] {
        return Array.from({ length: 7 }, (_, i) => firstDayOfWeek.add(i, 'day'));
    }

    private computeDayToEventAndPositioningMap(days: Dayjs[]): Map<string, CalendarEventAndPositioning[]> {
        const events = days.flatMap((day) => this.eventService.getEventsOfDay(day));
        if (events.length === 0) {
            return new Map<string, CalendarEventAndPositioning[]>();
        }
        const sorted = events.sort((a, b) => a.startDate.diff(b.startDate));

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

        const dayEventMap = new Map<string, CalendarEventAndPositioning[]>();
        for (const item of positionedEvents) {
            const key = item.event.startDate.format('YYYY-MM-DD');
            if (!dayEventMap.has(key)) {
                dayEventMap.set(key, []);
            }
            dayEventMap.get(key)!.push(item);
        }

        return dayEventMap;
    }

    private calculatePositioningsForEventGroup(currentGroup: CalendarEvent[]): CalendarEventAndPositioning[] {
        const pixelsPerMinute = CalendarWeekPresentationComponent.HOUR_SEGMENT_HEIGHT / 60;

        const gapBetweenEvents = 2;
        const totalGapBetweenEvents = currentGroup.length > 1 ? gapBetweenEvents * (currentGroup.length - 1) : 0;

        const availableWidth = 100 - totalGapBetweenEvents;
        const eventWidth = availableWidth / currentGroup.length;

        return currentGroup.map((event, index) => {
            const top = event.startDate.diff(event.startDate.startOf('day'), 'minute') * pixelsPerMinute;
            const height = event.endDate ? event.endDate.diff(event.startDate, 'minute') * pixelsPerMinute : 28;
            const left = index * (eventWidth + gapBetweenEvents);

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
