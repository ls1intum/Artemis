import { Component } from '@angular/core';
import dayjs from 'dayjs/esm';
import isoWeek from 'dayjs/esm/plugin/isoWeek';
import isSameOrBefore from 'dayjs/esm/plugin/isSameOrBefore';
import { CalendarDesktopOverviewComponent } from 'app/core/calendar/desktop/overview/calendar-desktop-overview.component';
import { CalendarMobileOverviewComponent } from 'app/core/calendar/mobile/overview/calendar-mobile-overview';

dayjs.extend(isoWeek);
dayjs.extend(isSameOrBefore);

@Component({
    selector: 'calendar-overview',
    imports: [CalendarDesktopOverviewComponent, CalendarMobileOverviewComponent],
    templateUrl: './calendar-overview.component.html',
    styleUrl: './calendar-overview.component.scss',
})
export class CalendarOverviewComponent {}
