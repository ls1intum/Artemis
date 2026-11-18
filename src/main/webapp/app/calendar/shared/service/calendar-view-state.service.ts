import { Injectable, signal } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';

/**
 * The period the calendar is currently showing.
 *
 * It lives outside the two overview components because the container swaps between them whenever the viewport crosses
 * the mobile breakpoint: desktop and mobile are separate components, so a month held in either is lost the moment the
 * window is resized past it, and the user is thrown back to the current month.
 *
 * Provided by the container rather than in the root injector, so the period still starts at today's month whenever the
 * calendar itself is opened.
 */
@Injectable()
export class CalendarViewStateService {
    /** First day of the month both overviews display, and the month whose events are loaded. */
    readonly firstDateOfDisplayedMonth = signal<Dayjs>(dayjs().startOf('month'));

    /** First day of the week the desktop overview displays in its week presentation. */
    readonly firstDateOfDisplayedWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));
}
