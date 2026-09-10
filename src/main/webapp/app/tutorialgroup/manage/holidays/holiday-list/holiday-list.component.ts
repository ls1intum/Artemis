import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonDirective, TumUiTagComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { DAY_KEY_FORMAT, Holiday } from 'app/tutorialgroup/manage/holidays/holiday.model';

/** Which holidays the list shows. */
export type HolidayListFilter = 'upcoming' | 'all';

/** A holiday as the list renders it, with the labels precomputed so the template stays declarative. */
interface HolidayListEntry {
    readonly holiday: Holiday;
    readonly key: string;
    readonly monthLabel: string;
    readonly dayOfMonth: number;
    /** Weekday for a single day, or the span for a holiday covering several. */
    readonly whenLabel: string;
    readonly timeLabel: string;
    readonly sessionCount: number;
}

/**
 * The holidays of a course as a list beside the calendar.
 *
 * One row per holiday rather than per day: a two-week break is one thing the reader created and one thing they will
 * edit or delete, so listing it fourteen times would bury everything else. The calendar still marks each of its days.
 *
 * Shows upcoming holidays by default, because the reason to open this page is usually to plan the rest of the term, and
 * says how many past ones that hides.
 */
@Component({
    selector: 'jhi-holiday-list',
    templateUrl: './holiday-list.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumUiButtonDirective, TumUiTagComponent, TumUiTooltipDirective],
})
export class HolidayListComponent {
    readonly holidays = input.required<readonly Holiday[]>();
    readonly sessionCountsByDay = input.required<Map<string, number>>();
    /** Today in the course's time zone, so "upcoming" is measured against the course's day. */
    readonly today = input.required<dayjs.Dayjs>();
    readonly filter = input.required<HolidayListFilter>();

    readonly filterChange = output<HolidayListFilter>();
    readonly editRequested = output<Holiday>();
    readonly deleteRequested = output<Holiday>();

    private readonly translateService = inject(TranslateService);
    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly faWrench = faWrench;
    protected readonly faTrash = faTrash;

    /** Two plain buttons rather than a form control: the filter is component state, so nothing here needs ngModel. */
    protected readonly filters: readonly HolidayListFilter[] = ['upcoming', 'all'];

    /** Holidays that ended before today, which the upcoming filter leaves out. One running today still counts. */
    protected readonly pastCount = computed(() => {
        const today = this.today().startOf('day');
        return this.holidays().filter((holiday) => holiday.end.isBefore(today, 'day')).length;
    });

    protected readonly entries = computed<HolidayListEntry[]>(() => {
        const locale = this.locale();
        const sessionCountsByDay = this.sessionCountsByDay();
        const today = this.today().startOf('day');
        const wholeDayLabel = this.translateService.instant('artemisApp.pages.tutorialFreePeriodsManagement.wholeDay');

        const visible = this.filter() === 'all' ? this.holidays() : this.holidays().filter((holiday) => !holiday.end.isBefore(today, 'day'));

        return visible.map((holiday) => {
            const start = holiday.start.locale(locale);
            const end = holiday.end.locale(locale);
            return {
                holiday,
                key: String(holiday.period.id),
                monthLabel: start.format('MMM'),
                dayOfMonth: holiday.start.date(),
                whenLabel: holiday.spansMultipleDays ? `${start.format('D MMM')} – ${end.format('D MMM')}` : start.format('dddd'),
                timeLabel: holiday.wholeDay ? wholeDayLabel : `${holiday.startTime}–${holiday.endTime}`,
                sessionCount: this.countSessionsIn(holiday, sessionCountsByDay),
            };
        });
    });

    /**
     * Adds up the sessions on every day the holiday touches.
     *
     * Counts are only loaded for the month the calendar shows, so a holiday reaching beyond it contributes what is
     * known and no more. The number is a hint about what a holiday cancels rather than a figure anything depends on.
     */
    private countSessionsIn(holiday: Holiday, sessionCountsByDay: Map<string, number>): number {
        let total = 0;
        let day = holiday.start.startOf('day');
        const lastDay = holiday.end.startOf('day');
        while (!day.isAfter(lastDay)) {
            total += sessionCountsByDay.get(day.format(DAY_KEY_FORMAT)) ?? 0;
            day = day.add(1, 'day');
        }
        return total;
    }
}
