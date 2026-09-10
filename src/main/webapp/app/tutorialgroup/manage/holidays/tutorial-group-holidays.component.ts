import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { EMPTY, Subject } from 'rxjs';
import { catchError, debounceTime, finalize, switchMap } from 'rxjs/operators';
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
import { Holiday, inCourseZone, toHolidays } from 'app/tutorialgroup/manage/holidays/holiday.model';
import { HolidayMonthGridComponent } from 'app/tutorialgroup/manage/holidays/holiday-month-grid/holiday-month-grid.component';
import { HolidayListComponent, HolidayListFilter } from 'app/tutorialgroup/manage/holidays/holiday-list/holiday-list.component';
import { HolidayDialogComponent, HolidaySubmission } from 'app/tutorialgroup/manage/holidays/holiday-dialog/holiday-dialog.component';

/** Long enough that typing a date or stepping through a month settles before the count is asked for. */
const SESSION_COUNT_DEBOUNCE_MS = 300;

/** Keeps the rollover from being scheduled for no delay at all if it is set up exactly on midnight. */
const MINIMUM_ROLLOVER_DELAY_MS = 1_000;

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
    /** Sessions each holiday covers, by overlap - the per-day totals would overstate a holiday within a day. */
    protected readonly sessionCountsByHoliday = signal<Map<number, number>>(new Map());
    /** Reloads of the configuration. Switched over, so a mutation's reload cannot be undone by an earlier one. */
    private readonly configurationRequests = new Subject<number>();
    /** Months whose day counts are wanted. Switched over, so only the month on screen can fill the calendar. */
    private readonly monthCountRequests = new Subject<dayjs.Dayjs>();
    /** Reloads of the per-holiday counts. Switched over for the same reason: the newest load is the true one. */
    private readonly holidayCountRequests = new Subject<number>();
    protected readonly isLoading = signal(false);
    protected readonly isSaving = signal(false);

    protected readonly filter = signal<HolidayListFilter>('upcoming');
    protected readonly dialogVisible = signal(false);
    protected readonly editedHoliday = signal<Holiday | undefined>(undefined);
    protected readonly dialogInitialDay = signal<dayjs.Dayjs | undefined>(undefined);
    /**
     * Counts each time the dialog is opened on something new.
     *
     * Cancelling stays available while a save is in flight, so a reader can dismiss the dialog, open another holiday
     * and start typing before the first answer arrives. The answer carries the number the dialog had when it was sent,
     * and closes the dialog only while that is still the one on screen - otherwise it would shut a dialog it never saw
     * and take the reader's input with it.
     */
    private dialogGeneration = 0;

    /** Sessions the span in the dialog covers, so the warning follows the dates the reader picks. */
    protected readonly dialogSessionCount = signal(0);
    /**
     * Spans chosen in the dialog, debounced before they reach the server.
     *
     * Typing a date emits on every keystroke and dragging through a month emits per day, so the raw stream would ask
     * for a count the reader never sees. switchMap also drops the answer to a span they have already moved past.
     */
    private readonly dialogSpanRequests = new Subject<{ start: dayjs.Dayjs; end: dayjs.Dayjs }>();

    private readonly timeZone = computed(() => this.course()?.timeZone);

    /**
     * The moment the page reckons from, moved on at each of the course's midnights.
     *
     * A plain `dayjs()` inside a computed has nothing that changes, so it would be read once and kept: a page left
     * open overnight would go on calling yesterday today, in the Today button, the marker on the calendar and the
     * upcoming filter alike.
     */
    private readonly now = signal(dayjs());
    private dayRolloverTimer?: ReturnType<typeof setTimeout>;

    /** Today in the course's zone: a holiday cancels a day of the course, not a day of whoever is reading. */
    protected readonly today = computed(() => inCourseZone(this.now(), this.timeZone()).startOf('day'));
    protected readonly displayedMonth = signal(dayjs().startOf('month'));

    protected readonly holidays = computed(() => toHolidays(this.freePeriods(), this.timeZone()));

    constructor() {
        this.destroyRef.onDestroy(() => clearTimeout(this.dayRolloverTimer));
        this.reloadConfigurationOnRequest();
        this.countSessionsForSpansChosenInTheDialog();
        this.countSessionsForTheDisplayedMonth();
        this.countSessionsForEachHoliday();
        this.loadCourseFromRoute();
    }

    /**
     * Fills the calendar with the day counts of the month on screen.
     *
     * Switched rather than subscribed per request: stepping quickly through months leaves several in flight, and
     * without this the slowest answer wins rather than the newest, leaving one month labelled with another's counts.
     */
    private countSessionsForTheDisplayedMonth(): void {
        this.monthCountRequests
            .pipe(
                switchMap((month) => {
                    const courseId = this.course()?.id;
                    if (courseId === undefined) {
                        return EMPTY;
                    }
                    // The whole grid, so the days of the neighbouring months it shows carry their counts too.
                    const from = month.startOf('month').startOf('isoWeek');
                    const to = month.endOf('month').endOf('isoWeek');
                    return this.freePeriodService.getSessionCounts(courseId, from, to).pipe(
                        catchError((response: HttpErrorResponse) => {
                            onError(this.alertService, response);
                            return EMPTY;
                        }),
                    );
                }),
                takeUntilDestroyed(),
            )
            .subscribe((counts) => this.sessionCountsByDay.set(new Map(counts.map((count) => [count.date, count.count]))));
    }

    /** One request for the whole list, switched over so a reload after an edit is not undone by the one before it. */
    private countSessionsForEachHoliday(): void {
        this.holidayCountRequests
            .pipe(
                switchMap((courseId) =>
                    this.freePeriodService.getSessionCountsPerFreePeriod(courseId).pipe(
                        catchError((response: HttpErrorResponse) => {
                            onError(this.alertService, response);
                            return EMPTY;
                        }),
                    ),
                ),
                takeUntilDestroyed(),
            )
            .subscribe((counts) => this.sessionCountsByHoliday.set(new Map(counts.map((count) => [count.freePeriodId, count.count]))));
    }

    /**
     * Answers the dialog's chosen span with the number of sessions it would cancel.
     *
     * Debounced because typing a date emits per keystroke and stepping through a month emits per day, so the raw
     * stream would ask for counts nobody sees. switchMap drops the answer to a span already moved past.
     */
    private countSessionsForSpansChosenInTheDialog(): void {
        this.dialogSpanRequests
            .pipe(
                debounceTime(SESSION_COUNT_DEBOUNCE_MS),
                // The course id is captured per request rather than asserted, so a span arriving before the route
                // resolves is dropped instead of throwing inside the stream and killing it for the rest of the page.
                switchMap((span) => {
                    const courseId = this.course()?.id;
                    if (courseId === undefined) {
                        return EMPTY;
                    }
                    // Caught inside the switch: an error reaching the outer subscription would close this pipeline, and
                    // every later date the reader picks would go uncounted until the page is reloaded.
                    return this.freePeriodService.getOverlappingSessionCount(courseId, span.start, span.end, this.editedHoliday()?.period.id).pipe(
                        catchError((response: HttpErrorResponse) => {
                            onError(this.alertService, response);
                            return EMPTY;
                        }),
                    );
                }),
                takeUntilDestroyed(),
            )
            .subscribe((count) => this.dialogSessionCount.set(count));
    }

    private loadCourseFromRoute(): void {
        this.activatedRoute.data.pipe(takeUntilDestroyed()).subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
                // Start on the month the reader is in, expressed in the course's zone.
                this.displayedMonth.set(inCourseZone(dayjs(), course.timeZone).startOf('month'));
                this.moveOnAtTheNextCourseMidnight();
                this.loadConfiguration();
            }
        });
    }

    /**
     * Schedules the page to reckon from the new day when the course's midnight passes, and again at each one after.
     *
     * Waiting exactly until midnight rather than polling, because the only thing that changes then is which day the
     * page calls today, and it changes once.
     */
    private moveOnAtTheNextCourseMidnight(): void {
        clearTimeout(this.dayRolloverTimer);
        const inZone = inCourseZone(dayjs(), this.timeZone());
        const delay = Math.max(inZone.add(1, 'day').startOf('day').diff(inZone), MINIMUM_ROLLOVER_DELAY_MS);
        this.dayRolloverTimer = setTimeout(() => {
            this.now.set(dayjs());
            this.moveOnAtTheNextCourseMidnight();
        }, delay);
    }

    /**
     * Reads the configuration and the holidays it holds.
     *
     * Switched rather than subscribed per call: saving and deleting each trigger a reload, and two of them close
     * together left the older answer free to land last and put a holiday that was just deleted back on the page.
     */
    private reloadConfigurationOnRequest(): void {
        this.configurationRequests
            .pipe(
                switchMap((courseId) =>
                    this.configurationService.getOneOfCourse(courseId).pipe(
                        catchError((response: HttpErrorResponse) => {
                            onError(this.alertService, response);
                            this.isLoading.set(false);
                            return EMPTY;
                        }),
                    ),
                ),
                takeUntilDestroyed(),
            )
            .subscribe((response) => {
                const configuration = response.body ? tutorialGroupsConfigurationEntityFromDto(response.body) : undefined;
                this.configuration.set(configuration);
                this.freePeriods.set(configuration?.tutorialGroupFreePeriods ?? []);
                this.isLoading.set(false);
                this.loadSessionCounts();
                this.loadSessionCountsPerHoliday();
            });
    }

    private loadConfiguration(): void {
        const courseId = this.course()?.id;
        if (courseId === undefined) {
            return;
        }
        this.isLoading.set(true);
        this.configurationRequests.next(courseId);
    }

    private loadSessionCounts(): void {
        this.monthCountRequests.next(this.displayedMonth());
    }

    private loadSessionCountsPerHoliday(): void {
        const courseId = this.course()?.id;
        if (courseId !== undefined) {
            this.holidayCountRequests.next(courseId);
        }
    }

    protected onMonthChange(month: dayjs.Dayjs): void {
        this.displayedMonth.set(month);
        this.loadSessionCounts();
    }

    protected openCreateDialog(day?: dayjs.Dayjs): void {
        this.dialogGeneration++;
        this.editedHoliday.set(undefined);
        this.dialogInitialDay.set(day ?? this.today());
        this.dialogSessionCount.set(0);
        this.dialogVisible.set(true);
    }

    protected openEditDialog(holiday: Holiday): void {
        this.dialogGeneration++;
        this.editedHoliday.set(holiday);
        this.dialogInitialDay.set(undefined);
        this.dialogSessionCount.set(0);
        this.dialogVisible.set(true);
    }

    /**
     * Counts the sessions the span currently chosen in the dialog would cancel.
     *
     * Asked of the server for the exact span rather than read off the loaded month, because a holiday can run past the
     * month the calendar happens to show and a partial count would understate what saving does.
     */
    protected onDialogSpanChange(span: { start: dayjs.Dayjs; end: dayjs.Dayjs }): void {
        // Dropped before the new one is asked for, so the count of the span just left is not shown against this one
        // while the request is on its way - and is not left standing if it never arrives.
        this.dialogSessionCount.set(0);
        this.dialogSpanRequests.next(span);
    }

    protected onSave(submission: HolidaySubmission): void {
        const courseId = this.course()?.id;
        const configurationId = this.configuration()?.id;
        if (courseId === undefined || configurationId === undefined) {
            return;
        }

        // Passed as Dayjs, not as instants: the service writes the wall clock of the zone these were chosen in, which
        // is the course's, and that is what the server reads them back in.
        const payload = { startDate: submission.start, endDate: submission.end, reason: submission.reason };
        const edited = this.editedHoliday();

        this.isSaving.set(true);
        const savedGeneration = this.dialogGeneration;
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
                    // The holiday is saved either way, so the list is reloaded either way; only the dialog is spared.
                    if (savedGeneration === this.dialogGeneration) {
                        this.dialogVisible.set(false);
                    }
                    this.loadConfiguration();
                },
                error: (response: HttpErrorResponse) => onError(this.alertService, response),
            });
    }

    protected onDelete(holiday: Holiday): void {
        const courseId = this.course()?.id;
        const configurationId = this.configuration()?.id;
        const freePeriodId = holiday.period.id;
        if (courseId === undefined || configurationId === undefined || freePeriodId === undefined) {
            return;
        }
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.pages.tutorialFreePeriodsManagement.deleteDialog.header'),
            // A holiday covering several days goes whole, so the confirmation names what actually disappears.
            message: this.translateService.instant(
                holiday.spansMultipleDays
                    ? 'artemisApp.pages.tutorialFreePeriodsManagement.deleteDialog.multiDayQuestion'
                    : 'artemisApp.pages.tutorialFreePeriodsManagement.deleteDialog.question',
                { reason: holiday.reason, count: holiday.dayCount },
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
}
