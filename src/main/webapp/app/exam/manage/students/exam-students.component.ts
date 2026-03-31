import { Component, EventEmitter, OnDestroy, ViewEncapsulation, computed, effect, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { Observable, Subject, of } from 'rxjs';
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
import { Path } from 'app/shared/util/global.utils';

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
        ExamStudentsMenuButtonComponent,
        ExamAddStudentsDialogComponent,
        TableModule,
        ButtonDirective,
        IconField,
        InputIcon,
        InputText,
    ],
})
export class ExamStudentsComponent implements OnDestroy {
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
    addStudentsDialog = viewChild.required(ExamAddStudentsDialogComponent);

    private routeData = toSignal(this.route.data, {
        initialValue: { exam: undefined as Exam | undefined },
    });

    readonly courseId = signal<number>(0);

    readonly exam = signal<Exam>(new Exam());
    readonly studentExams = signal<StudentExam[]>([]);
    readonly allRegisteredUsers = computed<ExamUser[]>(() => {
        const exam = this.exam();
        const studentExams = this.studentExams();
        const hasExamEnded = this.hasExamEnded();

        if (!exam?.examUsers) {
            return [];
        }

        if (hasExamEnded) {
            return exam.examUsers?.map((examUser) => {
                const studentExam = studentExams?.find((studentExam) => studentExam.user?.id === examUser.user!.id);
                return {
                    ...examUser.user!,
                    ...examUser,
                    didExamUserAttendExam: !!studentExam?.started,
                };
            });
        } else {
            return exam.examUsers?.map((examUser) => ({ ...examUser.user!, ...examUser }));
        }
    });

    readonly filteredUsersSize = signal(0);
    readonly rowClass = signal<string | undefined>(undefined);

    readonly isLoading = signal(true);
    readonly hasExamStarted = signal(false);
    readonly hasExamEnded = signal(false);
    readonly isSearching = signal(false);
    readonly searchFailed = signal(false);
    readonly searchNoResults = signal(false);
    readonly isTransitioning = signal(false);
    readonly isAdmin = signal(false);

    readonly isTestExam = computed(() => this.exam()?.testExam ?? false);

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

        if (hasExamEnded) {
            this.studentExamService.findAllForExam(this.courseId(), exam.id!).subscribe((res) => {
                this.studentExams.set(res.body || []);
                this.isLoading.set(false);
            });
        } else {
            this.studentExams.set([]); // Reset it just in case
            this.isLoading.set(false);
        }
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
                this.searchFailed.set(false);
                this.searchNoResults.set(false);
                if (loginOrName.length < 3) {
                    return of([]);
                }
                this.isSearching.set(true);
                return this.userService
                    .search(loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        tap((users) => {
                            if (users.length === 0) {
                                this.searchNoResults.set(true);
                            }
                        }),
                        catchError(() => {
                            this.searchFailed.set(true);
                            return of([]);
                        }),
                    );
            }),
            tap(() => {
                this.isSearching.set(false);
            }),
            tap((users) => {
                setTimeout(() => {
                    for (let i = 0; i < this.dataTable().typeaheadButtons.length; i++) {
                        const isAlreadyInCourseGroup = this.allRegisteredUsers()
                            .map((user) => user.id)
                            .includes(users[i].id);
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
        const examId = this.exam().id;

        // If the user is not registered for this exam yet, perform the server call to add them
        const userNotRegisteredForExam =
            !this.allRegisteredUsers()
                .map((eu) => eu.user!.id)
                .includes(user.id) &&
            user.login &&
            examId;
        if (userNotRegisteredForExam) {
            this.isTransitioning.set(true);
            this.examManagementService.addStudentToExam(this.courseId(), examId, user.login!).subscribe({
                next: () => {
                    this.isTransitioning.set(false);
                    this.reloadExamWithRegisteredUsers();
                    // Flash green background color to signal to the user that this student was registered
                    this.flashRowClass(cssClasses.newlyRegistered);
                },
                error: (error: HttpErrorResponse) => {
                    if (error.status === 403) {
                        this.onError(`artemisApp.exam.error.${error.error.errorKey}`);
                    }
                    this.isTransitioning.set(false);
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
     * Update the number of filtered users
     *
     * @param filteredUsersSize Total number of users after filters have been applied
     */
    handleUsersSizeChange = (filteredUsersSize: number) => {
        this.filteredUsersSize.set(filteredUsersSize);
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
        this.rowClass.set(className);
        setTimeout(() => this.rowClass.set(undefined), 500);
    };

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.alertService.error(error);
        this.isTransitioning.set(false);
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

    asExamUser(value: ExamUser | undefined) {
        return value as ExamUser | undefined;
    }
}
