import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';
import dayjs, { Dayjs } from 'dayjs/esm';
import 'dayjs/esm/locale/en';
import 'dayjs/esm/locale/de';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CalendarDesktopMonthPresentationComponent } from 'app/core/calendar/desktop/month-presentation/calendar-desktop-month-presentation.component';
import { CalendarDesktopWeekPresentationComponent } from 'app/core/calendar/desktop/week-presentation/calendar-desktop-week-presentation.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarSubscriptionPopoverComponent } from 'app/core/calendar/shared/calendar-subscription-popover/calendar-subscription-popover.component';
import { SelectButtonModule } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { MultiSelectModule } from 'primeng/multiselect';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

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
export class CalendarDesktopOverviewComponent implements OnInit {
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

    private calendarService = inject(CalendarService);
    private translateService = inject(TranslateService);
    private activatedRoute = inject(ActivatedRoute);
    private currentLocale = toSignal(this.translateService.onLangChange.pipe(map((event) => event.lang)), { initialValue: this.translateService.currentLang });

    protected readonly faChevronRight = faChevronRight;
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faXmark = faXmark;

    selectedPresentation = signal<'week' | 'month'>('month');
    presentationOptions = computed<{ label: string; value: 'week' | 'month' }[]>(() => {
        this.currentLocale();
        return this.buildPresentationOptions();
    });
    filterComponentPlaceholder = computed(() => {
        this.currentLocale();
        return this.translateService.instant('artemisApp.calendar.filterComponentPlaceholder');
    });
    selectedFilterOptions = computed<CalendarEventFilterOptionAndMetadata[]>(() => {
        this.currentLocale();
        return this.computeSelectedFilterOptions(this.calendarService.includedEventFilterOptions());
    });
    filterOptions = computed<CalendarEventFilterOptionAndMetadata[]>(() => {
        this.currentLocale();
        return this.buildFilterOptions();
    });
    firstDayOfCurrentMonth = signal<Dayjs>(dayjs().startOf('month'));
    firstDayOfCurrentWeek = signal<Dayjs>(dayjs().startOf('isoWeek'));
    calendarSubscriptionToken = this.calendarService.subscriptionToken;
    isLoading = signal<boolean>(false);
    currentCourseId = toSignal(
        this.activatedRoute.parent!.paramMap.pipe(
            map((params) => {
                const id = params.get('courseId');
                return id ? +id : undefined;
            }),
        ),
        { initialValue: undefined },
    );

    constructor() {
        effect(() => {
            const courseId = this.currentCourseId();
            if (courseId) {
                this.loadEventsForCurrentMonth();
            }
        });
    }

    ngOnInit(): void {
        this.calendarService.loadSubscriptionToken().subscribe();
    }

    goToPrevious(): void {
        if (this.selectedPresentation() === 'week') {
            this.firstDayOfCurrentWeek.update((current) => current.subtract(1, 'week'));
            const firstDayOfCurrentWeek = this.firstDayOfCurrentWeek();
            const firstDayOfCurrentMonth = this.firstDayOfCurrentMonth();
            if (firstDayOfCurrentWeek.isBefore(firstDayOfCurrentMonth)) {
                this.firstDayOfCurrentMonth.update((current) => current.subtract(1, 'month'));
            }
        } else {
            this.firstDayOfCurrentMonth.update((current) => current.subtract(1, 'month'));
            this.firstDayOfCurrentWeek.set(this.firstDayOfCurrentMonth().startOf('isoWeek'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToNext(): void {
        if (this.selectedPresentation() === 'week') {
            this.firstDayOfCurrentWeek.update((current) => current.add(1, 'week'));
            const endOfCurrentWeek = this.firstDayOfCurrentWeek().endOf('isoWeek');
            const endOfCurrentMonth = this.firstDayOfCurrentMonth().endOf('month');
            if (endOfCurrentWeek.isAfter(endOfCurrentMonth)) {
                this.firstDayOfCurrentMonth.update((current) => current.add(1, 'month'));
            }
        } else {
            this.firstDayOfCurrentMonth.update((current) => current.add(1, 'month'));
            this.firstDayOfCurrentWeek.set(this.firstDayOfCurrentMonth().startOf('isoWeek'));
        }
        this.loadEventsForCurrentMonth();
    }

    goToToday(): void {
        this.firstDayOfCurrentMonth.set(dayjs().startOf('month'));
        this.firstDayOfCurrentWeek.set(dayjs().startOf('isoWeek'));
        this.loadEventsForCurrentMonth();
    }

    getMonthDescription(): string {
        const currentLocale = this.currentLocale();
        if (this.selectedPresentation() === 'month') {
            return this.firstDayOfCurrentMonth().locale(currentLocale).format('MMMM YYYY');
        } else {
            const firstDayOfCurrentWeek = this.firstDayOfCurrentWeek().locale(currentLocale);
            const lastDayOfCurrentWeek = this.firstDayOfCurrentWeek().endOf('isoWeek').locale(currentLocale);
            if (lastDayOfCurrentWeek.isSame(firstDayOfCurrentWeek, 'month')) {
                return firstDayOfCurrentWeek.format('MMMM YYYY');
            } else {
                return firstDayOfCurrentWeek.format('MMMM') + ' | ' + lastDayOfCurrentWeek.format('MMMM YYYY');
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

    private loadEventsForCurrentMonth(): void {
        const courseId = this.currentCourseId();
        if (!courseId) return;
        this.isLoading.set(true);
        this.calendarService
            .loadEventsForCurrentMonth(courseId, this.firstDayOfCurrentMonth())
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe();
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
        return includedOptions.map((option) => this.addMetadataTo(option));
    }

    private buildFilterOptions(): CalendarEventFilterOptionAndMetadata[] {
        return [
            this.addMetadataTo(CalendarEventFilterOption.LectureEvents),
            this.addMetadataTo(CalendarEventFilterOption.ExamEvents),
            this.addMetadataTo(CalendarEventFilterOption.ExerciseEvents),
            this.addMetadataTo(CalendarEventFilterOption.TutorialEvents),
        ];
    }
}
