import { Component, OnInit } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import isoWeek from 'dayjs/plugin/isoWeek';
import * as Utils from '../../util/calendar-util';

import { AllDayEventSectionComponent } from '../../shared/all-day-event-section/all-day-event-section.component';
import { TimedEventSectionComponent } from '../../shared/timed-event-section/timed-event-section.component';
import { DayBadgeComponent } from '../../shared/day-badge/day-badge.component';

dayjs.extend(isoWeek);
@Component({
    selector: 'app-desktop-month-manual',
    imports: [DayBadgeComponent, AllDayEventSectionComponent, TimedEventSectionComponent],
    templateUrl: './calendar-desktop-week.component.html',
    styleUrl: './calendar-desktop-week.component.scss',
})
export class CalendarDesktopWeekComponent implements OnInit {
    readonly utils = Utils;
    currentDay = dayjs();
    weekDays: Dayjs[] = [];
    languageIsGerman = false;

    ngOnInit(): void {
        this.generateWeekDays();
    }

    private generateWeekDays(): void {
        const startOfWeek = this.currentDay.startOf('isoWeek');
        this.weekDays = Array.from({ length: 7 }, (_, i) => startOfWeek.add(i, 'day'));
    }

    range(size: number): number[] {
        return Array.from({ length: size }, (_, i) => i);
    }

    getDayName(day: dayjs.Dayjs): string {
        const locale = this.languageIsGerman ? 'de' : 'en';
        return day.locale(locale).format('dd');
    }
}
