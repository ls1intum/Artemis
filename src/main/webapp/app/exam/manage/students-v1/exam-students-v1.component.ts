import { Component, OnDestroy, OnInit, ViewEncapsulation, inject, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, Subscription, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { FormsModule } from '@angular/forms';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { User } from 'app/core/user/user.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { UserService } from 'app/core/user/shared/user.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { onError } from 'app/shared/util/global.utils';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { faChair, faCheck, faFileExport, faFileImport, faPlus, faThLarge, faTimes, faUpload, faUserSlash, faUserTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { StudentsRoomDistributionDialogComponent } from 'app/exam/manage/students/room-distribution/students-room-distribution-dialog.component';
import { StudentsReseatingDialogComponent } from 'app/exam/manage/students/room-distribution/students-reseating-dialog.component';
import { StudentsExportDialogComponent } from 'app/exam/manage/students/export-users/students-export-dialog.component';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status/student-exam-status.component';
import { NgbModal, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ButtonModule } from 'primeng/button';
import { UsersImportDialogComponent } from 'app/shared/user-import/dialog/users-import-dialog.component';
import { StudentsUploadImagesDialogComponent } from 'app/exam/manage/students/upload-images/students-upload-images-dialog.component';
import { Tag } from 'primeng/tag';

const getWebsocketChannel = (examId: number) => `/topic/exams/${examId}/exercise-start-status`;

type ExamExerciseStartPreparationStatus = {
    finished?: number;
    failed?: number;
    overall?: number;
    participationCount?: number;
    startedAt?: dayjs.Dayjs;
};

type StudentsV1Row = ExamUser & {
    user?: User;
    name?: string;
    login?: string;
    email?: string;
    visibleRegistrationNumber?: string;
    didExamUserAttendExam?: boolean;
    studentExam?: StudentExam;
    numberOfExamSessions: number;
    hasStudentExam: boolean;
    started?: boolean;
    submitted?: boolean;
    submissionDate?: dayjs.Dayjs;
    workingTime?: number;
};

@Component({
    selector: 'jhi-exam-students-v1',
    templateUrl: './exam-students-v1.component.html',
    styleUrls: ['./exam-students-v1.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        FormsModule,
        AutoCompleteModule,
        TranslateDirective,
        UsersImportDialogComponent,
        StudentsExportDialogComponent,
        StudentsRoomDistributionDialogComponent,
        FaIconComponent,
        RouterLink,
        DeleteButtonDirective,
        DataTableComponent,
        NgxDatatableModule,
        ArtemisTranslatePipe,
        StudentsReseatingDialogComponent,
        StudentExamStatusComponent,
        NgbProgressbar,
        StudentExamWorkingTimeComponent,
        TestExamWorkingTimeComponent,
        ArtemisDatePipe,
        ButtonModule,
        Tag,
    ],
})
export class ExamStudentsV1Component implements OnInit, OnDestroy {
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ActionType = ActionType;
    protected readonly missingImage = '/content/images/missing_image.png';
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private route = inject(ActivatedRoute);
    private modalService = inject(NgbModal);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private studentExamService = inject(StudentExamService);
    private userService = inject(UserService);
    private accountService = inject(AccountService);
    private courseService = inject(CourseManagementService);
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private websocketService = inject(WebsocketService);

    dataTable = viewChild.required(DataTableComponent);
    importDialog = viewChild<UsersImportDialogComponent>('importDialog');

    courseId: number;
    examId: number;
    course: Course;
    exam: Exam;
    isTestExam = false;
    isAdmin = false;

    allRows: StudentsV1Row[] = [];
    studentExams: StudentExam[] = [];
    filteredUsersSize = 0;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading = false;
    hasExamStarted = false;
    hasExamEnded = false;
    isTransitioning = false;

    hasStudentsWithoutExam = false;
    isExamStarted = false;

    exercisePreparationStatus?: ExamExerciseStartPreparationStatus;
    exercisePreparationRunning = false;
    exercisePreparationPercentage = 0;
    exercisePreparationEta?: string;
    private exercisePreparationSubscription?: Subscription;

    searchQuery = '';
    searchTerms: string[] = [];
    customFilterKey = 0;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    searchQueryTooShort = false;
    searchSuggestions: User[] = [];

    // Icons
    protected readonly faPlus = faPlus;
    protected readonly faUserSlash = faUserSlash;
    protected readonly faUpload = faUpload;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faThLarge = faThLarge;
    protected readonly faChair = faChair;
    protected readonly faUserTimes = faUserTimes;
    protected readonly faFileExport = faFileExport;
    protected readonly faFileImport = faFileImport;

    ngOnInit() {
        this.isLoading = true;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.isAdmin = this.accountService.isAdmin();

        this.route.data.subscribe(({ exam }: { exam: Exam }) => {
            this.setExamData(exam);
            this.loadCourse();
            this.reloadAllData();
        });

        const channel = getWebsocketChannel(this.examId);
        this.exercisePreparationSubscription = this.websocketService
            .subscribe<ExamExerciseStartPreparationStatus>(channel)
            .pipe(tap((status: ExamExerciseStartPreparationStatus) => (status.startedAt = convertDateFromServer(status.startedAt))))
            .subscribe((status: ExamExerciseStartPreparationStatus) => this.setExercisePreparationStatus(status));
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
        this.exercisePreparationSubscription?.unsubscribe();
    }

    private loadCourse() {
        this.courseService.find(this.courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
        });
    }

    private setExamData(exam: Exam) {
        this.exam = exam;
        this.isTestExam = this.exam.testExam ?? false;
        this.hasExamStarted = exam.startDate?.isBefore(dayjs()) || false;
        this.hasExamEnded = exam.endDate?.isBefore(dayjs()) || false;
        this.isExamStarted = this.hasExamStarted;
    }

    reloadAllData() {
        this.isLoading = true;
        this.examManagementService.find(this.courseId, this.examId, true).subscribe((examResponse: HttpResponse<Exam>) => {
            this.setExamData(examResponse.body!);
            this.studentExamService.findAllForExam(this.courseId, this.examId).subscribe((res) => {
                this.studentExams = res.body ?? [];
                this.studentExams.forEach((studentExam: StudentExam) => {
                    studentExam.exam = this.exam;
                    studentExam.numberOfExamSessions = studentExam.examSessions?.length ?? 0;
                });
                this.allRows = this.buildRows(this.exam.examUsers ?? [], this.studentExams);
                this.hasStudentsWithoutExam = (this.exam.examUsers?.length ?? 0) > this.studentExams.length;
                this.isLoading = false;
            });
            this.examManagementService.getExerciseStartStatus(this.courseId, this.examId).subscribe((statusRes) => this.setExercisePreparationStatus(statusRes.body ?? undefined));
        });
    }

    openUsersImportDialog(event: MouseEvent) {
        event.stopPropagation();
        this.importDialog()?.open();
    }

    openUploadImagesDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef = this.modalService.open(StudentsUploadImagesDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.exam = this.exam;
        modalRef.result.then(
            () => this.reloadAllData(),
            () => {},
        );
    }

    private buildRows(examUsers: ExamUser[], studentExams: StudentExam[]): StudentsV1Row[] {
        const studentExamByUserId = new Map<number, StudentExam>();
        for (const studentExam of studentExams) {
            if (studentExam.user?.id) {
                studentExamByUserId.set(studentExam.user.id, studentExam);
            }
        }

        return examUsers.map((examUser) => {
            const userId = examUser.user?.id;
            const studentExam = userId ? studentExamByUserId.get(userId) : undefined;
            const user = examUser.user;
            const row: StudentsV1Row = {
                ...examUser,
                user,
                name: user?.name,
                login: user?.login,
                email: user?.email,
                visibleRegistrationNumber: user?.visibleRegistrationNumber,
                studentExam,
                numberOfExamSessions: studentExam?.examSessions?.length ?? 0,
                hasStudentExam: !!studentExam,
                started: studentExam?.started,
                submitted: studentExam?.submitted,
                submissionDate: studentExam?.submissionDate,
                workingTime: studentExam?.workingTime,
                didExamUserAttendExam: !!studentExam?.started,
            };
            return row;
        });
    }

    onSearchComplete(event: { query: string }) {
        const query = (event.query ?? '').trim();
        this.searchFailed = false;
        this.searchNoResults = false;
        this.searchQueryTooShort = false;

        if (!query) {
            this.searchSuggestions = [];
            return;
        }

        if (query.length < 3) {
            this.searchQueryTooShort = true;
            this.searchSuggestions = [];
            return;
        }

        this.isSearching = true;
        this.userService
            .search(query)
            .pipe(
                map((response) => response.body ?? []),
                catchError(() => {
                    this.searchFailed = true;
                    return of([]);
                }),
            )
            .subscribe((users) => {
                this.searchSuggestions = users;
                this.searchNoResults = users.length === 0;
                this.isSearching = false;
            });
    }

    onSearchQueryChange(newValue: string) {
        this.searchQuery = newValue;
        this.searchTerms = this.extractSearchTerms(newValue);
        this.customFilterKey++;
    }

    onSearchSelect(event: { value: User }) {
        const user = event.value;
        if (!user?.id) {
            return;
        }

        const alreadyRegistered = this.allRows.some((row) => row.user?.id === user.id);
        if (alreadyRegistered) {
            this.searchQuery = user.login ?? this.searchQuery;
            this.onSearchQueryChange(this.searchQuery);
            return;
        }

        if (!user.login) {
            return;
        }

        this.isTransitioning = true;
        this.examManagementService.addStudentToExam(this.courseId, this.examId, user.login).subscribe({
            next: () => {
                this.isTransitioning = false;
                this.searchSuggestions = [];
                this.searchQuery = '';
                this.onSearchQueryChange('');
                this.reloadAllData();
            },
            error: (error: HttpErrorResponse) => {
                if (error.status === 403) {
                    this.onError(`artemisApp.exam.error.${error.error.errorKey}`);
                }
                this.isTransitioning = false;
            },
        });
    }

    isSuggestionAlreadyRegistered(user: User): boolean {
        if (!user?.id) {
            return false;
        }
        return this.allRows.some((row) => row.user?.id === user.id);
    }

    searchResultFormatter = (user: User) => `${user.name} (${user.login})`;

    private extractSearchTerms(searchQuery: string): string[] {
        const terms = searchQuery
            .split(',')
            .map((term) => term.trim().toLowerCase())
            .filter((term) => !!term);
        return terms;
    }

    matchesSearch = (row: StudentsV1Row): boolean => {
        if (!this.searchTerms.length) {
            return true;
        }

        const fields = [
            row.user?.login,
            row.user?.name,
            row.user?.email,
            row.user?.visibleRegistrationNumber,
            row.plannedRoom,
            row.actualRoom,
            row.plannedSeat,
            row.actualSeat,
            row.studentExam?.id?.toString(),
        ]
            .filter(Boolean)
            .map((field) => field!.toString().toLowerCase());
        return this.searchTerms.every((term) => fields.some((field) => field.includes(term)));
    };

    handleUsersSizeChange = (filteredUsersSize: number) => {
        this.filteredUsersSize = filteredUsersSize;
    };

    removeFromExam(examUser: StudentsV1Row, event: { [key: string]: boolean }) {
        const login = examUser.user?.login;
        if (!login) {
            return;
        }
        this.examManagementService.removeStudentFromExam(this.courseId, this.examId, login, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.allRows = this.allRows.filter((row) => row.user?.login !== login);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    removeAllStudents(event: { [key: string]: boolean }) {
        this.examManagementService.removeAllStudentsFromExam(this.courseId, this.examId, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.allRows = [];
                this.studentExams = [];
                this.hasStudentsWithoutExam = false;
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    registerAllStudentsFromCourse() {
        if (!this.exam?.id) {
            return;
        }
        this.examManagementService.addAllStudentsOfCourseToExam(this.courseId, this.exam.id).subscribe({
            next: () => this.reloadAllData(),
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    handleGenerateStudentExams() {
        if (this.studentExams.length) {
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
            modalRef.componentInstance.title = 'artemisApp.studentExams.generateStudentExams';
            modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.studentExams.studentExamGenerationModalText');
            modalRef.result.then(() => this.generateStudentExams());
            return;
        }
        this.generateStudentExams();
    }

    private generateStudentExams() {
        this.isLoading = true;
        this.examManagementService.generateStudentExams(this.courseId, this.examId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.studentExams.studentExamGenerationSuccess', { number: res?.body?.length ?? 0 });
                this.reloadAllData();
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.studentExamGenerationError', err);
                this.isLoading = false;
            },
        });
    }

    generateMissingStudentExams() {
        this.isLoading = true;
        this.examManagementService.generateMissingStudentExams(this.courseId, this.examId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.studentExams.missingStudentExamGenerationSuccess', { number: res?.body?.length ?? 0 });
                this.reloadAllData();
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.missingStudentExamGenerationError', err);
                this.isLoading = false;
            },
        });
    }

    startExercises() {
        this.isLoading = true;
        this.examManagementService.startExercises(this.courseId, this.examId).subscribe({
            next: () => {
                this.alertService.success('artemisApp.studentExams.startExerciseSuccess');
                this.isLoading = false;
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.startExerciseFailure', err);
                this.isLoading = false;
            },
        });
    }

    setExercisePreparationStatus(newStatus?: ExamExerciseStartPreparationStatus) {
        this.exercisePreparationStatus = newStatus;
        const processedExams = (newStatus?.finished ?? 0) + (newStatus?.failed ?? 0);
        this.exercisePreparationRunning = !!(newStatus && processedExams < (newStatus.overall ?? 0));
        this.exercisePreparationPercentage = newStatus ? (newStatus.overall ? Math.round((processedExams / newStatus.overall) * 100) : 100) : 0;
        this.exercisePreparationEta = undefined;
        if (this.exercisePreparationRunning && processedExams && newStatus?.startedAt && newStatus.overall) {
            const remainingExams = newStatus.overall - processedExams;
            const passedSeconds = dayjs().diff(newStatus.startedAt, 's');
            const remainingSeconds = (passedSeconds / processedExams) * remainingExams;

            const h = Math.floor(remainingSeconds / 60 / 60);
            const min = Math.floor((remainingSeconds - h * 60 * 60) / 60);
            const s = Math.floor(remainingSeconds - h * 60 * 60 - min * 60);

            this.exercisePreparationEta = (h ? `${h}h` : '') + (min || h ? `${min}m` : '') + (s || min || h ? `${s}s` : '');
        }
    }

    onError(error: string) {
        this.alertService.error(error);
        this.isTransitioning = false;
    }

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
            onError(this.alertService, err);
        }
    }

    protected readonly String = String;
}
