import { AfterViewInit, Component, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { Dayjs } from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarEventAndPosition, PositionInfo } from 'app/core/calendar/shared/entities/calendar-event-and-position.model';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { CalendarEventDetailPopoverComponent } from 'app/core/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';

@Component({
    selector: 'calendar-desktop-week',
    imports: [CalendarDayBadgeComponent, ArtemisTranslatePipe, NgbPopover, NgStyle, NgClass, CalendarEventDetailPopoverComponent],
    templateUrl: './calendar-week-presentation.component.html',
    styleUrl: './calendar-week-presentation.component.scss',
})
export class CalendarWeekPresentationComponent implements AfterViewInit {
    private eventService = inject(CalendarEventService);

    firstDayOfCurrentWeek = input.required<Dayjs>();
    selectedEvent = signal<CalendarEvent | undefined>(undefined);
    scrollContainer = viewChild<ElementRef>('scrollContainer');

    readonly utils = utils;
    readonly CalendarEventType = CalendarEventType;
    readonly weekDays = computed(() => this.computeWeekDaysFrom(this.firstDayOfCurrentWeek()));

    private popover?: NgbPopover;
    private dayToEventAndPositionMap = computed(() => this.computeDayToEventAndPositionMap(this.eventService.eventMap(), this.weekDays()));
    private static readonly PIXELS_PER_REM = 16;
    private static readonly HOUR_SEGMENT_HEIGHT_IN_PIXEL = 3.5 * CalendarWeekPresentationComponent.PIXELS_PER_REM;

    ngAfterViewInit(): void {
        const container = this.scrollContainer();
        if (container) {
            container.nativeElement.scrollTop = 7.5 * CalendarWeekPresentationComponent.HOUR_SEGMENT_HEIGHT_IN_PIXEL;
        }
    }

    getEventsAndPositionsFor(day: Dayjs): CalendarEventAndPosition[] {
        return this.dayToEventAndPositionMap().get(day.format('YYYY-MM-DD')) ?? [];
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
        return Array.from({ length: 7 }, (_, index) => firstDayOfWeek.add(index, 'day'));
    }

    private computeDayToEventAndPositionMap(eventMap: Map<string, CalendarEvent[]>, days: Dayjs[]): Map<string, CalendarEventAndPosition[]> {
        const dayKeysToBeIncluded = new Set(days.map((day) => day.format('YYYY-MM-DD')));
        return new Map(
            Array.from(eventMap)
                .filter(([key]) => dayKeysToBeIncluded.has(key))
                .map(([key, events]) => {
                    const positioned = this.addPositionsToCalendarEvents(events);
                    return [key, positioned];
                }),
        );
    }

    /**
     * Groups overlapping events and calculates positions based on the groups.
     *
     * @param calendarEvents - The list of calendar events to position.
     * @returns A list of calendar events with associated position metadata.
     */
    private addPositionsToCalendarEvents(calendarEvents: CalendarEvent[]): CalendarEventAndPosition[] {
        if (calendarEvents.length === 0) {
            return [];
        }

        const sorted = [...calendarEvents].sort((firstEvent, secondEvent) => firstEvent.startDate.diff(secondEvent.startDate));

        const positionedEvents: CalendarEventAndPosition[] = [];
        let currentGroup: CalendarEvent[] = [];
        for (const event of sorted) {
            if (currentGroup.length === 0 || currentGroup.some((otherEvent) => this.doEventsOverlap(otherEvent, event))) {
                currentGroup.push(event);
            } else {
                positionedEvents.push(...this.calculatePositionsForEventGroup(currentGroup));
                currentGroup = [event];
            }
        }
        positionedEvents.push(...this.calculatePositionsForEventGroup(currentGroup));

        return positionedEvents;
    }

    private calculatePositionsForEventGroup(group: CalendarEvent[]): CalendarEventAndPosition[] {
        const pixelsPerMinute = CalendarWeekPresentationComponent.HOUR_SEGMENT_HEIGHT_IN_PIXEL / 60;

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
