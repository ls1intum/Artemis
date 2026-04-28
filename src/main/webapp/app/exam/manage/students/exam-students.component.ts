import { Component, ElementRef, EventEmitter, OnDestroy, TemplateRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { User } from 'app/core/user/user.model';
import { EMPTY, Subject, forkJoin, of } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { faChair, faCheck, faTimes, faUserTimes } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UsersImportDialogComponent } from 'app/shared/user-import/dialog/users-import-dialog.component';
import { StudentsUploadImagesDialogComponent } from './upload-images/students-upload-images-dialog.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { StudentsRoomDistributionDialogComponent } from 'app/exam/manage/students/room-distribution/students-room-distribution-dialog.component';
import { StudentsReseatingDialogComponent } from 'app/exam/manage/students/room-distribution/students-reseating-dialog.component';
import { StudentsExportDialogComponent } from 'app/exam/manage/students/export-users/students-export-dialog.component';
import { MenuItem } from 'primeng/api';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ExamStudentsMenuButtonComponent } from 'app/exam/manage/students/exam-students-menu-button/exam-students-menu-button.component';
import { ExamAddStudentsDialogComponent } from 'app/exam/manage/students/add-students-dialog/exam-add-students-dialog.component';
import { ButtonDirective } from 'primeng/button';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status/student-exam-status.component';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ExamExerciseStartPreparationStatus } from 'app/exam/manage/services/exam-exercise-start-preparation-status.model';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import { Tag } from 'primeng/tag';
import { Popover } from 'primeng/popover';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { Tooltip } from 'primeng/tooltip';
import { ProgressBar } from 'primeng/progressbar';
import { TableLazyLoadEvent } from 'primeng/table';
import { CellRendererParams, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared/table-view/table-view';
import { buildDbQueryFromLazyEvent } from 'app/shared/table-view/request-builder';
import { ExamStudentDTO } from 'app/exam/manage/students/exam-student-dto.model';

const getWebsocketChannel = (examId: number) => `/topic/exams/${examId}/exercise-start-status`;

interface MenuCommandEvent {
    originalEvent?: Event;
}

@Component({
    selector: 'jhi-exam-students',
    templateUrl: './exam-students.component.html',
    imports: [
        TranslateDirective,
        UsersImportDialogComponent,
        StudentsExportDialogComponent,
        StudentsRoomDistributionDialogComponent,
        FaIconComponent,
        DeleteButtonDirective,
        ArtemisTranslatePipe,
        StudentsReseatingDialogComponent,
        ExamStudentsMenuButtonComponent,
        ExamAddStudentsDialogComponent,
        ButtonDirective,
        RouterLink,
        ArtemisDatePipe,
        StudentExamStatusComponent,
        StudentExamWorkingTimeComponent,
        TestExamWorkingTimeComponent,
        Tag,
        Popover,
        Tooltip,
        ProgressBar,
        TableViewComponent,
    ],
})
export class ExamStudentsComponent implements OnDestroy {
    protected readonly ActionType = ActionType;
    protected readonly missingImage = '/content/images/missing_image.png';
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private route = inject(ActivatedRoute);
    private examManagementService = inject(ExamManagementService);
    private accountService = inject(AccountService);
    private deleteDialogService = inject(DeleteDialogService);
    private modalService = inject(NgbModal);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private websocketService = inject(WebsocketService);
    private examChecklistService = inject(ExamChecklistService);

    readonly usersImportDialog = viewChild.required(UsersImportDialogComponent);
    readonly studentsExportDialog = viewChild.required(StudentsExportDialogComponent);
    readonly studentsRoomDistributionDialog = viewChild.required(StudentsRoomDistributionDialogComponent);
    readonly addStudentsDialog = viewChild.required(ExamAddStudentsDialogComponent);
    readonly individualExamsStatusPopover = viewChild.required<Popover>('individualExamsStatusPopover');
    readonly individualExamsStatusButton = viewChild<ElementRef<HTMLButtonElement>>('individualExamsStatusButton');
    readonly tableViewRef = viewChild(TableViewComponent);

    // Cell template refs (resolved after view init; used by computed columns signal)
    readonly imageTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<ExamStudentDTO> }>>('imageTemplate');
    readonly studentDetailsTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<ExamStudentDTO> }>>('studentDetailsTemplate');
    readonly roomTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<ExamStudentDTO> }>>('roomTemplate');
    readonly seatTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<ExamStudentDTO> }>>('seatTemplate');
    readonly attendanceTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<ExamStudentDTO> }>>('attendanceTemplate');
    readonly workingTimeTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<ExamStudentDTO> }>>('workingTimeTemplate');
    readonly progressTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<ExamStudentDTO> }>>('progressTemplate');

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

    private removeAllStudentsEmitter = new EventEmitter<{ [key: string]: boolean }>();
    private reloadRequest$ = new Subject<void>();
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

    readonly tableOptions: TableViewOptions = {
        scrollable: true,
        scrollHeight: 'flex',
        rowActionsFrozen: true,
        pageSize: 10,
        // TODO: after participations merged
        // searchPlaceholderKey: 'artemisApp.studentExams.searchForStudents',
    };

    readonly columns = computed<ColumnDef<ExamStudentDTO>[]>(() => {
        const cols: ColumnDef<ExamStudentDTO>[] = [
            { field: 'studentImagePath', width: '3rem', templateRef: this.imageTemplate() },
            { field: 'name', headerKey: 'artemisApp.examManagement.examStudents.table.studentDetails', sort: true, width: '6rem', templateRef: this.studentDetailsTemplate() },
            { field: 'visibleRegistrationNumber', headerKey: 'artemisApp.examManagement.examStudents.table.matriculationNumber', sort: true, width: '6rem' },
            { field: 'actualRoom', headerKey: 'artemisApp.examManagement.examStudents.table.room', sort: true, width: '2rem', templateRef: this.roomTemplate() },
            { field: 'actualSeat', headerKey: 'artemisApp.examManagement.examStudents.table.seat', sort: true, width: '3rem', templateRef: this.seatTemplate() },
        ];

        if (this.hasExamEnded()) {
            cols.push({
                field: 'didExamUserAttendExam',
                headerKey: 'artemisApp.examManagement.examStudents.table.status',
                sort: true,
                width: '2rem',
                templateRef: this.attendanceTemplate(),
            });
        }

        cols.push(
            {
                field: 'workingTime',
                headerKey: this.isTestExam() ? 'artemisApp.studentExams.usedWorkingTime' : 'artemisApp.studentExams.workingTime',
                sort: true,
                width: '4rem',
                templateRef: this.workingTimeTemplate(),
            },
            { field: 'progress', headerKey: 'artemisApp.examManagement.examStudents.table.progress', sort: true, width: '8rem', templateRef: this.progressTemplate() },
            { field: 'numberOfExamSessions', headerKey: 'artemisApp.examManagement.examStudents.table.sessions', sort: true, width: '4rem' },
        );

        return cols;
    });

    readonly manageStudentsMenuActions = signal<MenuItem[]>([
        { label: 'artemisApp.examManagement.examStudents.menu.addStudents', icon: 'pi pi-user-plus', command: () => this.openAddStudentsDialog() },
        { label: 'artemisApp.examManagement.examStudents.menu.importUsers', icon: 'pi pi-file-import', command: () => this.openImportUsersDialog() },
        { label: 'artemisApp.examManagement.examStudents.menu.exportUsers', icon: 'pi pi-file-export', command: () => this.openExportUsersDialog() },
        { label: 'artemisApp.examManagement.examStudents.menu.registerCourseStudents', icon: 'pi pi-user-plus', command: () => this.registerAllStudentsFromCourse() },
        {
            label: 'artemisApp.examManagement.examStudents.menu.removeAllStudents',
            icon: 'pi pi-user-minus',
            styleClass: 'text-danger',
            command: () => this.openRemoveAllStudentsDialog(),
        },
    ]);

    readonly examLogisticsMenuActions = computed<MenuItem[]>(() => [
        { label: 'artemisApp.examManagement.examStudents.menu.uploadImages', icon: 'pi pi-upload', command: () => this.openUploadImagesDialog() },
        { label: 'artemisApp.examManagement.examStudents.menu.distribute', icon: 'pi pi-th-large', command: () => this.studentsRoomDistributionDialog()?.openDialog() },
        {
            label: 'artemisApp.examManagement.examStudents.menu.verifyAttendance',
            icon: 'pi pi-check',
            disabled: !this.hasExamStarted(),
            tooltip: 'artemisApp.examManagement.examStudents.verifyAttendanceTooltip',
            command: () => this.openVerifyAttendance(),
        },
    ]);

    readonly studentExamsMenuActions = computed<MenuItem[]>(() => {
        const isExamStarted = this.hasExamStarted();
        const isLoading = this.isLoading();
        const hasStudentsWithoutExam = this.isMissingIndividualExams();
        const exercisePreparationRunning = this.exercisePreparationRunning();

        return [
            {
                label: 'artemisApp.studentExams.generateStudentExams',
                tooltip: 'artemisApp.studentExams.generateStudentExamsTooltip',
                icon: 'pi pi-file-plus',
                disabled: isExamStarted || isLoading,
                command: (event: MenuCommandEvent) => {
                    this.handleGenerateStudentExams(event.originalEvent);
                },
            },
            {
                label: 'artemisApp.studentExams.generateMissingStudentExams',
                tooltip: 'artemisApp.studentExams.generateMissingStudentExamsTooltip',
                icon: 'pi pi-file-plus',
                disabled: isExamStarted || isLoading || !hasStudentsWithoutExam,
                command: (event: MenuCommandEvent) => {
                    this.generateMissingStudentExams();
                    this.openIndividualExamsStatusPopover(event.originalEvent);
                },
            },
            {
                label: 'artemisApp.studentExams.startExercises',
                tooltip: 'artemisApp.studentExams.startExercisesTooltip',
                icon: 'pi pi-play',
                disabled: isExamStarted || isLoading || exercisePreparationRunning,
                command: (event: MenuCommandEvent) => {
                    this.startExercises();
                    this.openIndividualExamsStatusPopover(event.originalEvent);
                },
            },
        ];
    });

    constructor() {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId')));
        this.isAdmin.set(this.accountService.isAdmin());

        this.removeAllStudentsEmitter.pipe(takeUntilDestroyed()).subscribe({
            next: (event) => this.removeAllStudents(event),
            error: (err) => onError(this.alertService, err),
        });

        this.reloadRequest$
            .pipe(
                takeUntilDestroyed(),
                switchMap(() => {
                    const examId = this.exam().id;
                    if (!examId) {
                        return EMPTY;
                    }
                    return this.examManagementService.find(this.courseId(), examId, true).pipe(
                        catchError((err: HttpErrorResponse) => {
                            onError(this.alertService, err);
                            return EMPTY;
                        }),
                    );
                }),
            )
            .subscribe((examResponse: HttpResponse<Exam>) => {
                if (examResponse.body) {
                    this.examData$.next(examResponse.body);
                }
            });

        this.examData$
            .pipe(
                takeUntilDestroyed(),
                tap((exam: Exam) => {
                    this.exam.set(exam);
                    this.hasExamStarted.set(exam.startDate?.isBefore(dayjs()) || false);
                    this.hasExamEnded.set(exam.endDate?.isBefore(dayjs()) || false);
                    // Seed unfiltered total from route data so the badge appears before the first table load.
                    if (this.totalExamStudents() === 0 && exam.examUsers?.length) {
                        this.totalExamStudents.set(exam.examUsers.length);
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

        effect(() => {
            const exam: Exam | undefined = this.routeData().exam;
            if (exam) {
                // setup exam information
                this.examData$.next(exam);
            }
        });

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

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    loadExamStudents(event: TableLazyLoadEvent): void {
        const examId = this.exam().id;
        if (!examId) {
            return;
        }

        const query = buildDbQueryFromLazyEvent(event);
        this.isLoading.set(true);
        this.examManagementService.findExamStudentsPaged(this.courseId(), examId, query).subscribe({
            next: (result) => {
                this.rows.set(result.content);
                this.totalRows.set(result.totalElements);
                if (!query.searchTerm) {
                    this.totalExamStudents.set(result.totalElements);
                }
                this.isLoading.set(false);
            },
            error: (err: HttpErrorResponse) => {
                onError(this.alertService, err);
                this.isLoading.set(false);
            },
        });
    }

    openImportUsersDialog() {
        this.usersImportDialog()?.open();
    }

    openAddStudentsDialog() {
        this.addStudentsDialog()?.openDialog();
    }

    openExportUsersDialog() {
        this.studentsExportDialog()?.openDialog();
    }

    openRemoveAllStudentsDialog() {
        this.deleteDialogService.openDeleteDialog({
            entityTitle: this.exam()?.title || '',
            deleteQuestion: 'artemisApp.studentExams.removeAllStudents.question',
            translateValues: {},
            deleteConfirmationText: 'artemisApp.studentExams.removeAllStudents.confirmationText',
            additionalChecks: {
                deleteParticipationsAndSubmission: 'artemisApp.examManagement.examStudents.removeFromExam.deleteParticipationsAndSubmission',
            },
            actionType: ActionType.Remove,
            buttonType: ButtonType.ERROR,
            delete: this.removeAllStudentsEmitter,
            dialogError: this.dialogError$,
            requireConfirmationOnlyForAdditionalChecks: false,
        });
    }

    openUploadImagesDialog() {
        const modalRef: NgbModalRef = this.modalService.open(StudentsUploadImagesDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId; // passing the signal itself here else eslint error
        modalRef.componentInstance.exam = this.exam; // same here
        modalRef.result.then(
            () => this.reloadExamWithRegisteredUsers(),
            () => {},
        );
    }

    openVerifyAttendance() {
        const exam = this.exam();
        if (!this.hasExamStarted() || !exam?.id) {
            return;
        }
        this.router.navigate(['/course-management', this.courseId(), 'exams', exam.id, 'students', 'verify-attendance']);
    }

    private openIndividualExamsStatusPopover(event?: Event, defer = false) {
        const showPopover = () => {
            const popover = this.individualExamsStatusPopover();
            const target = this.individualExamsStatusButton()?.nativeElement;
            if (!popover || !target || popover.overlayVisible) {
                return;
            }
            popover.show(event ?? new MouseEvent('click'), target);
        };

        if (defer) {
            setTimeout(showPopover, 0);
            return;
        }
        showPopover();
    }

    reloadExamWithRegisteredUsers() {
        if (!this.exam().id) {
            return;
        }
        this.reloadRequest$.next();
        this.tableViewRef()?.reset();
    }

    /**
     * Unregister student from exam
     *
     * @param examUser User that should be removed from the exam
     * @param event generated by the jhiDeleteButton. Has the property deleteParticipationsAndSubmission, reflecting the checkbox choice of the user
     */
    removeFromExam(examUser: ExamStudentDTO, event: { [key: string]: boolean }) {
        const examId = this.exam().id;
        if (!examId || !examUser.login) {
            return;
        }

        this.examManagementService.removeStudentFromExam(this.courseId(), examId, examUser.login, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.reloadExamWithRegisteredUsers();
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
                this.reloadExamWithRegisteredUsers();
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
                    this.reloadExamWithRegisteredUsers();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }

    /**
     * Generate all student exams for the exam on the server and handle the result.
     * Asks for confirmation if some exams already exist.
     */
    handleGenerateStudentExams(event: Event | undefined) {
        if (this.studentExamCount()) {
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
            modalRef.componentInstance.title = 'artemisApp.studentExams.generateStudentExams';
            modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.studentExams.studentExamGenerationModalText');
            modalRef.result.then(() => {
                this.openIndividualExamsStatusPopover(undefined, true);
                this.generateStudentExams();
            });
        } else {
            this.openIndividualExamsStatusPopover(event);
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
                this.reloadExamWithRegisteredUsers();
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
        return !examUser?.didExamUserAttendExam && this.hasExamEnded();
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
                this.reloadExamWithRegisteredUsers();
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.studentExamGenerationError', err);
                this.isLoading.set(false);
            },
        });
    }

    private setExercisePreparationStatus(newStatus?: ExamExerciseStartPreparationStatus) {
        if (!newStatus || newStatus.overall === undefined) {
            this.exercisePreparationStatus.set(undefined);
            this.exercisePreparationEta.set(undefined);
            this.exercisePreparationRunning.set(false);
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
            const passedSeconds = dayjs().diff(newStatus!.startedAt!, 's');
            const remainingSeconds = (passedSeconds / processedExams) * remainingExams;

            const h = Math.floor(remainingSeconds / 60 / 60);
            const min = Math.floor((remainingSeconds - h * 60 * 60) / 60);
            const s = Math.floor(remainingSeconds - h * 60 * 60 - min * 60);

            this.exercisePreparationEta.set((h ? h + 'h' : '') + (min || h ? min + 'm' : '') + (s || min || h ? s + 's' : ''));
        } else {
            this.exercisePreparationEta.set(undefined);
            this.isAllExercisesPrepared.set(remainingExams === 0 && failedExams === 0);
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
