import { Component, Input, OnDestroy, ViewChild, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { StudentDTO } from 'app/entities/student-dto.model';
import { parse } from 'papaparse';
import { faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/tutorial-groups.service';

const POSSIBLE_REGISTRATION_NUMBER_HEADERS = ['registrationnumber', 'matriculationnumber', 'matrikelnummer', 'number'];
const POSSIBLE_LOGIN_HEADERS = ['login', 'user', 'username', 'benutzer', 'benutzername'];
const POSSIBLE_FIRST_NAME_HEADERS = ['firstname', 'firstnameofstudent', 'givenname', 'forename', 'vorname'];
const POSSIBLE_LAST_NAME_HEADERS = ['familyname', 'lastname', 'familynameofstudent', 'surname', 'nachname', 'familienname', 'name'];

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
    @Input() tutorialGroup: TutorialGroup | undefined;

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
        private tutorialGroupService: TutorialGroupsService,
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

        const usedHeaders = Object.keys(csvUsers.first() || []);

        const registrationNumberHeader = usedHeaders.find((value) => POSSIBLE_REGISTRATION_NUMBER_HEADERS.includes(value)) || '';
        const loginHeader = usedHeaders.find((value) => POSSIBLE_LOGIN_HEADERS.includes(value)) || '';
        const firstNameHeader = usedHeaders.find((value) => POSSIBLE_FIRST_NAME_HEADERS.includes(value)) || '';
        const lastNameHeader = usedHeaders.find((value) => POSSIBLE_LAST_NAME_HEADERS.includes(value)) || '';

        return csvUsers.map(
            (users) =>
                ({
                    registrationNumber: users[registrationNumberHeader]?.trim() || '',
                    login: users[loginHeader]?.trim() || '',
                    firstName: users[firstNameHeader]?.trim() || '',
                    lastName: users[lastNameHeader]?.trim() || '',
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
            if (!hasLogin && !hasRegistrationNumber) {
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
                transformHeader: (header: string) => header.toLowerCase().replaceAll(' ', '').replaceAll('_', '').replaceAll('-', ''),
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
        if (this.tutorialGroup) {
            this.tutorialGroupService.registerMultipleStudents(this.courseId, this.tutorialGroup.id!, this.usersToImport).subscribe({
                next: (res) => this.onSaveSuccess(res),
                error: () => this.onSaveError(),
            });
        } else if (this.courseGroup && !this.exam) {
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
