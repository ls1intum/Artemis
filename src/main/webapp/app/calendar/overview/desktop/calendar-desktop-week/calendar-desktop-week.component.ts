import { Component, computed, input } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import * as Utils from 'app/calendar/shared/util/calendar-util';

import { AllDayEventSectionComponent } from '../../../shared/all-day-event-section/all-day-event-section.component';
import { TimedEventSectionComponent } from '../../../shared/timed-event-section/timed-event-section.component';
import { DayBadgeComponent } from '../../../shared/day-badge/day-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

// TODO: where to  move this?
dayjs.extend(isoWeek);

@Component({
    selector: 'calendar-desktop-week',
    imports: [DayBadgeComponent, AllDayEventSectionComponent, TimedEventSectionComponent, ArtemisTranslatePipe],
    templateUrl: './calendar-desktop-week.component.html',
    styleUrl: './calendar-desktop-week.component.scss',
})
export class CalendarDesktopWeekComponent {
    firstDayOfCurrentMonth = input.required<Dayjs>();
    firstDayOfCurrentWeek = input.required<Dayjs>();

    readonly utils = Utils;
    readonly weekDays = computed(() => this.computeWeekDaysFrom(this.firstDayOfCurrentWeek()));

    private computeWeekDaysFrom(firstDayOfWeek: Dayjs): Dayjs[] {
        return Array.from({ length: 7 }, (_, i) => firstDayOfWeek.add(i, 'day'));
    }
}
