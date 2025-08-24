import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { Dayjs } from 'dayjs/esm';
import { CalendarEventAndPosition, PositionInfo } from 'app/core/calendar/shared/entities/calendar-event-and-position.model';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';

type Day = { date: Dayjs; eventsAndPositions: CalendarEventAndPosition[]; id: string };

@Component({
    selector: 'jhi-calendar-events-per-day-section',
    imports: [ArtemisTranslatePipe, NgClass, NgStyle, CalendarEventDetailPopoverComponent, NgbPopover],
    templateUrl: './calendar-events-per-day-section.component.html',
    styleUrl: './calendar-events-per-day-section.component.scss',
})
export class CalendarEventsPerDaySectionComponent {
    static readonly PIXELS_PER_REM = 16;
    static readonly HOUR_SEGMENT_HEIGHT_IN_PIXEL = 3.5 * CalendarEventsPerDaySectionComponent.PIXELS_PER_REM;

    private eventService = inject(CalendarEventService);
    private dateToEventAndPositionMap = computed(() => this.computeDateToEventAndPositionMap(this.eventService.eventMap(), this.dates()));
    private popover?: NgbPopover;

    readonly CalendarEventType = CalendarEventType;

    dates = input.required<Dayjs[]>();
    isEventSelected = output<boolean>();
    selectedEvent = signal<CalendarEvent | undefined>(undefined);
    hoursOfDay = utils.getHoursOfDay();
    zeroToTwentyFour = utils.range(24);
    days = computed<Day[]>(() => {
        return this.dates().map((date) => ({
            date: date,
            eventsAndPositions: this.getEventsAndPositions(date),
            id: utils.identify(date),
        }));
    });

    constructor() {
        effect(() => {
            this.isEventSelected.emit(this.selectedEvent() !== undefined);
        });
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

        const sorted = [...calendarEvents].sort((firstEvent, secondEvent) => firstEvent.startDate.diff(secondEvent.startDate));

        const eventsWithPositions: CalendarEventAndPosition[] = [];
        let currentGroup: CalendarEvent[] = [];
        for (const event of sorted) {
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
        const pixelsPerMinute = CalendarEventsPerDaySectionComponent.HOUR_SEGMENT_HEIGHT_IN_PIXEL / 60;

        const widthAndLeftOffsetFunction = this.getWidthAndLeftOffsetFunction(group.length);

        return group.map((event, index) => {
            const top = this.getTop(event, pixelsPerMinute);
            const height = this.getHeight(event, pixelsPerMinute);
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

    private getTop(event: CalendarEvent, pixelsPerMinute: number): number {
        const minutes = event.startDate.diff(event.startDate.startOf('day'), 'minute');
        return minutes * pixelsPerMinute;
    }

    private getHeight(event: CalendarEvent, pixelsPerMinute: number): number {
        const defaultHeightInPixel = 24;
        return event.endDate ? event.endDate.diff(event.startDate, 'minute') * pixelsPerMinute : defaultHeightInPixel;
    }

    private doEventsOverlap(firstEvent: CalendarEvent, secondEvent: CalendarEvent): boolean {
        const firstStartDate = firstEvent.startDate;
        const firstEndDate = firstEvent.endDate;
        const secondStartDate = secondEvent.startDate;
        const secondEndDate = secondEvent.endDate;

        if (!firstEndDate && !secondEndDate) {
            return this.areDatesSameMinute(firstStartDate, secondStartDate);
        }
        if (!firstEndDate) {
            return this.doesDateFallInRange(firstStartDate, secondStartDate, secondEndDate!);
        }
        if (!secondEndDate) {
            return this.doesDateFallInRange(secondStartDate, firstStartDate, firstEndDate!);
        }

        const firstStartFallsInSecondRange = this.doesDateFallInRange(firstStartDate, secondStartDate, secondEndDate!);
        const firstEndFallsInSecondRange = this.doesDateFallInRange(firstEndDate, secondStartDate, secondEndDate!);
        const firstEventEngulfsSecondEvent = this.doesFirstRangeEngulfSecondRange(firstStartDate, firstEndDate, secondStartDate, secondEndDate);
        return firstStartFallsInSecondRange || firstEndFallsInSecondRange || firstEventEngulfsSecondEvent;
    }

    private areDatesSameMinute(firstDate: Dayjs, secondDate: Dayjs): boolean {
        return firstDate.isSame(secondDate, 'minute');
    }

    private doesDateFallInRange(date: Dayjs, rangeStart: Dayjs, rangeEnd: Dayjs): boolean {
        return rangeStart.isSameOrBefore(date, 'minute') && date.isSameOrBefore(rangeEnd, 'minute');
    }

    private doesFirstRangeEngulfSecondRange(firstRangeStart: Dayjs, firstRangeEnd: Dayjs, secondRangeStart: Dayjs, secondRangeEnd: Dayjs): boolean {
        return firstRangeStart.isSameOrBefore(secondRangeStart, 'minute') && secondRangeEnd.isSameOrBefore(firstRangeEnd, 'minute');
    }
}
