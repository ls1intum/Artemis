import { Component, Input, OnDestroy, ViewChild, ViewEncapsulation, inject } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpResponse } from '@angular/common/http';
import { ExamUserDTO } from 'app/exam/shared/entities/exam-user-dto.model';
import { cleanString } from 'app/shared/util/utils';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { parse } from 'papaparse';
import { faArrowRight, faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from '../../components/help-icon/help-icon.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { Student } from 'app/openapi/model/student';

const POSSIBLE_REGISTRATION_NUMBER_HEADERS = ['registrationnumber', 'matriculationnumber', 'matrikelnummer', 'number'];
const POSSIBLE_LOGIN_HEADERS = ['login', 'user', 'username', 'benutzer', 'benutzername'];
const POSSIBLE_EMAIL_HEADERS = ['email', 'e-mail', 'mail'];
const POSSIBLE_FIRST_NAME_HEADERS = ['firstname', 'firstnameofstudent', 'givenname', 'forename', 'vorname'];
const POSSIBLE_LAST_NAME_HEADERS = ['familyname', 'lastname', 'familynameofstudent', 'surname', 'nachname', 'familienname', 'name'];
const POSSIBLE_ROOM_HEADERS = ['actualroom', 'actualRoom', 'raum', 'room', 'Room'];
const POSSIBLE_SEAT_HEADERS = ['actualseat', 'actualSeat', 'sitzplatz', 'sitz', 'seat', 'Seat'];

interface CsvUser {
    [key: string]: string;
}

@Component({
    selector: 'jhi-users-import-dialog',
    templateUrl: './users-import-dialog.component.html',
    styleUrls: ['./users-import-dialog.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, FaIconComponent, HelpIconComponent],
})
export class UsersImportDialogComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private courseManagementService = inject(CourseManagementService);
    private adminUserService = inject(AdminUserService);
    private tutorialGroupService = inject(TutorialGroupsService);

    readonly ActionType = ActionType;

    @ViewChild('importForm', { static: false }) importForm: NgForm;

    @Input() courseId: number;
    @Input() courseGroup: string;
    @Input() exam: Exam | undefined;
    @Input() tutorialGroup: TutorialGroup | undefined;
    @Input() examUserMode: boolean;
    @Input() adminUserMode: boolean;

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

    // Icons
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
    }

    async onCSVFileSelect(event: any) {
        if (event.target.files.length > 0) {
            this.resetDialog();
            if (this.examUserMode) {
                this.examUsersToImport = await this.readUsersFromCSVFile(event, event.target.files[0]);
            } else {
                this.usersToImport = await this.readUsersFromCSVFile(event, event.target.files[0]);
            }
        }
    }

    /**
     * Reads users from a csv file into a list of StudentDTOs or ExamUserDTO if examUserMode is true
     * The column "registrationNumber" is mandatory since the import requires it as an identifier
     * @param event File change event from the HTML input of type file
     * @param csvFile File that contains one user per row and has at least the columns specified in csvColumns
     */
    private async readUsersFromCSVFile(event: any, csvFile: File): Promise<StudentDTO[] | ExamUserDTO[]> {
        let csvUsers: CsvUser[] = [];
        try {
            this.isParsing = true;
            this.validationError = undefined;
            csvUsers = await this.parseCSVFile(csvFile);
        } catch (error) {
            this.validationError = error.message;
        } finally {
            this.isParsing = false;
        }
        if (csvUsers.length > 0) {
            this.performExtraValidations(csvUsers);
        } else if (csvUsers.length === 0) {
            this.noUsersFoundError = true;
        }
        if (this.validationError || csvUsers.length === 0) {
            event.target.value = ''; // remove selected file so user can fix the file and select it again
            return [];
        }

        const usedHeaders = Object.keys(csvUsers.first() || []);

        const registrationNumberHeader = usedHeaders.find((value) => POSSIBLE_REGISTRATION_NUMBER_HEADERS.includes(value)) || '';
        const loginHeader = usedHeaders.find((value) => POSSIBLE_LOGIN_HEADERS.includes(value)) || '';
        const emailHeader = usedHeaders.find((value) => POSSIBLE_EMAIL_HEADERS.includes(value)) || '';
        const firstNameHeader = usedHeaders.find((value) => POSSIBLE_FIRST_NAME_HEADERS.includes(value)) || '';
        const lastNameHeader = usedHeaders.find((value) => POSSIBLE_LAST_NAME_HEADERS.includes(value)) || '';

        const roomHeader = usedHeaders.find((value) => POSSIBLE_ROOM_HEADERS.includes(value)) || '';
        const seatHeader = usedHeaders.find((value) => POSSIBLE_SEAT_HEADERS.includes(value)) || '';

        if (this.examUserMode) {
            return csvUsers.map(
                (user: CsvUser) =>
                    ({
                        registrationNumber: user[registrationNumberHeader]?.trim() || '',
                        login: user[loginHeader]?.trim() || '',
                        email: user[emailHeader]?.trim() || '',
                        firstName: user[firstNameHeader]?.trim() || '',
                        lastName: user[lastNameHeader]?.trim() || '',
                        room: user[roomHeader]?.trim() || '',
                        seat: user[seatHeader]?.trim() || '',
                    }) as ExamUserDTO,
            );
        } else {
            return csvUsers.map(
                (user: CsvUser) =>
                    ({
                        registrationNumber: user[registrationNumberHeader]?.trim() || '',
                        login: user[loginHeader]?.trim() || '',
                        email: user[emailHeader]?.trim() || '',
                        firstName: user[firstNameHeader]?.trim() || '',
                        lastName: user[lastNameHeader]?.trim() || '',
                    }) as StudentDTO,
            );
        }
    }

    /**
     * Performs validations on the parsed users
     * - checks if values for the required column {csvColumns.registrationNumber} are present
     *
     * @param csvUsers Parsed list of users
     */
    performExtraValidations(csvUsers: CsvUser[]) {
        const invalidUserEntries = this.computeInvalidUserEntries(csvUsers);
        if (invalidUserEntries) {
            const maxLength = 30;
            this.validationError = invalidUserEntries.length <= maxLength ? invalidUserEntries : invalidUserEntries.slice(0, maxLength) + '...';
        }
    }

    /**
     * Checks if the csv entry contains one of the supplied keys.
     * @param entry which should be checked if it contains one of the keys.
     * @param keys that should be checked for in the entry.
     */
    checkIfEntryContainsKey(entry: CsvUser, keys: string[]): boolean {
        return keys.some((key) => entry[key] !== undefined && entry[key] !== '');
    }

    /**
     * Returns a comma separated list of row numbers that contains invalid student entries
     * @param csvUsers Parsed list of users
     */
    computeInvalidUserEntries(csvUsers: CsvUser[]): string | undefined {
        const invalidList: number[] = [];
        for (const [i, user] of csvUsers.entries()) {
            const hasLogin = this.checkIfEntryContainsKey(user, POSSIBLE_LOGIN_HEADERS);
            const hasRegistrationNumber = this.checkIfEntryContainsKey(user, POSSIBLE_REGISTRATION_NUMBER_HEADERS);
            const hasEmail = this.checkIfEntryContainsKey(user, POSSIBLE_EMAIL_HEADERS);

            if (!hasLogin && !hasRegistrationNumber && !hasEmail) {
                // '+ 2' instead of '+ 1' due to the header column in the csv file
                invalidList.push(i + 2);
            }
        }
        return invalidList.length === 0 ? undefined : invalidList.join(', ');
    }

    /**
     * Parses a csv file and returns a promise with a list of rows
     * @param csvFile File that should be parsed
     */
    private parseCSVFile(csvFile: File): Promise<CsvUser[]> {
        return new Promise((resolve, reject) => {
            parse<CsvUser>(csvFile, {
                header: true,
                transformHeader: (header: string) => cleanString(header),
                skipEmptyLines: true,
                complete: (results) => resolve(results.data),
                error: (error) => reject(error),
            });
        });
    }

    /**
     * Sends the import request to the server with the list of users to be imported
     */
    importUsers() {
        this.isImporting = true;
        if (this.tutorialGroup) {
            this.tutorialGroupService.registerMultipleStudents(this.courseId, this.tutorialGroup.id!, this.usersToImport).subscribe({
                next: (res: HttpResponse<Array<Student>>) => {
                    const convertedHttpResponse = this.convertGeneratedDtoToNonGenerated(res);
                    this.onSaveSuccess(convertedHttpResponse);
                },
                error: () => this.onSaveError(),
            });
        } else if (this.courseGroup && !this.exam) {
            this.courseManagementService.addUsersToGroupInCourse(this.courseId, this.usersToImport, this.courseGroup).subscribe({
                next: (res) => this.onSaveSuccess(res),
                error: () => this.onSaveError(),
            });
        } else if (!this.courseGroup && this.exam) {
            this.examManagementService.addStudentsToExam(this.courseId, this.exam.id!, this.examUsersToImport).subscribe({
                next: (res) => this.onSaveSuccess(res),
                error: () => this.onSaveError(),
            });
        } else if (this.adminUserMode) {
            // convert StudentDTO to User
            const artemisUsers = this.usersToImport.map((student) => Object.assign({}, student, { visibleRegistrationNumber: student.registrationNumber }));
            this.adminUserService.importAll(artemisUsers).subscribe({
                next: (res) => {
                    const convertedRes = new HttpResponse({
                        body: res.body?.map((user) => Object.assign({}, user, { registrationNumber: user.visibleRegistrationNumber })),
                    });
                    this.onSaveSuccess(convertedRes);
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
     * @param response The HttpResponse containing the DTOs converted to @link{StudentDTO}
     */
    private convertGeneratedDtoToNonGenerated(response: HttpResponse<Array<Student>>): HttpResponse<Partial<StudentDTO>[]> {
        const nonGeneratedDtos: Partial<StudentDTO>[] = [];
        if (response.body) {
            for (const student of response.body) {
                nonGeneratedDtos.push({
                    login: student.login,
                    firstName: student.firstName,
                    lastName: student.lastName,
                    registrationNumber: student.registrationNumber,
                    email: student.email,
                });
            }
        }
        return new HttpResponse<Partial<StudentDTO>[]>({
            body: nonGeneratedDtos,
            headers: response.headers,
            status: response.status,
            statusText: response.statusText,
            url: response.url ?? undefined,
        });
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
            : this.examUserMode
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
        return this.examUserMode ? this.isImporting || !this.examUsersToImport?.length : this.isImporting || !this.usersToImport?.length;
    }

    /**
     * Callback method that is called when the import request was successful
     * @param {HttpResponse<StudentDTO[]>} notFoundUsers - List of users that could NOT be imported since they were not found
     */
    onSaveSuccess(notFoundUsers: HttpResponse<Partial<StudentDTO>[]>) {
        this.isImporting = false;
        this.hasImported = true;
        this.notFoundUsers = notFoundUsers.body! || [];
    }

    /**
     * Callback method that is called when the import request failed
     */
    onSaveError() {
        this.alertService.error('artemisApp.importUsers.genericErrorMessage');
        this.isImporting = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        this.activeModal.close();
    }
}
