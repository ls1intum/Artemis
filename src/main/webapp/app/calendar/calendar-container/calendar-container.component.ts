import { Component, inject } from '@angular/core';
import { CalendarDesktopOverviewComponent } from 'app/calendar/desktop/overview/calendar-desktop-overview.component';
import { CalendarMobileOverviewComponent } from 'app/calendar/mobile/overview/calendar-mobile-overview.component';
import { BreakpointObserver } from '@angular/cdk/layout';
import { getIsMobileSignal } from 'app/foundation/util/global.utils';

@Component({
    selector: 'jhi-calendar-container',
    imports: [CalendarDesktopOverviewComponent, CalendarMobileOverviewComponent],
    templateUrl: './calendar-container.component.html',
})
export class CalendarContainerComponent {
    private breakpointObserver = inject(BreakpointObserver);

    readonly isMobile = getIsMobileSignal(this.breakpointObserver);
}
