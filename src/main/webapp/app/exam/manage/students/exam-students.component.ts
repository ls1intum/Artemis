import { Component, ElementRef, OnDestroy, computed, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { User } from 'app/account/user/user.model';
import { Observable, Subject, forkJoin, of } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import {
    faChair,
    faCheck,
    faCircleInfo,
    faEye,
    faFileCirclePlus,
    faFileExport,
    faFileImport,
    faFilePen,
    faIdCard,
    faPlay,
    faThLarge,
    faTimes,
    faTriangleExclamation,
    faUpload,
    faUser,
    faUserMinus,
    faUserPen,
    faUserPlus,
    faUserTimes,
} from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UsersImportDialogComponent } from 'app/shared-ui/user-import/dialog/users-import-dialog.component';
import { StudentsUploadImagesDialogComponent } from './upload-images/students-upload-images-dialog.component';
import {
    CellTemplateRef,
    ColumnDef,
    TumAetUiButtonDirective,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiPopoverComponent,
    TumAetUiPopoverTriggerDirective,
    TumAetUiProgressBarComponent,
    TumAetUiSearchFieldComponent,
    TumAetUiSelectComponent,
    TumAetUiTableComponent,
    TumAetUiTableQueryEvent,
    TumAetUiTagComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamDeleteDialogComponent } from 'app/exam/shared/delete-dialog/exam-delete-dialog.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { StudentsRoomDistributionDialogComponent } from 'app/exam/manage/students/room-distribution/students-room-distribution-dialog.component';
import { StudentsReseatingDialogComponent } from 'app/exam/manage/students/room-distribution/students-reseating-dialog.component';
import { StudentsExportDialogComponent } from 'app/exam/manage/students/export-users/students-export-dialog.component';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ExamStudentsMenuButtonComponent, ExamStudentsMenuItem } from 'app/exam/manage/students/exam-students-menu-button/exam-students-menu-button.component';
import { UserRegistrationModalComponent } from 'app/shared-ui/user-registration-modal/user-registration-modal.component';
import { UserForRegistration, UserSearchResult } from 'app/shared-ui/user-registration-modal/user-for-registration.model';
import { ExamUserDTO } from 'app/exam/shared/entities/exam-user-dto.model';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status/student-exam-status.component';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { ExamExerciseStartPreparationStatus } from 'app/exam/manage/services/exam-exercise-start-preparation-status.model';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/test-exam-working-time/test-exam-working-time.component';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { buildDbQueryFromTableEvent } from 'app/shared-ui/tum-aet-ui-integration/tumaet-ui-table-request-builder';
import { ExamStudentDTO, ExamStudentSearch } from 'app/exam/manage/students/exam-student-dto.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';

const getWebsocketChannel = (examId: number) => `/topic/exams/${examId}/exercise-start-status`;

const SEARCH_DEBOUNCE_MS = 300;

@Component({
    selector: 'jhi-exam-students',
    templateUrl: './exam-students.component.html',
    imports: [
        TranslateDirective,
        UsersImportDialogComponent,
        StudentsExportDialogComponent,
        StudentsUploadImagesDialogComponent,
        StudentsRoomDistributionDialogComponent,
        FaIconComponent,
        ExamDeleteDialogComponent,
        ArtemisTranslatePipe,
        StudentsReseatingDialogComponent,
        ExamStudentsMenuButtonComponent,
        UserRegistrationModalComponent,
        NgTemplateOutlet,
        TumAetUiButtonDirective,
        TumAetUiConfirmDialogComponent,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
        TumAetUiProgressBarComponent,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
        RouterLink,
        ArtemisDatePipe,
        StudentExamStatusComponent,
        StudentExamWorkingTimeComponent,
        TestExamWorkingTimeComponent,
        TumAetUiTableComponent,
        TumAetUiSearchFieldComponent,
        TumAetUiSelectComponent,
        FormsModule,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleDirective,
    ],
    providers: [TumAetUiConfirmationService],
})
export class ExamStudentsComponent implements OnDestroy {
    private route = inject(ActivatedRoute);
    private examManagementService = inject(ExamManagementService);
    private accountService = inject(AccountService);
    private confirmationService = inject(TumAetUiConfirmationService);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private websocketService = inject(WebsocketService);
    private examChecklistService = inject(ExamChecklistService);

    protected readonly ActionType = ActionType;
    protected readonly missingImage = '/content/images/missing_image.png';
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    readonly usersImportDialog = viewChild.required(UsersImportDialogComponent);
    readonly studentsExportDialog = viewChild.required(StudentsExportDialogComponent);
    readonly studentsUploadImagesDialog = viewChild.required(StudentsUploadImagesDialogComponent);
    readonly studentsRoomDistributionDialog = viewChild.required(StudentsRoomDistributionDialogComponent);
    readonly addStudentsModal = viewChild.required(UserRegistrationModalComponent);
    readonly individualExamsStatusPopover = viewChild(TumAetUiPopoverComponent);
    readonly individualExamsStatusButton = viewChild<ElementRef<HTMLButtonElement>>('individualExamsStatusButton');
    readonly table = viewChild(TumAetUiTableComponent<ExamStudentDTO>);

    // Cell template refs (resolved after view init; used by computed columns signal)
    readonly plainTemplate = viewChild<CellTemplateRef<ExamStudentDTO>>('plainTemplate');
    readonly imageTemplate = viewChild<CellTemplateRef<ExamStudentDTO>>('imageTemplate');
    readonly studentDetailsTemplate = viewChild<CellTemplateRef<ExamStudentDTO>>('studentDetailsTemplate');
    readonly roomTemplate = viewChild<CellTemplateRef<ExamStudentDTO>>('roomTemplate');
    readonly seatTemplate = viewChild<CellTemplateRef<ExamStudentDTO>>('seatTemplate');
    readonly attendanceTemplate = viewChild<CellTemplateRef<ExamStudentDTO>>('attendanceTemplate');
    readonly workingTimeTemplate = viewChild<CellTemplateRef<ExamStudentDTO>>('workingTimeTemplate');
    readonly progressTemplate = viewChild<CellTemplateRef<ExamStudentDTO>>('progressTemplate');

    private routeData = toSignal(this.route.data, {
        initialValue: { exam: undefined as Exam | undefined },
    });

    readonly courseId = signal<number>(0);
    readonly exam = signal<Exam>(new Exam());

    // Table data signals
    readonly rows = signal<ExamStudentDTO[]>([]);
    readonly totalRows = signal(0);
    /** Unfiltered total — used for "Registered students" badge and isMissingIndividualExams. */
    readonly totalExamStudents = signal(0);
    /** Number of generated student exams from the exam checklist. */
    readonly studentExamCount = signal(0);

    readonly hasRegisteredUsers = computed(() => this.totalExamStudents() > 0);
    readonly isMissingIndividualExams = computed(() => {
        const total = this.totalExamStudents();
        return total > 0 && this.studentExamCount() < total;
    });
    readonly isAllExercisesPrepared = signal(false);
    readonly examPreparationsComplete = computed(() => !this.isMissingIndividualExams() && this.isAllExercisesPrepared());

    readonly hasExamStarted = signal(false);
    readonly hasExamEnded = signal(false);
    readonly isAdmin = signal(false);
    readonly isTestExam = computed(() => this.exam()?.testExam ?? false);
    readonly isLoading = signal(true);

    readonly searchUsersForExamFn = (term: string, page: number, size: number): Observable<UserSearchResult> => {
        const courseId = this.courseId();
        const examId = this.exam().id;
        if (!examId) return of({ content: [], totalElements: 0 });
        return this.examManagementService.searchUsersForExamRegistration(courseId, examId, term, page, size);
    };

    readonly registerUsersForExamFn = (users: UserForRegistration[]): Observable<void> => {
        const courseId = this.courseId();
        const examId = this.exam().id;
        if (!examId) return of(void 0);
        const dtos: ExamUserDTO[] = users.map((u) => ({
            login: u.login,
            firstName: '',
            lastName: '',
            registrationNumber: u.registrationNumber ?? '',
            email: u.email ?? '',
        }));
        return this.examManagementService.addStudentsToExam(courseId, examId, dtos).pipe(
            tap((res) => {
                const { notFoundStudents, rejectedStaffUsers } = res.body ?? {};
                if (notFoundStudents?.length) {
                    const logins = notFoundStudents.map((u) => u.login).join(', ');
                    this.alertService.error('artemisApp.examManagement.examStudents.addDialog.notFoundStudents', { logins });
                }
                if (rejectedStaffUsers?.length) {
                    const logins = rejectedStaffUsers.map((u) => u.login).join(', ');
                    this.alertService.error('artemisApp.examManagement.examStudents.addDialog.rejectedStaffUsers', { logins });
                }
            }),
            map(() => undefined),
        );
    };

    readonly activeFilter = signal('All');
    /** The selected filter, or undefined while all students are shown. */
    readonly selectedFilter = computed(() => (this.activeFilter() === 'All' ? undefined : this.activeFilter()));
    readonly examStudentFilterOptions = computed<{ value: string; label: string }[]>(() => {
        const filters = ['ExamMissing', 'NotStarted', 'Started', 'Submitted'];
        if (this.hasExamEnded()) {
            filters.push('DidNotAttend', 'AttendanceNotChecked', 'AttendanceChecked');
        }
        return filters.map((value) => ({ value, label: this.artemisTranslatePipe.transform('artemisApp.examManagement.examStudents.filter.' + value) }));
    });
    private searchTerm = '';
    private searchTimer?: ReturnType<typeof setTimeout>;
    private requestId = 0;
    private lastLoadEvent: TumAetUiTableQueryEvent | undefined;
    // True while a lazy load was recorded but skipped because the exam id was not yet available. It lets the
    // examData$ tap replay exactly that one skipped load and nothing more (issue #13063).
    private lazyEventPending = false;

    readonly removeStudentDialogVisible = signal(false);
    readonly removeAllDialogVisible = signal(false);
    readonly studentToRemove = signal<ExamStudentDTO | undefined>(undefined);
    /** The extra check of both removal dialogs: whether the participations and submissions of the students are deleted as well. */
    protected readonly removeStudentChecks = {
        deleteParticipationsAndSubmission: 'artemisApp.examManagement.examStudents.removeFromExam.deleteParticipationsAndSubmission',
    };
    private examData$ = new Subject<Exam>();

    readonly exercisePreparationStatus = signal<ExamExerciseStartPreparationStatus | undefined>(undefined);
    readonly exercisePreparationRunning = signal(false);
    readonly exercisePreparationPercentage = signal(0);
    readonly exercisePreparationEta = signal<string | undefined>(undefined);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    protected readonly faUserTimes = faUserTimes;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faChair = faChair;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faEye = faEye;
    protected readonly faFilePen = faFilePen;
    protected readonly faIdCard = faIdCard;
    protected readonly faThLarge = faThLarge;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faUser = faUser;
    protected readonly faUserMinus = faUserMinus;
    protected readonly faUserPen = faUserPen;

    readonly columns = computed<ColumnDef<ExamStudentDTO>[]>(() => {
        const cols: ColumnDef<ExamStudentDTO>[] = [
            { field: 'studentImagePath', width: '3rem', templateRef: this.imageTemplate() },
            { field: 'name', headerKey: 'artemisApp.examManagement.examStudents.table.studentDetails', sort: true, width: '6rem', templateRef: this.studentDetailsTemplate() },
            {
                field: 'visibleRegistrationNumber',
                headerKey: 'artemisApp.examManagement.examStudents.table.matriculationNumber',
                sort: true,
                wrapHeader: true,
                width: '6rem',
                templateRef: this.plainTemplate(),
            },
            { field: 'actualRoom', headerKey: 'artemisApp.examManagement.examStudents.table.room', sort: true, width: '2rem', templateRef: this.roomTemplate() },
            { field: 'actualSeat', headerKey: 'artemisApp.examManagement.examStudents.table.seat', sort: true, width: '3rem', templateRef: this.seatTemplate() },
        ];

        if (this.hasExamEnded()) {
            cols.push({
                field: 'didExamUserAttendExam',
                headerKey: 'artemisApp.examManagement.examStudents.table.status',
                sort: false,
                width: '2rem',
                templateRef: this.attendanceTemplate(),
            });
        }

        cols.push(
            {
                field: 'workingTime',
                headerKey: this.isTestExam() ? 'artemisApp.studentExams.usedWorkingTime' : 'artemisApp.studentExams.workingTime',
                sort: true,
                wrapHeader: true,
                width: '4rem',
                templateRef: this.workingTimeTemplate(),
            },
            { field: 'progress', headerKey: 'artemisApp.examManagement.examStudents.table.progress', sort: true, width: '7rem', templateRef: this.progressTemplate() },
            {
                field: 'numberOfExamSessions',
                headerKey: 'artemisApp.examManagement.examStudents.table.sessions',
                sort: true,
                width: '4rem',
                hideBelow: '2xl',
                templateRef: this.plainTemplate(),
            },
        );

        return cols;
    });

    readonly manageStudentsMenuActions = signal<ExamStudentsMenuItem[]>([
        { label: 'artemisApp.examManagement.examStudents.menu.addStudents', icon: faUserPlus, command: () => this.openAddStudentsDialog() },
        { label: 'artemisApp.examManagement.examStudents.menu.importUsers', icon: faFileImport, command: () => this.openImportUsersDialog() },
        { label: 'artemisApp.examManagement.examStudents.menu.exportUsers', icon: faFileExport, command: () => this.openExportUsersDialog() },
        { label: 'artemisApp.examManagement.examStudents.menu.registerCourseStudents', icon: faUserPlus, command: () => this.registerAllStudentsFromCourse() },
        {
            label: 'artemisApp.examManagement.examStudents.menu.removeAllStudents',
            icon: faUserMinus,
            danger: true,
            command: () => this.openRemoveAllStudentsDialog(),
        },
    ]);

    readonly examLogisticsMenuActions = computed<ExamStudentsMenuItem[]>(() => [
        { label: 'artemisApp.examManagement.examStudents.menu.uploadImages', icon: faUpload, command: () => this.openUploadImagesDialog() },
        { label: 'artemisApp.examManagement.examStudents.menu.distribute', icon: faThLarge, command: () => this.studentsRoomDistributionDialog()?.openDialog() },
        {
            label: 'artemisApp.examManagement.examStudents.menu.verifyAttendance',
            icon: faCheck,
            disabled: !this.hasExamStarted(),
            tooltip: 'artemisApp.examManagement.examStudents.verifyAttendanceTooltip',
            command: () => this.openVerifyAttendance(),
        },
    ]);

    readonly studentExamsMenuActions = computed<ExamStudentsMenuItem[]>(() => {
        const isExamStarted = this.hasExamStarted();
        const isLoading = this.isLoading();
        const hasStudentsWithoutExam = this.isMissingIndividualExams();
        const exercisePreparationRunning = this.exercisePreparationRunning();

        return [
            {
                label: 'artemisApp.studentExams.generateStudentExams',
                tooltip: 'artemisApp.studentExams.generateStudentExamsTooltip',
                icon: faFileCirclePlus,
                disabled: isExamStarted || isLoading,
                command: () => this.handleGenerateStudentExams(),
            },
            {
                label: 'artemisApp.studentExams.generateMissingStudentExams',
                tooltip: 'artemisApp.studentExams.generateMissingStudentExamsTooltip',
                icon: faFileCirclePlus,
                disabled: isExamStarted || isLoading || !hasStudentsWithoutExam,
                command: () => {
                    this.generateMissingStudentExams();
                    this.openIndividualExamsStatusPopover();
                },
            },
            {
                label: 'artemisApp.studentExams.startExercises',
                tooltip: 'artemisApp.studentExams.startExercisesTooltip',
                icon: faPlay,
                disabled: isExamStarted || isLoading || exercisePreparationRunning,
                command: () => {
                    this.startExercises();
                    this.openIndividualExamsStatusPopover();
                },
            },
        ];
    });

    constructor() {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId')));
        this.isAdmin.set(this.accountService.isAdmin());

        this.examData$
            .pipe(
                takeUntilDestroyed(),
                tap((exam: Exam) => {
                    this.exam.set(exam);
                    this.hasExamStarted.set(exam.startDate?.isBefore(dayjs()) || false);
                    this.hasExamEnded.set(exam.endDate?.isBefore(dayjs()) || false);
                    // The paginated table fires its initial lazy load before the exam id is available, so
                    // loadExamStudents early-returns (leaving isLoading=true and totalExamStudents=0). Replay that one
                    // skipped load now that the exam has resolved, otherwise the "Generate student exams" button stays
                    // disabled even though students are registered (issue #13063). Guarded by lazyEventPending so later
                    // exam re-emissions (reloadStudentsView / websocket-driven fetchExamData) do not trigger a second,
                    // redundant page load — the table's own reset() already reloads on those paths. This replay is
                    // fire-and-forget and intentionally independent of the switchMap below (which only refreshes stats).
                    if (this.lastLoadEvent && this.lazyEventPending) {
                        this.loadExamStudents(this.lastLoadEvent);
                    }
                }),
                switchMap((exam: Exam) => {
                    const courseId = this.courseId();
                    const examId = exam.id!;

                    const examStats$ = this.examChecklistService.getExamStatistics(exam).pipe(
                        catchError((err: HttpErrorResponse) => {
                            onError(this.alertService, err);
                            return of(undefined);
                        }),
                    );

                    const exercisePreparationStatus$ = this.examManagementService.getExerciseStartStatus(courseId, examId).pipe(
                        catchError((err: HttpErrorResponse) => {
                            onError(this.alertService, err);
                            return of(undefined);
                        }),
                        map((res) => res?.body ?? undefined),
                    );

                    return forkJoin({ examStats: examStats$, exercisePreparationStatus: exercisePreparationStatus$ });
                }),
            )
            .subscribe(({ examStats, exercisePreparationStatus }) => {
                this.isAllExercisesPrepared.set(!!examStats?.allExamExercisesAllStudentsPrepared);
                this.studentExamCount.set(examStats?.numberOfGeneratedStudentExams ?? 0);
                this.setExercisePreparationStatus(exercisePreparationStatus);
            });

        // Bridge the route data into the exam-data pipeline whenever it changes. Replaces an effect() whose only job
        // was to call fetchExamData() when routeData() changed (an effect() misuse — using an effect to push a signal
        // value into a Subject). fetchExamData() stays a method because it is also invoked imperatively elsewhere
        // (setExercisePreparationStatus).
        toObservable(this.routeData)
            .pipe(takeUntilDestroyed())
            .subscribe(() => this.fetchExamData());

        effect((onCleanup) => {
            const examId = this.exam().id;
            if (!examId) {
                return;
            }

            const channel = getWebsocketChannel(examId);
            const exercisePreparationSubscription = this.websocketService
                .subscribe<ExamExerciseStartPreparationStatus>(channel)
                .pipe(tap((status: ExamExerciseStartPreparationStatus) => (status.startedAt = convertDateFromServer(status.startedAt))))
                .subscribe((status: ExamExerciseStartPreparationStatus) => this.setExercisePreparationStatus(status));

            onCleanup(() => {
                exercisePreparationSubscription.unsubscribe();
            });
        });
    }

    private fetchExamData() {
        const exam: Exam | undefined = this.routeData().exam;
        if (exam) {
            // setup exam information
            this.examData$.next(exam);
        }
    }

    ngOnDestroy() {
        clearTimeout(this.searchTimer);
        this.dialogErrorSource.unsubscribe();
    }

    /** The select reports the chosen value, or undefined once the selection was cleared. */
    onFilterSelected(value: unknown): void {
        this.onFilterChange(typeof value === 'string' ? value : undefined);
    }

    onFilterChange(filter: string | undefined): void {
        this.activeFilter.set(filter ?? 'All');
        this.reloadFromFirstPage();
    }

    /** Applies the search term after the reader stopped typing, so each keystroke does not trigger a request. */
    onSearchInput(term: string): void {
        clearTimeout(this.searchTimer);
        this.searchTimer = setTimeout(() => {
            this.searchTerm = term.trim();
            this.reloadFromFirstPage();
        }, SEARCH_DEBOUNCE_MS);
    }

    /** The table owns the page state: it re-requests only if it has to leave a later page, otherwise the current request is repeated. */
    private reloadFromFirstPage(): void {
        if (this.lastLoadEvent && this.lastLoadEvent.pageIndex !== 0) {
            this.table()?.resetPage();
        } else if (this.lastLoadEvent) {
            this.loadExamStudents(this.lastLoadEvent);
        }
    }

    loadExamStudents(event: TumAetUiTableQueryEvent): void {
        this.lastLoadEvent = event;
        const examId = this.exam().id;
        if (!examId) {
            // The table fired its lazy load before the exam id was available; remember that we owe a load so the
            // examData$ tap can replay it once the exam resolves (issue #13063).
            this.lazyEventPending = true;
            return;
        }
        this.lazyEventPending = false;

        const currentRequestId = ++this.requestId;
        const query = buildDbQueryFromTableEvent(event);
        const search: ExamStudentSearch = cloneWith(query, { searchTerm: this.searchTerm, filterProp: this.activeFilter() !== 'All' ? this.activeFilter() : undefined });
        this.isLoading.set(true);
        this.examManagementService.findExamStudentsPaged(this.courseId(), examId, search).subscribe({
            next: (result) => {
                if (currentRequestId !== this.requestId) {
                    return;
                }
                this.rows.set(result.content);
                this.totalRows.set(result.totalElements);
                if (!search.searchTerm && !search.filterProp) {
                    this.totalExamStudents.set(result.totalElements ?? 0);
                }
                this.isLoading.set(false);
            },
            error: (err: HttpErrorResponse) => {
                if (currentRequestId !== this.requestId) {
                    return;
                }
                onError(this.alertService, err);
                this.isLoading.set(false);
            },
        });
    }

    openImportUsersDialog() {
        this.usersImportDialog()?.open();
    }

    openAddStudentsDialog() {
        this.addStudentsModal()?.open();
    }

    openExportUsersDialog() {
        this.studentsExportDialog()?.openDialog();
    }

    openRemoveAllStudentsDialog() {
        this.removeAllDialogVisible.set(true);
    }

    openRemoveStudentDialog(examUser: ExamStudentDTO) {
        this.studentToRemove.set(examUser);
        this.removeStudentDialogVisible.set(true);
    }

    openUploadImagesDialog() {
        this.studentsUploadImagesDialog().open();
    }

    openVerifyAttendance() {
        const exam = this.exam();
        if (!this.hasExamStarted() || !exam?.id) {
            return;
        }
        void this.router.navigate(['/course-management', this.courseId(), 'exams', exam.id, 'students', 'verify-attendance']);
    }

    reloadStudentsView() {
        const exam = this.exam();
        if (!exam.id) {
            return;
        }
        this.examData$.next(exam);
        if (this.lastLoadEvent) {
            this.loadExamStudents(this.lastLoadEvent);
        }
    }

    /**
     * Unregister student from exam
     *
     * @param examUser User that should be removed from the exam
     * @param event emitted by the removal dialog. Has the property deleteParticipationsAndSubmission, reflecting the checkbox choice of the user
     */
    removeFromExam(examUser: ExamStudentDTO, event: { [key: string]: boolean }) {
        const examId = this.exam().id;
        if (!examId || !examUser.login) {
            return;
        }

        this.examManagementService.removeStudentFromExam(this.courseId(), examId, examUser.login, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.reloadStudentsView();
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Unregister all students from the exam
     */
    removeAllStudents(event: { [key: string]: boolean }) {
        const examId = this.exam().id;
        if (!examId) {
            return;
        }

        this.examManagementService.removeAllStudentsFromExam(this.courseId(), examId, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.reloadStudentsView();
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Registers all students who are enrolled in the course for the exam
     */
    registerAllStudentsFromCourse() {
        const exam = this.exam();
        if (exam?.id) {
            this.examManagementService.addAllStudentsOfCourseToExam(this.courseId(), exam.id).subscribe({
                next: () => {
                    this.reloadStudentsView();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }

    private openIndividualExamsStatusPopover(defer = false) {
        const showPopover = () => {
            const popover = this.individualExamsStatusPopover();
            const target = this.individualExamsStatusButton()?.nativeElement;
            if (!popover || !target) {
                return;
            }
            popover.open(target);
        };

        if (defer) {
            setTimeout(showPopover, 0);
            return;
        }
        showPopover();
    }

    /**
     * Generate all student exams for the exam on the server and handle the result.
     * Asks for confirmation if some exams already exist.
     */
    handleGenerateStudentExams() {
        if (this.studentExamCount() > 0) {
            this.confirmationService.confirm({
                header: this.artemisTranslatePipe.transform('artemisApp.studentExams.generateStudentExams'),
                message: this.artemisTranslatePipe.transform('artemisApp.studentExams.studentExamGenerationModalText'),
                rejectLabel: this.artemisTranslatePipe.transform('global.form.cancel'),
                acceptLabel: this.artemisTranslatePipe.transform('global.form.confirm'),
                acceptSeverity: 'danger',
                accept: () => {
                    this.openIndividualExamsStatusPopover(true);
                    this.generateStudentExams();
                },
            });
        } else {
            this.openIndividualExamsStatusPopover();
            this.generateStudentExams();
        }
    }

    /**
     * Generate missing student exams for the exam on the server and handle the result.
     * Student exams can be missing if a student was added after the initial generation of all student exams.
     */
    generateMissingStudentExams() {
        const examId = this.exam().id;
        if (!examId) {
            return;
        }

        this.isLoading.set(true);
        this.examManagementService.generateMissingStudentExams(this.courseId(), examId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.studentExams.missingStudentExamGenerationSuccess', { number: res?.body?.length ?? 0 });
                this.reloadStudentsView();
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.missingStudentExamGenerationError', err);
                this.isLoading.set(false);
            },
        });
    }

    /**
     * Starts all the exercises of the student exams that belong to the exam
     */
    startExercises() {
        const examId = this.exam().id;
        if (!examId) {
            return;
        }

        this.isLoading.set(true);
        this.examManagementService.startExercises(this.courseId(), examId).subscribe({
            next: () => {
                this.alertService.success('artemisApp.studentExams.startExerciseSuccess');
                this.isLoading.set(false);
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.startExerciseFailure', err);
                this.isLoading.set(false);
            },
        });
    }

    attendanceCheckFailed(examUser: ExamStudentDTO | undefined) {
        return (
            examUser?.didExamUserAttendExam &&
            this.hasExamEnded() &&
            (!examUser.didCheckLogin || !examUser.didCheckImage || !examUser.didCheckName || !examUser.didCheckRegistrationNumber || !examUser.signingImagePath)
        );
    }

    attendanceCheckPassed(examUser: ExamStudentDTO | undefined) {
        return (
            examUser?.didExamUserAttendExam &&
            examUser.didCheckLogin &&
            examUser.didCheckImage &&
            examUser.didCheckName &&
            examUser.didCheckRegistrationNumber &&
            examUser.signingImagePath &&
            this.hasExamEnded()
        );
    }

    didNotAttendExam(examUser: ExamStudentDTO | undefined) {
        return examUser?.didExamUserAttendExam === false && this.hasExamEnded();
    }

    /** Builds a minimal StudentExam from the DTO so working-time sub-components can compute % extension. */
    toStudentExam(dto: ExamStudentDTO): StudentExam | undefined {
        if (dto.studentExamId === undefined) {
            return undefined;
        }
        const se = new StudentExam();
        se.id = Number(dto.studentExamId);
        se.workingTime = dto.workingTime;
        se.started = dto.started;
        se.submitted = dto.submitted;
        se.startedDate = dto.startedDate;
        se.submissionDate = dto.submissionDate;
        se.testRun = false;
        se.exam = this.exam();
        return se;
    }

    /** Maps a flat DTO to the ExamUser shape expected by StudentsReseatingDialogComponent. */
    toExamUserForReseating(dto: ExamStudentDTO): ExamUser {
        const user = new User();
        user.id = dto.userId;
        user.login = dto.login;
        user.name = dto.name;
        user.firstName = dto.name; // getSelectedStudentName() concatenates firstName + lastName
        const examUser = new ExamUser();
        examUser.id = dto.id;
        examUser.plannedRoom = dto.plannedRoom;
        examUser.actualRoom = dto.actualRoom;
        examUser.plannedSeat = dto.plannedSeat;
        examUser.actualSeat = dto.actualSeat;
        examUser.user = user;
        return examUser;
    }

    private generateStudentExams() {
        const examId = this.exam().id;
        if (!examId) {
            return;
        }

        this.isLoading.set(true);
        this.examManagementService.generateStudentExams(this.courseId(), examId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.studentExams.studentExamGenerationSuccess', { number: res?.body?.length ?? 0 });
                this.reloadStudentsView();
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.studentExamGenerationError', err);
                this.isLoading.set(false);
            },
        });
    }

    private setExercisePreparationStatus(newStatus?: ExamExerciseStartPreparationStatus) {
        const wasExercisePreparationRunning = this.exercisePreparationRunning();

        if (!newStatus || newStatus.overall === undefined) {
            this.exercisePreparationStatus.set(undefined);
            this.exercisePreparationEta.set(undefined);
            this.exercisePreparationRunning.set(false);
            if (wasExercisePreparationRunning) {
                this.fetchExamData();
            }
            return;
        }
        const failedExams = newStatus.failed ?? 0;
        const finishedExams = newStatus.finished ?? 0;
        const processedExams = finishedExams + failedExams;
        const remainingExams = newStatus.overall - processedExams;
        const exPrepRunning = processedExams < newStatus.overall;

        this.exercisePreparationStatus.set(newStatus);
        this.exercisePreparationRunning.set(exPrepRunning);
        this.exercisePreparationPercentage.set(newStatus.overall ? Math.round((processedExams / newStatus.overall) * 100) : 100);

        if (exPrepRunning && processedExams) {
            const passedSeconds = dayjs().diff(newStatus.startedAt, 's');
            const remainingSeconds = (passedSeconds / processedExams) * remainingExams;

            const h = Math.floor(remainingSeconds / 60 / 60);
            const min = Math.floor((remainingSeconds - h * 60 * 60) / 60);
            const s = Math.floor(remainingSeconds - h * 60 * 60 - min * 60);

            this.exercisePreparationEta.set((h ? h + 'h' : '') + (min || h ? min + 'm' : '') + (s || min || h ? s + 's' : ''));
        } else {
            this.exercisePreparationEta.set(undefined);
            if (wasExercisePreparationRunning && !exPrepRunning) {
                this.fetchExamData();
            } else if (!exPrepRunning) {
                this.isAllExercisesPrepared.set(failedExams === 0 && finishedExams >= newStatus.overall);
            }
        }
    }

    /**
     * Shows the translated error message if an error key is available in the error response. Otherwise it defaults to the generic alert.
     * @param translationString the string identifier in the translation service for the text. This is ignored if the response does not contain an error message or error key.
     * @param err the error response
     */
    private handleError(translationString: string, err: HttpErrorResponse) {
        let errorDetail;
        if (err?.error && err.error.errorKey) {
            errorDetail = this.artemisTranslatePipe.transform(err.error.errorKey);
        } else {
            errorDetail = err?.error?.message;
        }
        if (errorDetail) {
            this.alertService.error(translationString, { message: errorDetail });
        } else {
            // Sometimes the response does not have an error field, so we default to generic error handling
            onError(this.alertService, err);
        }
    }
}
