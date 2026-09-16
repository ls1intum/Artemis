import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { faTrash, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonDirective, TumUiTagComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { Holiday } from 'app/tutorialgroup/manage/holidays/holiday.model';

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
    /**
     * The full date, for screen readers.
     *
     * The date block beside the row is hidden from the accessibility tree because it splits the month from the day, and
     * the visible weekday alone would leave a listener without a date at all.
     */
    readonly accessibleDate: string;
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
    /** Sessions each holiday covers, keyed by free period id and counted by overlap rather than by whole days. */
    readonly sessionCountsByHoliday = input.required<Map<number, number>>();
    /** Today in the course's time zone, so "upcoming" is measured against the course's day. */
    readonly today = input.required<dayjs.Dayjs>();
    readonly filter = input.required<HolidayListFilter>();

    readonly filterChange = output<HolidayListFilter>();
    /** Carries the button pressed, so the form can point at the row it was asked for rather than at the page. */
    readonly editRequested = output<{ holiday: Holiday; origin: HTMLElement }>();

    /** Narrows the event target here rather than in the template, where `currentTarget` is only an `EventTarget`. */
    protected requestEdit(holiday: Holiday, event: Event): void {
        this.editRequested.emit({ holiday, origin: event.currentTarget as HTMLElement });
    }
    readonly deleteRequested = output<Holiday>();

    private readonly translateService = inject(TranslateService);
    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly faWrench = faWrench;
    protected readonly faTrash = faTrash;

    /**
     * Two plain buttons rather than a form control: the filter is component state, so nothing here needs ngModel.
     *
     * The keys are spelled out rather than built from the value, so a search for either one finds this.
     */
    protected readonly filters: readonly { value: HolidayListFilter; labelKey: string }[] = [
        { value: 'upcoming', labelKey: 'artemisApp.pages.tutorialFreePeriodsManagement.filter.upcoming' },
        { value: 'all', labelKey: 'artemisApp.pages.tutorialFreePeriodsManagement.filter.all' },
    ];

    /** Holidays whose last covered day is behind today, which the upcoming filter leaves out. One running today counts. */
    protected readonly pastCount = computed(() => {
        const today = this.today().startOf('day');
        return this.holidays().filter((holiday) => holiday.lastDay.isBefore(today, 'day')).length;
    });

    protected readonly entries = computed<HolidayListEntry[]>(() => {
        const locale = this.locale();
        const sessionCountsByHoliday = this.sessionCountsByHoliday();
        const today = this.today().startOf('day');
        const wholeDayLabel = this.translateService.instant('artemisApp.pages.tutorialFreePeriodsManagement.wholeDay');

        const visible = this.filter() === 'all' ? this.holidays() : this.holidays().filter((holiday) => !holiday.lastDay.isBefore(today, 'day'));

        return visible.map((holiday) => {
            const start = holiday.start.locale(locale);
            // The last day it covers rather than its exclusive end, so a span finishing at midnight is not named a day long.
            const end = holiday.lastDay.locale(locale);
            return {
                holiday,
                key: String(holiday.period.id),
                monthLabel: start.format('MMM'),
                dayOfMonth: holiday.start.date(),
                whenLabel: holiday.spansMultipleDays ? `${start.format('D MMM')} – ${end.format('D MMM')}` : start.format('dddd'),
                accessibleDate: holiday.spansMultipleDays ? `${start.format('LL')} – ${end.format('LL')}` : start.format('LL'),
                timeLabel: holiday.wholeDay ? wholeDayLabel : `${holiday.startTime}–${holiday.endTime}`,
                sessionCount: sessionCountsByHoliday.get(holiday.period.id!) ?? 0,
            };
        });
    });
}
