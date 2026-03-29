import { Component, EventEmitter, OnDestroy, OnInit, ViewEncapsulation, inject, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { Observable, Subject, Subscription, of } from 'rxjs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { User } from 'app/core/user/user.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { UserService } from 'app/core/user/shared/user.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { iconsAsHTML } from 'app/shared/util/icons.utils';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import {
    faChair,
    faCheck,
    faChevronDown,
    faFileExport,
    faFileImport,
    faInfoCircle,
    faPlus,
    faThLarge,
    faTimes,
    faUpload,
    faUserSlash,
    faUserTimes,
    faUsersGear,
} from '@fortawesome/free-solid-svg-icons';
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
import { Menu } from 'primeng/menu';
import { ButtonDirective } from 'primeng/button';
import { MenuItem } from 'primeng/api';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';

const cssClasses = {
    alreadyRegistered: 'already-registered',
    newlyRegistered: 'newly-registered',
};

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
        RouterLink,
        DeleteButtonDirective,
        DataTableComponent,
        NgxDatatableModule,
        ArtemisTranslatePipe,
        StudentsReseatingDialogComponent,
        Toolbar,
        Menu,
        ButtonDirective,
    ],
})
export class ExamStudentsComponent implements OnInit, OnDestroy {
    protected readonly ActionType = ActionType;
    protected readonly missingImage = '/content/images/missing_image.png';
    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private userService = inject(UserService);
    private accountService = inject(AccountService);
    private studentExamService = inject(StudentExamService);
    private deleteDialogService = inject(DeleteDialogService);
    private modalService = inject(NgbModal);
    private router = inject(Router);

    dataTable = viewChild.required(DataTableComponent);
    usersImportDialog = viewChild.required(UsersImportDialogComponent);
    studentsExportDialog = viewChild.required(StudentsExportDialogComponent);
    studentsRoomDistributionDialog = viewChild.required(StudentsRoomDistributionDialogComponent);

    courseId: number;
    exam: Exam;
    isTestExam: boolean;
    allRegisteredUsers: ExamUser[] = [];
    filteredUsersSize = 0;
    paramSub: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading = false;
    hasExamStarted = false;
    hasExamEnded = false;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass: string | undefined = undefined;

    isAdmin = false;

    // Icons
    protected readonly faPlus = faPlus;
    protected readonly faUserSlash = faUserSlash;
    protected readonly faUserTimes = faUserTimes;
    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faThLarge = faThLarge;
    protected readonly faUpload = faUpload;
    protected readonly faChair = faChair;
    protected readonly faFileExport = faFileExport;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faUsersGear = faUsersGear;

    manageStudentsMenuActions: MenuItem[] = [
        { label: 'Add students', faIcon: faPlus },
        { label: 'Import users', faIcon: faFileImport, command: () => this.openImportUsersDialog() },
        { label: 'Export users', faIcon: faFileExport, command: () => this.openExportUsersDialog() },
        { label: 'Register course students', faIcon: faPlus, command: () => this.registerAllStudentsFromCourse() },
        { label: 'Remove all students', faIcon: faUserSlash, styleClass: 'text-danger', command: () => this.openRemoveAllStudentsDialog() },
    ];

    examLogisticsMenuActions: MenuItem[] = [
        { label: 'Upload images', faIcon: faUpload, command: () => this.openUploadImagesDialog() },
        { label: 'Distribute', faIcon: faThLarge, command: () => this.studentsRoomDistributionDialog()?.openDialog() },
        {
            label: 'Verify attendance',
            faIcon: faCheck,
            tooltip: 'artemisApp.examManagement.examStudents.verifyAttendanceTooltip',
            command: () => this.openVerifyAttendance(),
        },
    ];

    openImportUsersDialog() {
        this.usersImportDialog()?.open();
    }

    openExportUsersDialog() {
        this.studentsExportDialog()?.openDialog();
    }

    openRemoveAllStudentsDialog() {
        const removeAllStudentsEmitter = new EventEmitter<{ [key: string]: boolean }>();
        removeAllStudentsEmitter.subscribe((event) => this.removeAllStudents(event));

        this.deleteDialogService.openDeleteDialog({
            entityTitle: this.exam.title || '',
            deleteQuestion: 'artemisApp.studentExams.removeAllStudents.question',
            translateValues: {},
            deleteConfirmationText: 'artemisApp.studentExams.removeAllStudents.confirmationText',
            additionalChecks: {
                deleteParticipationsAndSubmission: 'artemisApp.examManagement.examStudents.removeFromExam.deleteParticipationsAndSubmission',
            },
            actionType: ActionType.Remove,
            buttonType: ButtonType.ERROR,
            delete: removeAllStudentsEmitter,
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
        if (!this.hasExamStarted || !this.exam?.id) {
            return;
        }

        this.router.navigate(['/course-management', this.courseId, 'exams', this.exam.id, 'students', 'verify-attendance']);
    }

    ngOnInit() {
        this.isLoading = true;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.isAdmin = this.accountService.isAdmin();
        this.examLogisticsMenuActions = this.buildExamLogisticsMenuActions();
        this.route.data.subscribe(({ exam }: { exam: Exam }) => {
            this.setUpExamInformation(exam);
        });
    }

    reloadExamWithRegisteredUsers() {
        this.isLoading = true;
        this.examManagementService.find(this.courseId, this.exam.id!, true).subscribe((examResponse: HttpResponse<Exam>) => {
            this.setUpExamInformation(examResponse.body!);
        });
    }

    private setUpExamInformation(exam: Exam) {
        this.exam = exam;
        this.hasExamStarted = exam.startDate?.isBefore(dayjs()) || false;
        this.hasExamEnded = exam.endDate?.isBefore(dayjs()) || false;
        this.examLogisticsMenuActions = this.buildExamLogisticsMenuActions();

        if (this.hasExamEnded) {
            this.studentExamService.findAllForExam(this.courseId, exam.id!).subscribe((res) => {
                const studentExams = res.body;
                this.allRegisteredUsers =
                    exam.examUsers?.map((examUser) => {
                        const studentExam = studentExams?.filter((studentExam) => studentExam.user?.id === examUser.user!.id).first();
                        return {
                            ...examUser.user!,
                            ...examUser,
                            didExamUserAttendExam: !!studentExam?.started,
                        };
                    }) || [];
            });
        } else {
            this.allRegisteredUsers =
                exam.examUsers?.map((examUser) => {
                    return {
                        ...examUser.user!,
                        ...examUser,
                    };
                }) || [];
        }
        this.isTestExam = this.exam.testExam!;
        this.isLoading = false;
    }

    private buildExamLogisticsMenuActions(): MenuItem[] {
        return [
            { label: 'Upload images', faIcon: faUpload, command: () => this.openUploadImagesDialog() },
            { label: 'Distribute', faIcon: faThLarge, command: () => this.studentsRoomDistributionDialog()?.openDialog() },
            {
                label: 'Verify attendance',
                faIcon: faCheck,
                tooltip: 'artemisApp.examManagement.examStudents.verifyAttendanceTooltip',
                disabled: !this.hasExamStarted,
                command: () => this.openVerifyAttendance(),
            },
        ];
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Receives the search text and filter results from DataTableComponent, modifies them and returns the result which will be used by ngbTypeahead.
     *
     * @param stream$ stream of searches of the format {text, entities} where entities are the results
     * @return stream of users for the autocomplete
     */
    searchAllUsers = (stream$: Observable<{ text: string; entities: User[] }>): Observable<User[]> => {
        return stream$.pipe(
            switchMap(({ text: loginOrName }) => {
                this.searchFailed = false;
                this.searchNoResults = false;
                if (loginOrName.length < 3) {
                    return of([]);
                }
                this.isSearching = true;
                return this.userService
                    .search(loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        tap((users) => {
                            if (users.length === 0) {
                                this.searchNoResults = true;
                            }
                        }),
                        catchError(() => {
                            this.searchFailed = true;
                            return of([]);
                        }),
                    );
            }),
            tap(() => {
                this.isSearching = false;
            }),
            tap((users) => {
                setTimeout(() => {
                    for (let i = 0; i < this.dataTable().typeaheadButtons.length; i++) {
                        const isAlreadyInCourseGroup = this.allRegisteredUsers.map((user) => user.id).includes(users[i].id);
                        const button = this.dataTable().typeaheadButtons[i];
                        const hasIcon = button.querySelector('fa-icon');
                        if (!hasIcon) {
                            button.insertAdjacentHTML('beforeend', iconsAsHTML[isAlreadyInCourseGroup ? 'users' : 'users-plus']);
                        }
                        if (isAlreadyInCourseGroup) {
                            button.classList.add(cssClasses.alreadyRegistered);
                        }
                    }
                });
            }),
        );
    };

    /**
     * Receives the user that was selected in the autocomplete and the callback from DataTableComponent.
     * The callback inserts the search term of the selected entity into the search field and updates the displayed users.
     *
     * @param user The selected user from the autocomplete suggestions
     * @param callback Function that can be called with the selected user to trigger the DataTableComponent default behavior
     */
    onAutocompleteSelect = (user: User, callback: (user: User) => void): void => {
        // If the user is not registered for this exam yet, perform the server call to add them
        if (!this.allRegisteredUsers.map((eu) => eu.user!.id).includes(user.id) && user.login) {
            this.isTransitioning = true;
            this.examManagementService.addStudentToExam(this.courseId, this.exam.id!, user.login).subscribe({
                next: () => {
                    this.isTransitioning = false;
                    this.reloadExamWithRegisteredUsers();
                    // Flash green background color to signal to the user that this student was registered
                    this.flashRowClass(cssClasses.newlyRegistered);
                },
                error: (error: HttpErrorResponse) => {
                    if (error.status === 403) {
                        this.onError(`artemisApp.exam.error.${error.error.errorKey}`);
                    }
                    this.isTransitioning = false;
                },
            });
        } else {
            // Hand back over to the data table
            callback(user);
        }
    };

    /**
     * Unregister student from exam
     *
     * @param examUser User that should be removed from the exam
     * @param event generated by the jhiDeleteButton. Has the property deleteParticipationsAndSubmission, reflecting the checkbox choice of the user
     */
    removeFromExam(examUser: ExamUser, event: { [key: string]: boolean }) {
        this.examManagementService.removeStudentFromExam(this.courseId, this.exam.id!, examUser.user!.login!, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.allRegisteredUsers = this.allRegisteredUsers.filter((eu) => eu.user!.login !== examUser.user!.login);
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Unregister all students from the exam
     */
    removeAllStudents(event: { [key: string]: boolean }) {
        this.examManagementService.removeAllStudentsFromExam(this.courseId, this.exam.id!, event.deleteParticipationsAndSubmission).subscribe({
            next: () => {
                this.allRegisteredUsers = [];
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Update the number of filtered users
     *
     * @param filteredUsersSize Total number of users after filters have been applied
     */
    handleUsersSizeChange = (filteredUsersSize: number) => {
        this.filteredUsersSize = filteredUsersSize;
    };

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
     * Converts a user object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param user User
     */
    searchTextFromUser = (user: User): string => {
        return user.login || '';
    };

    /**
     * Can be used to highlight rows temporarily by flashing a certain css class
     *
     * @param className Name of the class to be applied to all rows
     */
    flashRowClass = (className: string) => {
        this.rowClass = className;
        setTimeout(() => (this.rowClass = undefined), 500);
    };

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.alertService.error(error);
        this.isTransitioning = false;
    }

    /**
     * Registers all students who are enrolled in the course for the exam
     */
    registerAllStudentsFromCourse() {
        if (this.exam?.id) {
            this.examManagementService.addAllStudentsOfCourseToExam(this.courseId, this.exam.id).subscribe({
                next: () => {
                    this.reloadExamWithRegisteredUsers();
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
        }
    }
}
