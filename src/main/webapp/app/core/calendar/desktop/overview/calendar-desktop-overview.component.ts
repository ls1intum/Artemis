import { Component, computed, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import 'dayjs/esm/locale/en';
import 'dayjs/esm/locale/de';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarDesktopMonthPresentationComponent } from 'app/core/calendar/desktop/month-presentation/calendar-desktop-month-presentation.component';
import { CalendarDesktopWeekPresentationComponent } from 'app/core/calendar/desktop/week-presentation/calendar-desktop-week-presentation.component';
import { CalendarEventFilterComponent } from 'app/core/calendar/shared/calendar-event-filter/calendar-event-filter.component';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { CalendarOverviewComponent } from 'app/core/calendar/shared/calendar-overview/calendar-overview-component.directive';

type Presentation = 'week' | 'month';

@Component({
    selector: 'jhi-calendar-desktop-overview',
    imports: [
        CalendarDesktopMonthPresentationComponent,
        CalendarDesktopWeekPresentationComponent,
        CalendarEventFilterComponent,
        NgClass,
        FaIconComponent,
        TranslateDirective,
        CalendarSubscriptionPopoverComponent,
    ],
    templateUrl: './calendar-desktop-overview.component.html',
    styleUrl: './calendar-desktop-overview.component.scss',
})
export class CalendarDesktopOverviewComponent extends CalendarOverviewComponent {
    presentation = signal<Presentation>('month');
    firstDateOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDateOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));
    monthDescription = computed<string>(() => this.computeMonthDescription(this.locale(), this.presentation(), this.firstDateOfCurrentMonth(), this.firstDateOfCurrentWeek()));

    goToPrevious(): void {
        if (this.presentation() === 'week') {
            this.firstDateOfCurrentWeek.update((current) => current.subtract(1, 'week'));
            const firstDayOfCurrentWeek = this.firstDateOfCurrentWeek();
            const firstDayOfCurrentMonth = this.firstDateOfCurrentMonth();
            if (firstDayOfCurrentWeek.isBefore(firstDayOfCurrentMonth)) {
                this.firstDateOfCurrentMonth.update((current) => current.subtract(1, 'month'));
            }
        } else {
            this.firstDateOfCurrentMonth.update((current) => current.subtract(1, 'month'));
            this.firstDateOfCurrentWeek.set(this.firstDateOfCurrentMonth().startOf('isoWeek'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToNext(): void {
        if (this.presentation() === 'week') {
            this.firstDateOfCurrentWeek.update((current) => current.add(1, 'week'));
            const endOfCurrentWeek = this.firstDateOfCurrentWeek().endOf('isoWeek');
            const endOfCurrentMonth = this.firstDateOfCurrentMonth().endOf('month');
            if (endOfCurrentWeek.isAfter(endOfCurrentMonth)) {
                this.firstDateOfCurrentMonth.update((current) => current.add(1, 'month'));
            }
        } else {
            this.firstDateOfCurrentMonth.update((current) => current.add(1, 'month'));
            this.firstDateOfCurrentWeek.set(this.firstDateOfCurrentMonth().startOf('isoWeek'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToToday(): void {
        this.firstDateOfCurrentMonth.set(dayjs().startOf('month'));
        this.firstDateOfCurrentWeek.set(dayjs().startOf('isoWeek'));
        this.loadEventsForCurrentMonth();
    }

    private computeMonthDescription(currentLocale: string, presentation: Presentation, firstDayOfCurrentMonth: Dayjs, firstDayOfCurrentWeek: Dayjs): string {
        if (presentation === 'month') {
            return firstDayOfCurrentMonth.locale(currentLocale).format('MMMM YYYY');
        } else {
            const localizedFirstDayOfCurrentWeek = firstDayOfCurrentWeek.locale(currentLocale);
            const localizedLastDayOfCurrentWeek = firstDayOfCurrentWeek.endOf('isoWeek').locale(currentLocale);
            if (localizedLastDayOfCurrentWeek.isSame(firstDayOfCurrentWeek, 'month')) {
                return localizedFirstDayOfCurrentWeek.format('MMMM YYYY');
            } else {
                return localizedFirstDayOfCurrentWeek.format('MMMM') + ' | ' + localizedLastDayOfCurrentWeek.format('MMMM YYYY');
            }
        }
    }
}
