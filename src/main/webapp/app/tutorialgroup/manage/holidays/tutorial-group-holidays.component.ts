import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { finalize } from 'rxjs/operators';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonDirective, TumUiConfirmDialogComponent, TumUiConfirmationService } from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';
import { TutorialGroupFreePeriodService } from 'app/tutorialgroup/manage/service/tutorial-group-free-period.service';
import { DAY_KEY_FORMAT, HolidayOccurrence, groupByDay, inCourseZone, toOccurrences } from 'app/tutorialgroup/manage/holidays/holiday.model';
import { HolidayMonthGridComponent } from 'app/tutorialgroup/manage/holidays/holiday-month-grid/holiday-month-grid.component';
import { HolidayListComponent, HolidayListFilter } from 'app/tutorialgroup/manage/holidays/holiday-list/holiday-list.component';
import { HolidayDialogComponent, HolidaySubmission } from 'app/tutorialgroup/manage/holidays/holiday-dialog/holiday-dialog.component';

/** The last minute of the day, which is how a whole-day holiday is stored. */
const END_OF_DAY = { hour: 23, minute: 59 };

/**
 * The holidays of a course: a month calendar of everything that is cancelled, and the list of holidays beside it.
 *
 * Replaces the page that split holidays into free days, free periods and periods within a day. The distinction was a
 * property of the stored span rather than something an instructor thinks in, and it forced three tables over what is one
 * list of days. A holiday is now a day, optionally narrowed to a span within it.
 */
@Component({
    selector: 'jhi-tutorial-group-holidays',
    templateUrl: './tutorial-group-holidays.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    // Scoped to this page, so a confirmation raised here can only be answered by the dialog this page renders.
    providers: [TumUiConfirmationService],
    imports: [
        TumUiConfirmDialogComponent,
        FaIconComponent,
        TranslateDirective,
        CourseTitleBarActionsDirective,
        TumUiButtonDirective,
        HolidayMonthGridComponent,
        HolidayListComponent,
        HolidayDialogComponent,
    ],
})
export class TutorialGroupHolidaysComponent {
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly configurationService = inject(TutorialGroupsConfigurationService);
    private readonly freePeriodService = inject(TutorialGroupFreePeriodService);
    private readonly confirmationService = inject(TumUiConfirmationService);
    private readonly translateService = inject(TranslateService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faPlus = faPlus;

    protected readonly course = signal<Course | undefined>(undefined);
    protected readonly configuration = signal<TutorialGroupsConfiguration | undefined>(undefined);
    protected readonly freePeriods = signal<TutorialGroupFreePeriod[]>([]);
    protected readonly sessionCountsByDay = signal<Map<string, number>>(new Map());
    protected readonly isLoading = signal(false);
    protected readonly isSaving = signal(false);

    protected readonly filter = signal<HolidayListFilter>('upcoming');
    protected readonly dialogVisible = signal(false);
    protected readonly editedHoliday = signal<HolidayOccurrence | undefined>(undefined);
    protected readonly dialogInitialDay = signal<dayjs.Dayjs | undefined>(undefined);
    /** The day the dialog is currently showing, so the session warning follows the date the reader picks. */
    protected readonly dialogSelectedDay = signal<dayjs.Dayjs | undefined>(undefined);

    private readonly timeZone = computed(() => this.course()?.timeZone);

    /** Today in the course's zone: a holiday cancels a day of the course, not a day of whoever is reading. */
    protected readonly today = computed(() => inCourseZone(dayjs(), this.timeZone()).startOf('day'));
    protected readonly displayedMonth = signal(dayjs().startOf('month'));

    protected readonly occurrences = computed(() => toOccurrences(this.freePeriods(), this.timeZone()));
    protected readonly holidaysByDay = computed(() => groupByDay(this.occurrences()));

    protected readonly sessionCountForDialogDay = computed(() => {
        const day = this.dialogSelectedDay();
        return day ? (this.sessionCountsByDay().get(day.format(DAY_KEY_FORMAT)) ?? 0) : 0;
    });

    constructor() {
        this.activatedRoute.data.pipe(takeUntilDestroyed()).subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
                // Start on the month the reader is in, expressed in the course's zone.
                this.displayedMonth.set(inCourseZone(dayjs(), course.timeZone).startOf('month'));
                this.loadConfiguration();
            }
        });
    }

    private loadConfiguration(): void {
        const courseId = this.course()?.id;
        if (courseId === undefined) {
            return;
        }
        this.isLoading.set(true);
        this.configurationService
            .getOneOfCourse(courseId)
            .pipe(
                finalize(() => this.isLoading.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (response) => {
                    const configuration = response.body ? tutorialGroupsConfigurationEntityFromDto(response.body) : undefined;
                    this.configuration.set(configuration);
                    this.freePeriods.set(configuration?.tutorialGroupFreePeriods ?? []);
                    this.loadSessionCounts();
                },
                error: (response: HttpErrorResponse) => onError(this.alertService, response),
            });
    }

    /**
     * Loads the session counts for the displayed month.
     *
     * The span is the whole grid rather than the month, so the days of the neighbouring months the grid shows carry
     * their counts too instead of appearing empty.
     */
    private loadSessionCounts(): void {
        const courseId = this.course()?.id;
        if (courseId === undefined) {
            return;
        }
        const from = this.displayedMonth().startOf('month').startOf('isoWeek');
        const to = this.displayedMonth().endOf('month').endOf('isoWeek');
        this.freePeriodService
            .getSessionCounts(courseId, from, to)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (counts) => this.sessionCountsByDay.set(new Map(counts.map((count) => [count.date, count.count]))),
                error: (response: HttpErrorResponse) => onError(this.alertService, response),
            });
    }

    protected onMonthChange(month: dayjs.Dayjs): void {
        this.displayedMonth.set(month);
        this.loadSessionCounts();
    }

    protected openCreateDialog(day?: dayjs.Dayjs): void {
        this.editedHoliday.set(undefined);
        const initialDay = day ?? this.today();
        this.dialogInitialDay.set(initialDay);
        this.dialogSelectedDay.set(initialDay);
        this.dialogVisible.set(true);
    }

    protected openEditDialog(occurrence: HolidayOccurrence): void {
        this.editedHoliday.set(occurrence);
        this.dialogInitialDay.set(undefined);
        this.dialogSelectedDay.set(occurrence.day);
        this.dialogVisible.set(true);
    }

    protected onSave(submission: HolidaySubmission): void {
        const courseId = this.course()?.id;
        const configurationId = this.configuration()?.id;
        if (courseId === undefined || configurationId === undefined) {
            return;
        }

        const start = submission.wholeDay ? submission.day.startOf('day') : this.applyTime(submission.day, submission.startTime!);
        const end = submission.wholeDay
            ? submission.day.set('hour', END_OF_DAY.hour).set('minute', END_OF_DAY.minute).startOf('minute')
            : this.applyTime(submission.day, submission.endTime!);

        // The server reads these as wall-clock values in the course's zone, so they are sent without an offset.
        const payload = { startDate: start.toDate(), endDate: end.toDate(), reason: submission.reason };
        const edited = this.editedHoliday();

        this.isSaving.set(true);
        const request = edited?.period.id
            ? this.freePeriodService.update(courseId, configurationId, edited.period.id, payload)
            : this.freePeriodService.create(courseId, configurationId, payload);

        request
            .pipe(
                finalize(() => this.isSaving.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: () => {
                    this.dialogVisible.set(false);
                    this.loadConfiguration();
                },
                error: (response: HttpErrorResponse) => onError(this.alertService, response),
            });
    }

    protected onDelete(occurrence: HolidayOccurrence): void {
        const courseId = this.course()?.id;
        const configurationId = this.configuration()?.id;
        const freePeriodId = occurrence.period.id;
        if (courseId === undefined || configurationId === undefined || freePeriodId === undefined) {
            return;
        }
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.pages.tutorialFreePeriodsManagement.deleteDialog.header'),
            // A row that spans several days is deleted whole, so the confirmation names what actually goes.
            message: this.translateService.instant(
                occurrence.partOfMultiDayPeriod
                    ? 'artemisApp.pages.tutorialFreePeriodsManagement.deleteDialog.multiDayQuestion'
                    : 'artemisApp.pages.tutorialFreePeriodsManagement.deleteDialog.question',
                { reason: occurrence.reason },
            ),
            acceptLabel: this.translateService.instant('entity.action.delete'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptSeverity: 'danger',
            accept: () => {
                this.freePeriodService
                    .delete(courseId, configurationId, freePeriodId)
                    .pipe(takeUntilDestroyed(this.destroyRef))
                    .subscribe({
                        next: () => this.loadConfiguration(),
                        error: (response: HttpErrorResponse) => onError(this.alertService, response),
                    });
            },
        });
    }

    private applyTime(day: dayjs.Dayjs, time: string): dayjs.Dayjs {
        const [hour, minute] = time.split(':').map(Number);
        return day.startOf('day').set('hour', hour).set('minute', minute);
    }
}
