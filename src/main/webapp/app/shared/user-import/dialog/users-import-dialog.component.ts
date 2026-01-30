import { Component, OnDestroy, ViewChild, ViewEncapsulation, inject, input, output, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { AlertService } from 'app/shared/service/alert.service';
import { DialogModule } from 'primeng/dialog';
import { HttpResponse } from '@angular/common/http';
import { ExamUserDTO } from 'app/exam/shared/entities/exam-user-dto.model';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { faArrowRight, faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from '../../components/help-icon/help-icon.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { Student } from 'app/openapi/model/student';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PrimeTemplate } from 'primeng/api';
import { readExamUserDTOsFromCSVFile, readStudentDTOsFromCSVFile } from 'app/shared/user-import/helpers/read-users-from-csv';

@Component({
    selector: 'jhi-users-import-dialog',
    templateUrl: './users-import-dialog.component.html',
    styleUrls: ['./users-import-dialog.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, FaIconComponent, HelpIconComponent, DialogModule, ArtemisTranslatePipe, PrimeTemplate],
})
export class UsersImportDialogComponent implements OnDestroy {
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private courseManagementService = inject(CourseManagementService);
    private adminUserService = inject(AdminUserService);
    private tutorialGroupService = inject(TutorialGroupsService);

    readonly ActionType = ActionType;
    readonly dialogVisible = signal<boolean>(false);
    readonly importCompleted = output<void>();

    @ViewChild('importForm', { static: false }) importForm: NgForm;

    courseId = input<number>();
    courseGroup = input<string>();
    exam = input<Exam | undefined>();
    tutorialGroup = input<TutorialGroup | undefined>();
    examUserMode = input<boolean>(false);
    adminUserMode = input<boolean>(false);

    usersToImport: StudentDTO[] = [];
    examUsersToImport: ExamUserDTO[] = [];
    notFoundUsers: Partial<StudentDTO>[] = [];

    isParsing = false;
    validationError?: string;
    noUsersFoundError?: boolean;
    isImporting = false;
    hasImported = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;
    faCircleNotch = faCircleNotch;
    faUpload = faUpload;
    faArrowRight = faArrowRight;

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    private resetDialog() {
        this.usersToImport = [];
        this.examUsersToImport = [];
        this.notFoundUsers = [];
        this.hasImported = false;
        this.validationError = undefined;
        this.noUsersFoundError = undefined;
    }

    async onCSVFileSelect(event: any) {
        if (event.target.files.length > 0) {
            this.resetDialog();
            const file = event.target.files[0];
            this.isParsing = true;

            if (this.examUserMode()) {
                const result = await readExamUserDTOsFromCSVFile(file);
                this.isParsing = false;
                if (!result.ok) {
                    this.validationError = result.invalidRowIndices.join(', ');
                    event.target.value = '';
                    return;
                }

                const examUsers = result.examUsers;
                if (examUsers.length === 0) {
                    this.noUsersFoundError = true;
                    event.target.value = '';
                    return;
                }

                this.examUsersToImport = result.examUsers;
            } else {
                const result = await readStudentDTOsFromCSVFile(file);
                this.isParsing = false;
                if (!result.ok) {
                    this.validationError = result.invalidRowIndices.join(', ');
                    event.target.value = '';
                    return;
                }

                const students = result.students;
                if (students.length === 0) {
                    this.noUsersFoundError = true;
                    event.target.value = '';
                    return;
                }

                this.usersToImport = result.students;
            }
        }
    }

    /**
     * Sends the import request to the server with the list of users to be imported
     */
    importUsers() {
        this.isImporting = true;
        const tutorialGroup = this.tutorialGroup();
        const courseGroup = this.courseGroup();
        const exam = this.exam();
        const courseId = this.courseId();

        if (tutorialGroup) {
            this.tutorialGroupService.registerMultipleStudents(courseId!, tutorialGroup.id!, this.usersToImport).subscribe({
                next: (res: HttpResponse<Array<Student>>) => {
                    const convertedStudents = this.convertGeneratedDtoToNonGenerated(res.body || []);
                    this.onSaveSuccess(convertedStudents);
                },
                error: () => this.onSaveError(),
            });
        } else if (courseGroup && !exam) {
            this.courseManagementService.addUsersToGroupInCourse(courseId!, this.usersToImport, courseGroup).subscribe({
                next: (res) => this.onSaveSuccess(res.body || []),
                error: () => this.onSaveError(),
            });
        } else if (!courseGroup && exam) {
            this.examManagementService.addStudentsToExam(courseId!, exam.id!, this.examUsersToImport).subscribe({
                next: (res) => this.onSaveSuccess(res.body || []),
                error: () => this.onSaveError(),
            });
        } else if (this.adminUserMode()) {
            // convert StudentDTO to User
            const artemisUsers = this.usersToImport.map((student) => ({ ...student, visibleRegistrationNumber: student.registrationNumber }));
            this.adminUserService.importAll(artemisUsers).subscribe({
                next: (res) => {
                    const convertedStudents =
                        res.body?.map((user) => ({
                            ...user,
                            registrationNumber: user.visibleRegistrationNumber,
                        })) || [];
                    this.onSaveSuccess(convertedStudents);
                },
                error: () => this.onSaveError(),
            });
        } else {
            this.alertService.error('artemisApp.importUsers.genericErrorMessage');
        }
    }

    /**
     * Helper method to convert the generated Student DTOs to non-generated StudentDTOs
     * This is needed as long as not all methods in this component are converted to use the generated Student DTO.
     * @param response DTOs converted to @link{StudentDTO}
     */
    private convertGeneratedDtoToNonGenerated(response: Array<Student>): Partial<StudentDTO>[] {
        const nonGeneratedDtos: Partial<StudentDTO>[] = [];
        if (response) {
            for (const student of response) {
                nonGeneratedDtos.push({
                    login: student.login,
                    firstName: student.firstName,
                    lastName: student.lastName,
                    registrationNumber: student.registrationNumber,
                    email: student.email,
                });
            }
        }
        return nonGeneratedDtos;
    }

    /**
     * True if this user was successfully imported, false otherwise
     * @param user The user to be checked
     */
    wasImported(user: StudentDTO): boolean {
        return this.hasImported && !this.wasNotImported(user);
    }

    /**
     * True if this user could not be imported, false otherwise
     * @param user The user to be checked
     */
    wasNotImported(user: StudentDTO): boolean {
        if (this.hasImported && this.notFoundUsers?.length === 0) {
            return false;
        }

        for (const notFound of this.notFoundUsers) {
            if (
                (notFound.registrationNumber?.length && notFound.registrationNumber === user.registrationNumber) ||
                (notFound.login?.length && notFound.login === user.login) ||
                (notFound.email?.length && notFound.email === user.email)
            ) {
                return true;
            }
        }

        return false;
    }

    /**
     * Number of Users that were successfully imported
     */
    get numberOfUsersImported(): number {
        return !this.hasImported
            ? 0
            : this.examUserMode()
              ? this.examUsersToImport.length - this.numberOfUsersNotImported
              : this.usersToImport.length - this.numberOfUsersNotImported;
    }

    /**
     * Number of users which could not be imported
     */
    get numberOfUsersNotImported(): number {
        return !this.hasImported ? 0 : this.notFoundUsers.length;
    }

    get isSubmitDisabled(): boolean {
        return this.examUserMode() ? this.isImporting || !this.examUsersToImport?.length : this.isImporting || !this.usersToImport?.length;
    }

    /**
     * Callback method that is called when the import request was successful
     * @param notFoundUsers - List of users that could NOT be imported since they were not found
     */
    onSaveSuccess(notFoundUsers: Partial<StudentDTO>[]) {
        this.isImporting = false;
        this.hasImported = true;
        this.notFoundUsers = notFoundUsers || [];
    }

    /**
     * Callback method that is called when the import request failed
     */
    onSaveError() {
        this.alertService.error('artemisApp.importUsers.genericErrorMessage');
        this.isImporting = false;
    }

    open(): void {
        this.resetDialog();
        this.dialogVisible.set(true);
    }

    close(): void {
        this.dialogVisible.set(false);
    }

    clear() {
        this.close();
    }

    onFinish() {
        this.close();
        this.importCompleted.emit();
    }
}
