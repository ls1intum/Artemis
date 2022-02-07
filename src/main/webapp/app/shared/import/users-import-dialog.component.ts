import { Component, Input, OnDestroy, ViewChild, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { StudentDTO } from 'app/entities/student-dto.model';
import { parse } from 'papaparse';
import { faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';

const csvColumns = Object.freeze({
    registrationNumber: 'registrationnumber',
    matrikelNummer: 'matrikelnummer',
    matriculationNumber: 'matriculationnumber',
    firstNameOfUser: 'firstname',
    familyNameOfUser: 'familyname',
    firstName: 'firstname',
    familyName: 'familyname',
    lastName: 'lastname',
    login: 'login',
    username: 'username',
    user: 'user',
    benutzer: 'benutzer',
    benutzerName: 'benutzername',
});

type CsvUser = object;

@Component({
    selector: 'jhi-users-import-dialog',
    templateUrl: './users-import-dialog.component.html',
    styleUrls: ['./users-import-dialog.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class UsersImportDialogComponent implements OnDestroy {
    readonly ActionType = ActionType;

    @ViewChild('importForm', { static: false }) importForm: NgForm;

    @Input() courseId: number;
    @Input() courseGroup: string;
    @Input() exam: Exam | undefined;

    usersToImport: StudentDTO[] = [];
    notFoundUsers: StudentDTO[] = [];

    isParsing = false;
    validationError?: string;
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

    constructor(
        private activeModal: NgbActiveModal,
        private alertService: AlertService,
        private examManagementService: ExamManagementService,
        private courseManagementService: CourseManagementService,
        private translateService: TranslateService,
    ) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    private resetDialog() {
        this.usersToImport = [];
        this.notFoundUsers = [];
        this.hasImported = false;
    }

    async onCSVFileSelect(event: any) {
        if (event.target.files.length > 0) {
            this.resetDialog();
            this.usersToImport = await this.readUsersFromCSVFile(event, event.target.files[0]);
        }
    }

    /**
     * Reads users from a csv file into a list of StudentDTOs
     * The column "registrationNumber" is mandatory since the import requires it as an identifier
     * @param event File change event from the HTML input of type file
     * @param csvFile File that contains one user per row and has at least the columns specified in csvColumns
     */
    private async readUsersFromCSVFile(event: any, csvFile: File): Promise<StudentDTO[]> {
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
            this.performExtraValidations(csvFile, csvUsers);
        }
        if (this.validationError) {
            event.target.value = ''; // remove selected file so user can fix the file and select it again
            return [];
        }
        return csvUsers.map(
            (users) =>
                ({
                    registrationNumber: users[csvColumns.registrationNumber] || users[csvColumns.matrikelNummer] || users[csvColumns.matriculationNumber] || '',
                    login: users[csvColumns.login] || users[csvColumns.username] || users[csvColumns.user] || users[csvColumns.benutzer] || users[csvColumns.benutzerName] || '',
                    firstName: users[csvColumns.firstName] || users[csvColumns.firstNameOfUser] || '',
                    lastName: users[csvColumns.lastName] || users[csvColumns.familyName] || users[csvColumns.familyNameOfUser] || '',
                } as StudentDTO),
        );
    }

    /**
     * Performs validations on the parsed users
     * - checks if values for the required column {csvColumns.registrationNumber} are present
     *
     * @param csvFile File that contains one user per row and has at least the columns specified in csvColumns
     * @param csvUsers Parsed list of users
     */
    performExtraValidations(csvFile: File, csvUsers: CsvUser[]) {
        const invalidUserEntries = this.computeInvalidUserEntries(csvUsers);
        if (invalidUserEntries) {
            const maxLength = 30;
            const entriesFormatted = invalidUserEntries.length <= maxLength ? invalidUserEntries : invalidUserEntries.slice(0, maxLength) + '...';
            this.validationError = entriesFormatted;
        }
    }

    /**
     * Returns a comma separated list of row numbers that contains invalid student entries
     * @param csvUsers Parsed list of users
     */
    computeInvalidUserEntries(csvUsers: CsvUser[]): string | undefined {
        const invalidList: number[] = [];
        for (const [i, user] of csvUsers.entries()) {
            if (
                !user[csvColumns.registrationNumber] &&
                !user[csvColumns.matrikelNummer] &&
                !user[csvColumns.matriculationNumber] &&
                !user[csvColumns.login] &&
                !user[csvColumns.user] &&
                !user[csvColumns.username] &&
                !user[csvColumns.benutzer] &&
                !user[csvColumns.benutzerName]
            ) {
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
            parse(csvFile, {
                header: true,
                transformHeader: (header: string) => header.toLowerCase().replace(' ', '').replace('_', ''),
                skipEmptyLines: true,
                complete: (results) => resolve(results.data as CsvUser[]),
                error: (error) => reject(error),
            });
        });
    }

    /**
     * Sends the import request to the server with the list of users to be imported
     */
    importUsers() {
        this.isImporting = true;
        if (this.courseGroup && !this.exam) {
            this.courseManagementService.addUsersToGroupInCourse(this.courseId, this.usersToImport, this.courseGroup).subscribe({
                next: (res) => this.onSaveSuccess(res),
                error: () => this.onSaveError(),
            });
        } else if (!this.courseGroup && this.exam) {
            this.examManagementService.addStudentsToExam(this.courseId, this.exam.id!, this.usersToImport).subscribe({
                next: (res) => this.onSaveSuccess(res),
                error: () => this.onSaveError(),
            });
        } else {
            this.alertService.error('importUsers.genericErrorMessage');
        }
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
                (notFound.registrationNumber?.length > 0 && notFound.registrationNumber === user.registrationNumber) ||
                (notFound.login?.length > 0 && notFound.login === user.login)
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
        return !this.hasImported ? 0 : this.usersToImport.length - this.numberOfUsersNotImported;
    }

    /**
     * Number of users which could not be imported
     */
    get numberOfUsersNotImported(): number {
        return !this.hasImported ? 0 : this.notFoundUsers.length;
    }

    get isSubmitDisabled(): boolean {
        return this.isImporting || !this.usersToImport?.length;
    }

    /**
     * Callback method that is called when the import request was successful
     * @param {HttpResponse<StudentDTO[]>} notFoundUsers - List of users that could NOT be imported since they were not found
     */
    onSaveSuccess(notFoundUsers: HttpResponse<StudentDTO[]>) {
        this.isImporting = false;
        this.hasImported = true;
        this.notFoundUsers = notFoundUsers.body! || [];
    }

    /**
     * Callback method that is called when the import request failed
     */
    onSaveError() {
        this.alertService.error('importUsers.genericErrorMessage');
        this.isImporting = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        this.activeModal.close();
    }
}
