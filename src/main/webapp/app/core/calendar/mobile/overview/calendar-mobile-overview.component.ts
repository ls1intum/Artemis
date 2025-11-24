import { Component, computed, signal } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarMobileMonthPresentationComponent } from 'app/core/calendar/mobile/month-presentation/calendar-mobile-month-presentation.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarMobileDayPresentationComponent } from 'app/core/calendar/mobile/day-presentation/calendar-mobile-day-presentation.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowUpFromBracket, faFilter } from '@fortawesome/free-solid-svg-icons';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { CalendarOverviewComponent } from 'app/core/calendar/shared/calendar-overview/calendar-overview-component.directive';
import { PopoverModule } from 'primeng/popover';
import { CheckboxModule } from 'primeng/checkbox';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';

@Component({
    selector: 'jhi-calendar-mobile-overview',
    imports: [
        CalendarMobileMonthPresentationComponent,
        CalendarMobileDayPresentationComponent,
        TranslateDirective,
        FaIconComponent,
        CalendarSubscriptionPopoverComponent,
        PopoverModule,
        CheckboxModule,
        FormsModule,
        ButtonModule,
    ],
    templateUrl: './calendar-mobile-overview.component.html',
    styleUrl: './calendar-mobile-overview.component.scss',
})
export class CalendarMobileOverviewComponent extends CalendarOverviewComponent {
    readonly faArrowUpFromBracket = faArrowUpFromBracket;
    readonly faFilter = faFilter;
    readonly CalendarEventFilterOption = CalendarEventFilterOption;

    firstDateOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    selectedDate = signal<Dayjs | undefined>(undefined);
    weekdayNameKeys = utils.getWeekdayNameKeys();
    monthDescription = computed<string>(() => this.firstDateOfCurrentMonth().locale(this.locale()).format('MMMM YYYY'));
    lectureFilterOptionSelected = computed(() => this.calendarService.includedEventFilterOptions().includes(CalendarEventFilterOption.LectureEvents));
    exerciseFilterOptionSelected = computed(() => this.calendarService.includedEventFilterOptions().includes(CalendarEventFilterOption.ExerciseEvents));
    tutorialFilterOptionSelected = computed(() => this.calendarService.includedEventFilterOptions().includes(CalendarEventFilterOption.TutorialEvents));
    examFilterOptionSelected = computed(() => this.calendarService.includedEventFilterOptions().includes(CalendarEventFilterOption.ExamEvents));

    toggleFilterOption(option: CalendarEventFilterOption) {
        if (this.calendarService.includedEventFilterOptions().includes(option)) {
            this.calendarService.includedEventFilterOptions.update((oldOptions) => oldOptions.filter((otherOption) => otherOption !== option));
        } else {
            this.calendarService.includedEventFilterOptions.update((oldOptions) => [...oldOptions, option]);
        }
    }

    onDateSelected(date: Dayjs): void {
        this.selectedDate.set(date);
    }

    unselectDate() {
        this.selectedDate.set(undefined);
    }

    goToPrevious(): void {
        if (this.selectedDate()) {
            this.selectedDate.update((oldDate) => oldDate!.subtract(1, 'day'));
            if (!this.selectedDate()!.isSame(this.firstDateOfCurrentMonth(), 'month')) {
                this.firstDateOfCurrentMonth.update((oldDate) => oldDate.subtract(1, 'month'));
            }
        } else {
            this.firstDateOfCurrentMonth.update((oldDate) => oldDate.subtract(1, 'month'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToNext(): void {
        if (this.selectedDate()) {
            this.selectedDate.update((oldDate) => oldDate!.add(1, 'day'));
            if (!this.selectedDate()!.isSame(this.firstDateOfCurrentMonth(), 'month')) {
                this.firstDateOfCurrentMonth.update((oldDate) => oldDate.add(1, 'month'));
            }
        } else {
            this.firstDateOfCurrentMonth.update((oldDate) => oldDate.add(1, 'month'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToToday(): void {
        const today = dayjs();
        if (this.selectedDate()) {
            this.selectedDate.set(today);
            this.firstDateOfCurrentMonth.set(today.startOf('month'));
        } else {
            this.firstDateOfCurrentMonth.set(today.startOf('month'));
        }
        this.loadEventsForCurrentMonth();
    }
}
