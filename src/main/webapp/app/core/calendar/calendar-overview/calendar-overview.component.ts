import { Component, inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { CalendarDesktopOverviewComponent } from 'app/core/calendar/desktop/overview/calendar-desktop-overview.component';
import { CalendarMobileOverviewComponent } from 'app/core/calendar/mobile/overview/calendar-mobile-overview';
import { BreakpointObserver } from '@angular/cdk/layout';

@Component({
    selector: 'jhi-calendar-overview',
    imports: [CalendarDesktopOverviewComponent, CalendarMobileOverviewComponent],
    templateUrl: './calendar-overview.component.html',
    styleUrl: './calendar-overview.component.scss',
})
export class CalendarOverviewComponent {
    private breakpointObserver = inject(BreakpointObserver);

    readonly isMobile = toSignal(this.breakpointObserver.observe(['(max-width: 600px)']).pipe(map((result) => result.matches)), { initialValue: false });
}
