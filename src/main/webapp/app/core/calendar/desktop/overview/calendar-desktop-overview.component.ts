import { Component, computed, signal } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import 'dayjs/esm/locale/en';
import 'dayjs/esm/locale/de';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarDesktopMonthPresentationComponent } from 'app/core/calendar/desktop/month-presentation/calendar-desktop-month-presentation.component';
import { CalendarDesktopWeekPresentationComponent } from 'app/core/calendar/desktop/week-presentation/calendar-desktop-week-presentation.component';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { CalendarOverviewComponent } from 'app/core/calendar/shared/calendar-overview/calendar-overview-component.directive';
import { SelectButtonModule } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { MultiSelectModule } from 'primeng/multiselect';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

type Presentation = 'week' | 'month';

interface CalendarEventFilterOptionAndMetadata {
    option: CalendarEventFilterOption;
    name: string;
    colorClassName: string;
}

@Component({
    selector: 'jhi-calendar-desktop-overview',
    imports: [
        CalendarDesktopMonthPresentationComponent,
        CalendarDesktopWeekPresentationComponent,
        FaIconComponent,
        TranslateDirective,
        CalendarSubscriptionPopoverComponent,
        SelectButtonModule,
        FormsModule,
        ButtonModule,
        ButtonGroupModule,
        MultiSelectModule,
    ],
    templateUrl: './calendar-desktop-overview.component.html',
    styleUrl: './calendar-desktop-overview.component.scss',
})
export class CalendarDesktopOverviewComponent extends CalendarOverviewComponent {
    private static readonly FILTER_OPTION_NAME_KEY_MAP: Record<CalendarEventFilterOption, string> = {
        exerciseEvents: 'artemisApp.calendar.filterOption.exercises',
        lectureEvents: 'artemisApp.calendar.filterOption.lectures',
        tutorialEvents: 'artemisApp.calendar.filterOption.tutorials',
        examEvents: 'artemisApp.calendar.filterOption.exams',
    };
    private static readonly FILTER_OPTION_COLOR_CLASS_MAP: Record<CalendarEventFilterOption, string> = {
        exerciseEvents: 'exercise-chip',
        lectureEvents: 'lecture-chip',
        tutorialEvents: 'tutorial-chip',
        examEvents: 'exam-chip',
    };

    presentation = signal<Presentation>('month');
    firstDateOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDateOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));
    monthDescription = computed<string>(() => this.computeMonthDescription(this.locale(), this.presentation(), this.firstDateOfCurrentMonth(), this.firstDateOfCurrentWeek()));

    presentationOptions = computed<{ label: string; value: Presentation }[]>(() => {
        this.locale();
        return this.buildPresentationOptions();
    });
    filterComponentPlaceholder = computed(() => this.computeFilterComponentPlaceholder());
    selectedFilterOptions = computed<CalendarEventFilterOptionAndMetadata[]>(() => this.computeSelectedFilterOptions(this.calendarService.includedEventFilterOptions()));

    filterOptions = computed<CalendarEventFilterOptionAndMetadata[]>(() => this.buildFilterOptions());

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

    onSelectionOptionsChange(newSelectedOptionsAndMetadata: CalendarEventFilterOptionAndMetadata[]): void {
        const options = newSelectedOptionsAndMetadata.map((optionAndMetadata) => optionAndMetadata.option);
        this.calendarService.includedEventFilterOptions.set(options);
    }

    removeOption(option: CalendarEventFilterOption): void {
        this.calendarService.includedEventFilterOptions.update((currentOptions) => currentOptions.filter((otherOption) => otherOption !== option));
    }

    private addMetadataTo(option: CalendarEventFilterOption): CalendarEventFilterOptionAndMetadata {
        return {
            option: option,
            name: this.translateService.instant(CalendarDesktopOverviewComponent.FILTER_OPTION_NAME_KEY_MAP[option]),
            colorClassName: CalendarDesktopOverviewComponent.FILTER_OPTION_COLOR_CLASS_MAP[option],
        };
    }

    private buildPresentationOptions() {
        return [
            {
                label: this.translateService.instant('artemisApp.calendar.weekButtonLabel'),
                value: 'week' as const,
            },
            {
                label: this.translateService.instant('artemisApp.calendar.monthButtonLabel'),
                value: 'month' as const,
            },
        ];
    }

    private computeSelectedFilterOptions(includedOptions: CalendarEventFilterOption[]): CalendarEventFilterOptionAndMetadata[] {
        this.locale();
        return includedOptions.map((option) => this.addMetadataTo(option));
    }

    private buildFilterOptions(): CalendarEventFilterOptionAndMetadata[] {
        this.locale();
        return [
            this.addMetadataTo(CalendarEventFilterOption.LectureEvents),
            this.addMetadataTo(CalendarEventFilterOption.ExamEvents),
            this.addMetadataTo(CalendarEventFilterOption.ExerciseEvents),
            this.addMetadataTo(CalendarEventFilterOption.TutorialEvents),
        ];
    }

    private computeFilterComponentPlaceholder(): string {
        this.locale();
        return this.translateService.instant('artemisApp.calendar.filterComponentPlaceholder');
    }
}
