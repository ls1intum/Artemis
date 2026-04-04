import { Component, EventEmitter, OnDestroy, ViewEncapsulation, computed, effect, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { Subject } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { User } from 'app/core/user/user.model';
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
import { Toolbar } from 'primeng/toolbar';
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
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

type ExamProgress = 'examMissing' | 'notStarted' | 'started' | 'submitted';

interface ExamUserWithExamData extends ExamUser {
    workingTime?: number;
    progress: ExamProgress;
    submissionDate?: dayjs.Dayjs;
    numberOfExamSessions: number;
    studentExamId?: number;
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
        Toolbar,
        ExamStudentsMenuButtonComponent,
        ExamAddStudentsDialogComponent,
        TableModule,
        ButtonDirective,
        IconField,
        InputIcon,
        InputText,
        RouterLink,
        NgTemplateOutlet,
        ArtemisDurationFromSecondsPipe,
        ArtemisDatePipe,
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

    usersImportDialog = viewChild.required(UsersImportDialogComponent);
    studentsExportDialog = viewChild.required(StudentsExportDialogComponent);
    studentsRoomDistributionDialog = viewChild.required(StudentsRoomDistributionDialogComponent);
    addStudentsDialog = viewChild.required(ExamAddStudentsDialogComponent);

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
            const progress: ExamProgress = studentExam?.submitted ? 'submitted' : studentExam?.started ? 'started' : studentExam ? 'notStarted' : 'examMissing';

            return Object.assign({}, examUser, {
                didExamUserAttendExam: hasExamEnded ? !!studentExam?.started : examUser.didExamUserAttendExam,
                workingTime: studentExam?.workingTime,
                progress,
                submissionDate: studentExam?.submissionDate,
                numberOfExamSessions: studentExam?.examSessions?.length ?? 0,
                studentExamId: studentExam?.id,
            }) as ExamUserWithExamData;
        });
    });

    readonly hasStudentsWithoutExam = computed(() => {
        const registeredStudents = this.exam().examUsers?.length ?? 0;
        return registeredStudents > 0 && this.studentExams().length < registeredStudents;
    });

    readonly hasExamStarted = signal(false);
    readonly hasExamEnded = signal(false);
    readonly isAdmin = signal(false);
    readonly isTestExam = computed(() => this.exam()?.testExam ?? false);
    readonly isLoading = signal(true);
    private removeAllStudentsEmitter = new EventEmitter<{ [key: string]: boolean }>();

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly filterFields: Path<ExamUser, 1>[] = ['user.login', 'user.name', 'user.email', 'user.visibleRegistrationNumber'] as const;

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
        const hasStudentsWithoutExam = this.hasStudentsWithoutExam();

        return [
            {
                label: 'artemisApp.studentExams.generateStudentExams',
                icon: 'pi pi-file-plus',
                disabled: isExamStarted || isLoading,
                command: () => this.handleGenerateStudentExams(),
            },
            {
                label: 'artemisApp.studentExams.generateMissingStudentExams',
                icon: 'pi pi-file-plus',
                disabled: isExamStarted || isLoading || !hasStudentsWithoutExam,
                command: () => this.generateMissingStudentExams(),
            },
            {
                label: 'artemisApp.studentExams.startExercises',
                icon: 'pi pi-play',
                disabled: isExamStarted || isLoading,
                command: () => this.startExercises(),
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
            this.isLoading.set(false);
            return;
        }

        this.studentExamService.findAllForExam(this.courseId(), exam.id).subscribe((res) => {
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
                this.exam.update((prevExam) => {
                    const updatedExamUsers = prevExam.examUsers?.filter((eu) => eu.user!.login !== examUser.user!.login);
                    return Object.assign(new Exam(), prevExam, { examUsers: updatedExamUsers });
                });
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
                this.exam.update((prevExam) => Object.assign(new Exam(), prevExam, { examUsers: [] }));
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param user
     */
    searchResultFormatter = (user: User) => {
        const { name, login } = user;
        return `${name} (${login})`;
    };

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
    handleGenerateStudentExams() {
        if (this.studentExams().length) {
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
            modalRef.componentInstance.title = 'artemisApp.studentExams.generateStudentExams';
            modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.studentExams.studentExamGenerationModalText');
            modalRef.result.then(() => {
                this.generateStudentExams();
            });
        } else {
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
