import { Component, inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { CalendarDesktopOverviewComponent } from 'app/core/calendar/desktop/overview/calendar-desktop-overview.component';
import { CalendarMobileOverviewComponent } from 'app/core/calendar/mobile/overview/calendar-mobile-overview';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { CalendarEventsPerDaySectionComponent } from 'app/core/calendar/shared/calendar-events-per-day-section/calendar-events-per-day-section.component';

@Component({
    selector: 'jhi-calendar-overview',
    imports: [CalendarDesktopOverviewComponent, CalendarMobileOverviewComponent],
    templateUrl: './calendar-overview.component.html',
    styleUrl: './calendar-overview.component.scss',
})
export class CalendarOverviewComponent {
    private static readonly INITIAL_SCROLL_HOURS_AFTER_MIDNIGHT = 7.5;
    static readonly INITIAL_SCROLL_POSITION = CalendarOverviewComponent.INITIAL_SCROLL_HOURS_AFTER_MIDNIGHT * CalendarEventsPerDaySectionComponent.HOUR_SEGMENT_HEIGHT_IN_PIXEL;

    private breakpointObserver = inject(BreakpointObserver);

    readonly isMobile = toSignal(this.breakpointObserver.observe([Breakpoints.Handset]).pipe(map((result) => result.matches)), { initialValue: false });
}
