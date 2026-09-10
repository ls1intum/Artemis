import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { faEllipsis } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonDirective, TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective, TumUiSelectButtonComponent, TumUiTagComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { HolidayOccurrence } from 'app/tutorialgroup/manage/holidays/holiday.model';

/** Which holidays the list shows. */
export type HolidayListFilter = 'upcoming' | 'all';

/** A holiday as the list renders it, with the labels precomputed so the template stays declarative. */
interface HolidayListEntry {
    readonly occurrence: HolidayOccurrence;
    readonly key: string;
    readonly monthLabel: string;
    readonly dayOfMonth: number;
    readonly weekdayLabel: string;
    readonly timeLabel: string;
    readonly sessionCount: number;
}

/**
 * The holidays of a course as a list beside the calendar.
 *
 * Shows upcoming holidays by default, because the reason to open this page is usually to plan the rest of the term, and
 * says how many past ones that hides rather than dropping them silently.
 */
@Component({
    selector: 'jhi-holiday-list',
    templateUrl: './holiday-list.component.html',
    styleUrl: './holiday-list.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiButtonDirective,
        TumUiMenuComponent,
        TumUiMenuItemDirective,
        TumUiMenuTriggerDirective,
        TumUiSelectButtonComponent,
        TumUiTagComponent,
    ],
})
export class HolidayListComponent {
    readonly holidays = input.required<readonly HolidayOccurrence[]>();
    readonly sessionCountsByDay = input.required<Map<string, number>>();
    /** Today in the course's time zone, so "upcoming" is measured against the course's day. */
    readonly today = input.required<dayjs.Dayjs>();
    readonly filter = input.required<HolidayListFilter>();

    readonly filterChange = output<HolidayListFilter>();
    readonly editRequested = output<HolidayOccurrence>();
    readonly deleteRequested = output<HolidayOccurrence>();

    private readonly translateService = inject(TranslateService);
    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly faEllipsis = faEllipsis;

    protected readonly filterOptions = computed(() => [
        { label: this.translateService.instant('artemisApp.pages.tutorialFreePeriodsManagement.filter.upcoming'), value: 'upcoming' },
        { label: this.translateService.instant('artemisApp.pages.tutorialFreePeriodsManagement.filter.all'), value: 'all' },
    ]);

    /** Holidays strictly before today, which the upcoming filter leaves out. A holiday today still counts as upcoming. */
    protected readonly pastCount = computed(() => {
        const today = this.today().startOf('day');
        return this.holidays().filter((holiday) => holiday.day.isBefore(today, 'day')).length;
    });

    protected readonly entries = computed<HolidayListEntry[]>(() => {
        const locale = this.locale();
        const sessionCountsByDay = this.sessionCountsByDay();
        const today = this.today().startOf('day');
        const wholeDayLabel = this.translateService.instant('artemisApp.pages.tutorialFreePeriodsManagement.wholeDay');

        const visible = this.filter() === 'all' ? this.holidays() : this.holidays().filter((holiday) => !holiday.day.isBefore(today, 'day'));

        return visible.map((occurrence) => {
            const localizedDay = occurrence.day.locale(locale);
            return {
                occurrence,
                key: `${occurrence.period.id}-${occurrence.dayKey}`,
                monthLabel: localizedDay.format('MMM'),
                dayOfMonth: occurrence.day.date(),
                weekdayLabel: localizedDay.format('dddd'),
                timeLabel: occurrence.wholeDay ? wholeDayLabel : `${occurrence.startTime}–${occurrence.endTime}`,
                sessionCount: sessionCountsByDay.get(occurrence.dayKey) ?? 0,
            };
        });
    });

    protected onFilterSelected(value: unknown): void {
        // The segmented control allows an empty selection, which here would leave the list with no filter at all.
        if (value === 'upcoming' || value === 'all') {
            this.filterChange.emit(value);
        }
    }
}
