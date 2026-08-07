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
 * The breakpoint is the calendar's own rather than the app-wide handset one: this page carries more controls in its
 * title bar than any other, and they are already down to icons before the handset breakpoint (960px in landscape) is
 * reached. Handing over to the mobile overview a little earlier keeps that row readable.
 */
const CALENDAR_MOBILE_BREAKPOINT = '(max-width: 800px)';

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
