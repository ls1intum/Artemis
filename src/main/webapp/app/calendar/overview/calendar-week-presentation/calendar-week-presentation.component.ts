import { AfterViewInit, Component, ElementRef, computed, input, signal, viewChild } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { Dayjs } from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as utils from 'app/calendar/shared/util/calendar-util';
import { CalendarEventAndPosition, PositionInfo } from 'app/calendar/shared/entities/calendar-event-and-position.model';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { CalendarEventService } from 'app/calendar/shared/service/calendar-event.service';
import { CalendarEventDetailPopoverComponent } from 'app/calendar/shared/calendar-event-detail-popover/calendar-event-detail-popover.component';
import { CalendarDayBadgeComponent } from 'app/calendar/shared/calendar-day-badge/calendar-day-badge.component';

@Component({
    selector: 'calendar-desktop-week',
    imports: [CalendarDayBadgeComponent, ArtemisTranslatePipe, NgbPopover, NgStyle, NgClass, CalendarEventDetailPopoverComponent],
    templateUrl: './calendar-week-presentation.component.html',
    styleUrl: './calendar-week-presentation.component.scss',
})
export class CalendarWeekPresentationComponent implements AfterViewInit {
    firstDayOfCurrentWeek = input.required<Dayjs>();
    selectedEvent = signal<CalendarEvent | undefined>(undefined);
    scrollContainer = viewChild<ElementRef>('scrollContainer');

    readonly utils = utils;
    readonly weekDays = computed(() => this.computeWeekDaysFrom(this.firstDayOfCurrentWeek()));

    private popover?: NgbPopover;
    private dayToEventAndPositionMap = computed(() => this.computeDayToEventAndPositionMap(this.eventService.eventMap(), this.weekDays()));
    private static readonly PIXELS_PER_REM = 16;
    private static readonly HOUR_SEGMENT_HEIGHT_IN_PIXEL = 3.5 * CalendarWeekPresentationComponent.PIXELS_PER_REM;

    constructor(private eventService: CalendarEventService) {}

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

    private addPositionsToCalendarEvents(calendarEvents: CalendarEvent[]): CalendarEventAndPosition[] {
        if (calendarEvents.length === 0) {
            return [];
        }

        const sorted = [...calendarEvents].sort((firstEvent, secondEvent) => firstEvent.startDate.diff(secondEvent.startDate));

        const positionedEvents: CalendarEventAndPosition[] = [];
        let currentGroup: CalendarEvent[] = [];
        for (const event of sorted) {
            if (currentGroup.length === 0 || currentGroup.some((otherEvent) => this.overlaps(otherEvent, event))) {
                currentGroup.push(event);
            } else {
                positionedEvents.push(...this.calculatePositionsForEventGroup(currentGroup));
                currentGroup = [event];
            }
        }
        positionedEvents.push(...this.calculatePositionsForEventGroup(currentGroup));

        return positionedEvents;
    }

    private calculatePositionsForEventGroup(currentGroup: CalendarEvent[]): CalendarEventAndPosition[] {
        const pixelsPerMinute = CalendarWeekPresentationComponent.HOUR_SEGMENT_HEIGHT_IN_PIXEL / 60;

        const horizontalMarginAsPercentage = 1.5;
        const gapBetweenEventsAsPercentage = 1.5;
        const totalGapBetweenEventsAsPercentage = currentGroup.length > 1 ? gapBetweenEventsAsPercentage * (currentGroup.length - 1) : 0;

        const availableWidthAsPercentage = 100 - totalGapBetweenEventsAsPercentage - 2 * horizontalMarginAsPercentage;
        const eventWidthAsPercentage = availableWidthAsPercentage / currentGroup.length;

        const defaultHeightInPixel = 24;
        return currentGroup.map((event, index) => {
            const top = event.startDate.diff(event.startDate.startOf('day'), 'minute') * pixelsPerMinute;
            const height = event.endDate ? event.endDate.diff(event.startDate, 'minute') * pixelsPerMinute : defaultHeightInPixel;
            const left = horizontalMarginAsPercentage + index * (eventWidthAsPercentage + gapBetweenEventsAsPercentage);

            const pos: PositionInfo = { top, height, left, width: eventWidthAsPercentage };
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
