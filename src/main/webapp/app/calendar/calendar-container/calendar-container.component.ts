import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { CalendarDesktopOverviewComponent } from 'app/calendar/desktop/overview/calendar-desktop-overview.component';
import { CalendarMobileOverviewComponent } from 'app/calendar/mobile/overview/calendar-mobile-overview.component';
import { BreakpointObserver } from '@angular/cdk/layout';
import { CalendarViewStateService } from 'app/calendar/shared/service/calendar-view-state.service';

/**
 * Switches between the two calendar overviews.
 *
 * The breakpoint is the calendar's own rather than the app-wide handset one, which in landscape is 960px — far wider
 * than this page needs. With its controls down to icons the desktop bar holds well below that; the handover sits
 * just above the width where the month title starts to truncate, with room to spare rather than at the exact edge.
 */
const CALENDAR_MOBILE_BREAKPOINT = '(max-width: 670px)';

@Component({
    selector: 'jhi-calendar-container',
    imports: [CalendarDesktopOverviewComponent, CalendarMobileOverviewComponent],
    templateUrl: './calendar-container.component.html',
    // Held here, so it outlives the overview the resize destroys but still resets when the calendar is left.
    providers: [CalendarViewStateService],
})
export class CalendarContainerComponent {
    private breakpointObserver = inject(BreakpointObserver);

    readonly isMobile = toSignal(this.breakpointObserver.observe(CALENDAR_MOBILE_BREAKPOINT).pipe(map((result) => result.matches)), {
        initialValue: this.breakpointObserver.isMatched(CALENDAR_MOBILE_BREAKPOINT),
    });
}
