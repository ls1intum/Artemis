import { Component, computed, inject, input } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { Dayjs } from 'dayjs/esm';
import { CalendarEventAndPosition, PositionInfo } from 'app/core/calendar/shared/entities/calendar-event-and-position.model';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover-component/calendar-event-detail-popover.component';

type Day = { date: Dayjs; eventsAndPositions: CalendarEventAndPosition[]; id: string };

@Component({
    selector: 'jhi-calendar-events-per-day-section',
    imports: [NgClass, NgStyle, CalendarEventDetailPopoverComponent],
    templateUrl: './calendar-events-per-day-section.component.html',
    styleUrl: './calendar-events-per-day-section.component.scss',
})
export class CalendarEventsPerDaySectionComponent {
    // amount of pixels in 1rem
    static readonly PIXELS_PER_REM = 16;
    // height of one hour in the grid that is part of the template (measured in rem)
    static readonly HOUR_HEIGHT_IN_REM = 3.5;
    // height of one hour in the grid that is part of the template (measured in px)
    static readonly HOUR_HEIGHT_IN_PIXEL = CalendarEventsPerDaySectionComponent.HOUR_HEIGHT_IN_REM * CalendarEventsPerDaySectionComponent.PIXELS_PER_REM;
    // amount of pixels that represent 1min in the grid that is part of the template
    static readonly PIXELS_PER_MINUTE = CalendarEventsPerDaySectionComponent.HOUR_HEIGHT_IN_PIXEL / 60;
    // amount of minutes represented by 1px in the grid that is part of the template
    static readonly MINUTES_PER_PIXEL = 1 / CalendarEventsPerDaySectionComponent.PIXELS_PER_MINUTE;
    // default height for events that are displayed in the template (equivalent to 1.5rem)
    static readonly DEFAULT_EVENT_HEIGHT_IN_PIXEL = 24;
    // rounded amount of minutes that is equivalent to the default height of events
    static readonly DEFAULT_EVENT_LENGTH_IN_MINUTES = Math.ceil(
        CalendarEventsPerDaySectionComponent.DEFAULT_EVENT_HEIGHT_IN_PIXEL * CalendarEventsPerDaySectionComponent.MINUTES_PER_PIXEL,
    );

    private eventService = inject(CalendarService);
    private dateToEventAndPositionMap = computed(() => this.computeDateToEventAndPositionMap(this.eventService.eventMap(), this.dates()));

    readonly CalendarEventType = CalendarEventType;

    dates = input.required<Dayjs[]>();
    hoursOfDay = utils.getHoursOfDay();
    zeroToTwentyFour = utils.range(24);
    days = computed<Day[]>(() => {
        return this.dates().map((date) => ({
            date: date,
            eventsAndPositions: this.getEventsAndPositions(date),
            id: utils.identify(date),
        }));
    });

    private getEventsAndPositions(date: Dayjs): CalendarEventAndPosition[] {
        return this.dateToEventAndPositionMap().get(date.format('YYYY-MM-DD')) ?? [];
    }

    private computeDateToEventAndPositionMap(eventMap: Map<string, CalendarEvent[]>, dates: Dayjs[]): Map<string, CalendarEventAndPosition[]> {
        const dateKeysToBeIncluded = new Set(dates.map((date) => date.format('YYYY-MM-DD')));
        return new Map(
            Array.from(eventMap)
                .filter(([key]) => dateKeysToBeIncluded.has(key))
                .map(([key, events]) => {
                    const eventsWithPositions = this.addPositionsToCalendarEvents(events);
                    return [key, eventsWithPositions];
                }),
        );
    }

    /**
     * Groups overlapping events and adds positions for each group.
     *
     * @param calendarEvents - The list of calendar events to position.
     * @returns A list of calendar events with associated positions.
     */
    private addPositionsToCalendarEvents(calendarEvents: CalendarEvent[]): CalendarEventAndPosition[] {
        if (calendarEvents.length === 0) {
            return [];
        }
        const eventsWithPositions: CalendarEventAndPosition[] = [];
        let currentGroup: CalendarEvent[] = [];
        for (const event of calendarEvents) {
            if (currentGroup.length === 0 || currentGroup.some((otherEvent) => this.doEventsOverlap(otherEvent, event))) {
                currentGroup.push(event);
            } else {
                eventsWithPositions.push(...this.addPositionsToEventGroup(currentGroup));
                currentGroup = [event];
            }
        }
        eventsWithPositions.push(...this.addPositionsToEventGroup(currentGroup));

        return eventsWithPositions;
    }

    private addPositionsToEventGroup(group: CalendarEvent[]): CalendarEventAndPosition[] {
        const widthAndLeftOffsetFunction = this.getWidthAndLeftOffsetFunction(group.length);
        return group.map((event, index) => {
            const top = this.getTop(event);
            const height = this.getHeight(event);
            const left = widthAndLeftOffsetFunction.leftOffset(index);

            const position: PositionInfo = { top, height, left, width: widthAndLeftOffsetFunction.eventWidth };
            return { event: event, position: position };
        });
    }

    private getWidthAndLeftOffsetFunction(groupSize: number) {
        const horizontalMarginAsPercentage = 1.5;
        const gapBetweenEventsAsPercentage = 1.5;
        const totalGapBetweenEventsAsPercentage = groupSize > 1 ? gapBetweenEventsAsPercentage * (groupSize - 1) : 0;
        const availableWidthAsPercentage = 100 - totalGapBetweenEventsAsPercentage - 2 * horizontalMarginAsPercentage;
        const eventWidthAsPercentage = availableWidthAsPercentage / groupSize;

        return {
            eventWidth: eventWidthAsPercentage,
            leftOffset: (index: number) => horizontalMarginAsPercentage + index * (eventWidthAsPercentage + gapBetweenEventsAsPercentage),
        };
    }

    private getTop(event: CalendarEvent): number {
        const minutes = event.startDate.diff(event.startDate.startOf('day'), 'minute');
        return minutes * CalendarEventsPerDaySectionComponent.PIXELS_PER_MINUTE;
    }

    private getHeight(event: CalendarEvent): number {
        if (event.endDate) {
            return Math.max(
                event.endDate.diff(event.startDate, 'minute') * CalendarEventsPerDaySectionComponent.PIXELS_PER_MINUTE,
                CalendarEventsPerDaySectionComponent.DEFAULT_EVENT_HEIGHT_IN_PIXEL,
            );
        } else {
            return CalendarEventsPerDaySectionComponent.DEFAULT_EVENT_HEIGHT_IN_PIXEL;
        }
    }

    private doEventsOverlap(firstEvent: CalendarEvent, secondEvent: CalendarEvent): boolean {
        const firstStartDate = firstEvent.startDate;
        const firstEndDate = this.getDisplayedEndOf(firstEvent);
        const secondStartDate = secondEvent.startDate;
        const secondEndDate = this.getDisplayedEndOf(secondEvent);

        const firstStartFallsInSecondRange = this.doesDateFallInRange(firstStartDate, secondStartDate, secondEndDate!);
        const firstEndFallsInSecondRange = this.doesDateFallInRange(firstEndDate, secondStartDate, secondEndDate!);
        const firstEventEngulfsSecondEvent = this.doesFirstRangeEngulfSecondRange(firstStartDate, firstEndDate, secondStartDate, secondEndDate);
        return firstStartFallsInSecondRange || firstEndFallsInSecondRange || firstEventEngulfsSecondEvent;
    }

    private getDisplayedEndOf(event: CalendarEvent): Dayjs {
        const endWithDefaultLength = event.startDate.add(CalendarEventsPerDaySectionComponent.DEFAULT_EVENT_LENGTH_IN_MINUTES, 'minute');
        return event.endDate && event.endDate.isAfter(endWithDefaultLength) ? event.endDate : endWithDefaultLength;
    }

    private doesDateFallInRange(date: Dayjs, rangeStart: Dayjs, rangeEnd: Dayjs): boolean {
        return rangeStart.isSameOrBefore(date, 'minute') && date.isSameOrBefore(rangeEnd, 'minute');
    }

    private doesFirstRangeEngulfSecondRange(firstRangeStart: Dayjs, firstRangeEnd: Dayjs, secondRangeStart: Dayjs, secondRangeEnd: Dayjs): boolean {
        return firstRangeStart.isSameOrBefore(secondRangeStart, 'minute') && secondRangeEnd.isSameOrBefore(firstRangeEnd, 'minute');
    }
}
