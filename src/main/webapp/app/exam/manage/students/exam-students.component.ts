import { Component, ElementRef, EventEmitter, OnDestroy, ViewEncapsulation, computed, effect, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { Subject } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbModal, NgbModalRef, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { faChair, faCheck, faTimes, faUserTimes } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UsersImportDialogComponent } from 'app/shared/user-import/dialog/users-import-dialog.component';
import { StudentsUploadImagesDialogComponent } from './upload-images/students-upload-images-dialog.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
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
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { TableModule } from 'primeng/table';
import { ButtonDirective } from 'primeng/button';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Path, onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status/student-exam-status.component';
import { tap } from 'rxjs/operators';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ExamExerciseStartPreparationStatus } from 'app/exam/manage/services/exam-exercise-start-preparation-status.model';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import { SortEvent } from 'primeng/api';
import { Tag } from 'primeng/tag';
import { Popover } from 'primeng/popover';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';

const getWebsocketChannel = (examId: number) => `/topic/exams/${examId}/exercise-start-status`;

type ExamProgress = 'examMissing' | 'notStarted' | 'started' | 'submitted';

interface ExamUserWithExamData extends ExamUser {
    workingTime?: number;
    studentExam?: StudentExam;
    progress: ExamProgress;
    submissionDate?: dayjs.Dayjs;
    numberOfExamSessions: number;
    studentExamId?: number;
}

interface MenuCommandEvent {
    originalEvent?: Event;
}

@Component({
    selector: 'jhi-exam-students',
    templateUrl: './exam-students.component.html',
    styleUrls: ['./exam-students.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        UsersImportDialogComponent,
        StudentsExportDialogComponent,
        StudentsRoomDistributionDialogComponent,
        FaIconComponent,
        DeleteButtonDirective,
        NgxDatatableModule,
        ArtemisTranslatePipe,
        StudentsReseatingDialogComponent,
        ExamStudentsMenuButtonComponent,
        ExamAddStudentsDialogComponent,
        TableModule,
        ButtonDirective,
        IconField,
        InputIcon,
        InputText,
        RouterLink,
        NgTemplateOutlet,
        ArtemisDatePipe,
        NgbProgressbar,
        StudentExamStatusComponent,
        StudentExamWorkingTimeComponent,
        TestExamWorkingTimeComponent,
        Tag,
        Popover,
    ],
})
export class ExamStudentsComponent implements OnDestroy {
    protected readonly ActionType = ActionType;
    protected readonly missingImage = '/content/images/missing_image.png';
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private route = inject(ActivatedRoute);
    private examManagementService = inject(ExamManagementService);
    private accountService = inject(AccountService);
    private studentExamService = inject(StudentExamService);
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

    private routeData = toSignal(this.route.data, {
        initialValue: { exam: undefined as Exam | undefined },
    });

    readonly courseId = signal<number>(0);

    readonly exam = signal<Exam>(new Exam());
    readonly studentExams = signal<StudentExam[]>([]);
    readonly allRegisteredUsers = computed<ExamUserWithExamData[]>(() => {
        const exam = this.exam();
        const studentExams = this.studentExams();
        const hasExamEnded = this.hasExamEnded();

        if (!exam?.examUsers) {
            return [];
        }

        const studentExamsByUserId = new Map<number, StudentExam>();
        studentExams.forEach((studentExam) => {
            const userId = studentExam.user?.id;
            if (userId) {
                studentExamsByUserId.set(userId, studentExam);
            }
        });

        return exam.examUsers.map((examUser) => {
            const studentExam = examUser.user?.id ? studentExamsByUserId.get(examUser.user.id) : undefined;
            if (studentExam) {
                studentExam.exam = exam;
            }
            const progress: ExamProgress = studentExam?.submitted ? 'submitted' : studentExam?.started ? 'started' : studentExam ? 'notStarted' : 'examMissing';

            return Object.assign({}, examUser, {
                didExamUserAttendExam: hasExamEnded ? !!studentExam?.started : examUser.didExamUserAttendExam,
                workingTime: studentExam?.workingTime,
                studentExam,
                progress,
                submissionDate: studentExam?.submissionDate,
                numberOfExamSessions: studentExam?.examSessions?.length ?? 0,
                studentExamId: studentExam?.id,
            }) as ExamUserWithExamData;
        });
    });
    readonly hasRegisteredUsers = computed(() => this.allRegisteredUsers().length != 0);

    readonly isMissingIndividualExams = computed(() => {
        const registeredStudents = this.exam().examUsers?.length ?? 0;
        return registeredStudents > 0 && this.studentExams().length < registeredStudents;
    });
    readonly isAllExercisesPrepared = signal(false);
    readonly examPreparationsComplete = computed(() => !this.isMissingIndividualExams() && this.isAllExercisesPrepared());

    readonly hasExamStarted = signal(false);
    readonly hasExamEnded = signal(false);
    readonly isAdmin = signal(false);
    readonly isTestExam = computed(() => this.exam()?.testExam ?? false);
    readonly isLoading = signal(true);
    private removeAllStudentsEmitter = new EventEmitter<{ [key: string]: boolean }>();

    readonly exercisePreparationStatus = signal<ExamExerciseStartPreparationStatus | undefined>(undefined);
    readonly exercisePreparationRunning = signal(false);
    readonly exercisePreparationPercentage = signal(0);
    readonly exercisePreparationEta = signal<string | undefined>(undefined);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly filterFields: Path<ExamUser, 1>[] = ['user.login', 'user.name', 'user.email', 'user.visibleRegistrationNumber'] as const;

    private readonly progressRank: Record<ExamProgress, number> = {
        examMissing: 0,
        notStarted: 1,
        started: 2,
        submitted: 3,
    } as const;

    // Icons
    protected readonly faUserTimes = faUserTimes;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faChair = faChair;

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

        this.removeAllStudentsEmitter.pipe(takeUntilDestroyed()).subscribe((event) => {
            this.removeAllStudents(event);
        });

        effect(() => {
            const exam = this.routeData().exam;
            if (exam) {
                this.setUpExamInformation(exam);
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
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.exam = this.exam;
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
        const examId = this.exam().id;
        if (!examId) {
            return;
        }

        this.isLoading.set(true);
        this.examManagementService.find(this.courseId(), examId, true).subscribe((examResponse: HttpResponse<Exam>) => {
            this.setUpExamInformation(examResponse.body!);
        });
    }

    private setUpExamInformation(exam: Exam) {
        this.exam.set(exam);

        const hasExamStarted = exam.startDate?.isBefore(dayjs()) || false;
        this.hasExamStarted.set(hasExamStarted);
        const hasExamEnded = exam.endDate?.isBefore(dayjs()) || false;
        this.hasExamEnded.set(hasExamEnded);

        if (!exam.id) {
            this.studentExams.set([]);
            this.isAllExercisesPrepared.set(false);
            this.isLoading.set(false);
            return;
        }

        if (exam.course?.id) {
            this.examChecklistService.getExamStatistics(exam).subscribe({
                next: (examChecklist) => this.isAllExercisesPrepared.set(!!examChecklist.allExamExercisesAllStudentsPrepared),
                error: () => this.isAllExercisesPrepared.set(false),
            });
        } else {
            this.isAllExercisesPrepared.set(false);
        }

        const courseId = this.courseId();
        this.examManagementService.getExerciseStartStatus(courseId, exam.id).subscribe((res) => this.setExercisePreparationStatus(res.body ?? undefined));

        this.studentExamService.findAllForExam(courseId, exam.id).subscribe((res) => {
            this.studentExams.set(res.body || []);
            this.isLoading.set(false);
        });
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Unregister student from exam
     *
     * @param examUser User that should be removed from the exam
     * @param event generated by the jhiDeleteButton. Has the property deleteParticipationsAndSubmission, reflecting the checkbox choice of the user
     */
    removeFromExam(examUser: ExamUser, event: { [key: string]: boolean }) {
        const examId = this.exam().id;
        if (!examId) {
            return;
        }

        this.examManagementService.removeStudentFromExam(this.courseId(), examId, examUser.user!.login!, event.deleteParticipationsAndSubmission).subscribe({
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
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
        }
    }

    /**
     * Generate all student exams for the exam on the server and handle the result.
     * Asks for confirmation if some exams already exist.
     */
    handleGenerateStudentExams(event: Event | undefined) {
        if (this.studentExams().length) {
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
                this.isLoading.set(false);
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

    attendanceCheckFailed(examUser: ExamUser | undefined) {
        return (
            examUser?.didExamUserAttendExam &&
            this.hasExamEnded() &&
            (!examUser.didCheckLogin || !examUser.didCheckImage || !examUser.didCheckName || !examUser.didCheckRegistrationNumber || !examUser.signingImagePath)
        );
    }

    attendanceCheckPassed(examUser: ExamUser | undefined) {
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

    didAttendExam(examUser: ExamUser | undefined) {
        return !examUser?.didExamUserAttendExam && this.hasExamEnded();
    }

    asExamUserWithExamData(value: ExamUserWithExamData | undefined) {
        return value as ExamUserWithExamData | undefined;
    }

    viewStudentExam(examUser: ExamUserWithExamData) {
        const examId = this.exam().id;
        if (!examId || !examUser.studentExamId) {
            return;
        }

        this.router.navigate(['/course-management', this.courseId(), 'exams', examId, 'student-exams', examUser.studentExamId]);
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
                this.isLoading.set(false);
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
        this.exercisePreparationStatus.set(newStatus);
        const processedExams = (newStatus.finished ?? 0) + (newStatus.failed ?? 0);
        const exPrepRunning = newStatus && processedExams < newStatus.overall;
        this.exercisePreparationRunning.set(exPrepRunning);
        this.exercisePreparationPercentage.set(newStatus.overall ? Math.round((processedExams / newStatus.overall) * 100) : 100);
        const remainingExams = newStatus.overall - processedExams;
        if (exPrepRunning && processedExams) {
            const passedSeconds = dayjs().diff(newStatus!.startedAt!, 's');
            const remainingSeconds = (passedSeconds / processedExams) * remainingExams;

            const h = Math.floor(remainingSeconds / 60 / 60);
            const min = Math.floor((remainingSeconds - h * 60 * 60) / 60);
            const s = Math.floor(remainingSeconds - h * 60 * 60 - min * 60);

            this.exercisePreparationEta.set((h ? h + 'h' : '') + (min || h ? min + 'm' : '') + (s || min || h ? s + 's' : ''));
        } else {
            this.exercisePreparationEta.set(undefined);
            this.isAllExercisesPrepared.set(remainingExams === 0);
        }
    }

    onCustomSort(event: SortEvent) {
        const data = event.data as ExamUserWithExamData[] | undefined;
        const field = event.field;
        const order = event.order;
        if (!data || !field || !order) {
            return;
        }

        data.sort((a, b) => this.compareByField(a, b, field, order));
    }

    private compareByField(a: ExamUserWithExamData, b: ExamUserWithExamData, field: string, order: number) {
        switch (field) {
            case 'studentDetails':
                return this.compareStudentDetails(a, b, order);
            case 'matriculationNumber':
                return this.compareNullableStrings(a.user?.visibleRegistrationNumber, b.user?.visibleRegistrationNumber, order);
            case 'room':
                return this.compareNullableStrings(this.getDisplayedRoom(a), this.getDisplayedRoom(b), order);
            case 'seat':
                return this.compareNatural(this.getDisplayedSeat(a), this.getDisplayedSeat(b), order);
            case 'attendanceStatus':
                return this.compareAttendanceStatus(a, b, order);
            case 'workingTime':
                return this.compareNullableNumbers(this.getWorkingTimeSortValue(a), this.getWorkingTimeSortValue(b), order);
            case 'progress':
                return this.compareProgress(a, b, order);
            case 'sessions':
                return this.compareNullableNumbers(a.numberOfExamSessions, b.numberOfExamSessions, order);
            default:
                return 0;
        }
    }

    private compareStudentDetails(a: ExamUserWithExamData, b: ExamUserWithExamData, order: number) {
        const byName = this.compareNullableStrings(a.user?.name, b.user?.name, order);
        if (byName !== 0) {
            return byName;
        }

        const byLogin = this.compareNullableStrings(a.user?.login, b.user?.login, order);
        if (byLogin !== 0) {
            return byLogin;
        }

        return this.compareNullableStrings(a.user?.email, b.user?.email, order);
    }

    private compareAttendanceStatus(a: ExamUserWithExamData, b: ExamUserWithExamData, order: number) {
        const rankA = this.getAttendanceRank(a);
        const rankB = this.getAttendanceRank(b);
        const byRank = (rankA - rankB) * order;
        if (byRank !== 0) {
            return byRank;
        }

        return this.compareStudentDetails(a, b, order);
    }

    private compareProgress(a: ExamUserWithExamData, b: ExamUserWithExamData, order: number) {
        const byProgress = (this.progressRank[a.progress] - this.progressRank[b.progress]) * order;
        if (byProgress !== 0) {
            return byProgress;
        }

        if (a.progress === 'submitted' && b.progress === 'submitted') {
            const bySubmissionDate = this.compareNullableNumbers(a.submissionDate?.valueOf(), b.submissionDate?.valueOf(), order);
            if (bySubmissionDate !== 0) {
                return bySubmissionDate;
            }
        }

        return this.compareStudentDetails(a, b, order);
    }

    private compareNullableStrings(a: string | undefined, b: string | undefined, order: number) {
        if (a === b) {
            return 0;
        }
        if (a == undefined || a === '') {
            return -1 * order;
        }
        if (b == undefined || b === '') {
            return order;
        }
        return a.localeCompare(b, undefined, { sensitivity: 'base' }) * order;
    }

    private compareNullableNumbers(a: number | undefined, b: number | undefined, order: number) {
        if (a === b) {
            return 0;
        }
        if (a == undefined) {
            return -1 * order;
        }
        if (b == undefined) {
            return order;
        }
        return (a - b) * order;
    }

    private compareNatural(a: string | undefined, b: string | undefined, order: number) {
        if (a === b) {
            return 0;
        }
        if (a == undefined || a === '') {
            return -1 * order;
        }
        if (b == undefined || b === '') {
            return order;
        }
        return a.localeCompare(b, undefined, { sensitivity: 'base', numeric: true }) * order;
    }

    private getDisplayedRoom(examUser: ExamUserWithExamData) {
        return examUser.actualRoom ?? examUser.plannedRoom;
    }

    private getDisplayedSeat(examUser: ExamUserWithExamData) {
        return examUser.actualSeat ?? examUser.plannedSeat;
    }

    private getAttendanceRank(examUser: ExamUserWithExamData) {
        if (this.attendanceCheckFailed(examUser)) {
            return 0;
        }
        if (this.attendanceCheckPassed(examUser)) {
            return 1;
        }
        if (examUser.didExamUserAttendExam) {
            return 2;
        }
        return 3;
    }

    private getWorkingTimeSortValue(examUser: ExamUserWithExamData) {
        const studentExam = examUser.studentExam;
        if (!studentExam) {
            return undefined;
        }

        if (!this.isTestExam()) {
            return studentExam.workingTime;
        }

        if (!studentExam.started || !studentExam.submitted || !studentExam.workingTime || !studentExam.startedDate || !studentExam.submissionDate) {
            return 0;
        }

        const usedWorkingTime = dayjs(studentExam.submissionDate).diff(dayjs(studentExam.startedDate), 'seconds');
        return Math.min(studentExam.workingTime, usedWorkingTime);
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
