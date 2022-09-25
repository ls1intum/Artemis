import { Component, Input, OnDestroy } from '@angular/core';
import { faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupRegistrationImportDTO } from 'app/entities/tutorial-group/tutorial-group-import-dto.model';
import { parse } from 'papaparse';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { StudentDTO } from 'app/entities/student-dto.model';
import { HttpResponse } from '@angular/common/http';

/**
 * Each row is a object with the structure
 * 	{
 * 		"Column Header 1": "foo",
 * 		"Column Header 2": "bar"
 * 	}
 */
type ParsedCSVRow = object;

const POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS = ['gruppe', 'titel', 'group', 'title'];
const POSSIBLE_REGISTRATION_NUMBER_HEADERS = ['registrationnumber', 'matriculationnumber', 'matrikelnummer', 'number'];
const POSSIBLE_LOGIN_HEADERS = ['login', 'user', 'username', 'benutzer', 'benutzername'];
const POSSIBLE_FIRST_NAME_HEADERS = ['firstname', 'firstnameofstudent', 'givenname', 'forename', 'vorname'];
const POSSIBLE_LAST_NAME_HEADERS = ['familyname', 'lastname', 'familynameofstudent', 'surname', 'nachname', 'familienname', 'name'];

@Component({
    selector: 'jhi-tutorial-groups-import-dialog',
    templateUrl: './tutorial-groups-registration-import-dialog.component.html',
    styleUrls: ['./tutorial-groups-registration-import-dialog.component.scss'],
})
export class TutorialGroupsRegistrationImportDialog implements OnDestroy {
    // ToDo: Implement filter for registrations that are not completed
    // ToDo: Maybe even return a special dto from the server that contains information why the registration was not imported

    @Input() courseId: number;

    registrationsToImport: TutorialGroupRegistrationImportDTO[] = [];
    notImportedRegistrations: TutorialGroupRegistrationImportDTO[] = [];

    isCSVParsing = false;
    validationError?: string;
    isImporting = false;
    isImportDone = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;
    faCircleNotch = faCircleNotch;
    faUpload = faUpload;

    constructor(private activeModal: NgbActiveModal, private alertService: AlertService, private tutorialGroupService: TutorialGroupsService) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    get numberOfRegistrationsImported(): number {
        return !this.isImportDone ? 0 : this.registrationsToImport.length - this.numberOfRegistrationsNotImported;
    }
    get numberOfRegistrationsNotImported(): number {
        return !this.isImportDone ? 0 : this.notImportedRegistrations.length;
    }

    get isSubmitDisabled(): boolean {
        return this.isImporting || !this.registrationsToImport?.length;
    }

    private resetDialog() {
        this.registrationsToImport = [];
        this.notImportedRegistrations = [];
        this.isImportDone = false;
    }

    async onCSVFileSelected(event: Event) {
        const target = event.target as HTMLInputElement;

        if (target.files && target.files.length > 0) {
            this.resetDialog();
            if (target.files[0]) {
                this.registrationsToImport = await this.readRegistrationsFromCSVFile(event, target.files[0]);
            }
        }
    }

    /**
     * Reads registrations from a csv file
     * The column "title" is mandatory, all other columns are optional
     * @param event File change event from the HTML input of type file
     * @param csvFile File that contains one registration per row
     */
    private async readRegistrationsFromCSVFile(event: Event, csvFile: File): Promise<TutorialGroupRegistrationImportDTO[]> {
        let csvRows: ParsedCSVRow[] = [];
        try {
            this.isCSVParsing = true;
            this.validationError = undefined;
            csvRows = await this.parseCSVFile(csvFile);
        } catch (error) {
            this.validationError = error.message;
        } finally {
            this.isCSVParsing = false;
        }
        if (csvRows.length > 0) {
            this.performExtraValidations(csvRows);
        }
        if (this.validationError) {
            (event.target as HTMLInputElement).value = ''; // remove selected file so user can fix the file and select it again
            return [];
        }
        // get the used headers from the first csv row object returned by the parser
        const parsedHeaders = Object.keys(csvRows.first() || []);

        // we find out which of the possible values is used in the csv file for the respective properties
        const usedTitleHeader = parsedHeaders.find((value) => POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS.includes(value)) || '';
        const usedRegistrationNumberHeader = parsedHeaders.find((value) => POSSIBLE_REGISTRATION_NUMBER_HEADERS.includes(value)) || '';
        const usedLoginHeader = parsedHeaders.find((value) => POSSIBLE_LOGIN_HEADERS.includes(value)) || '';
        const usedFirstNameHeader = parsedHeaders.find((value) => POSSIBLE_FIRST_NAME_HEADERS.includes(value)) || '';
        const usedLastNameHeader = parsedHeaders.find((value) => POSSIBLE_LAST_NAME_HEADERS.includes(value)) || '';

        // convert the 'raw' csv rows into a list of TutorialGroupImportDTOs
        return csvRows.map((csvRow) => {
            const registration: TutorialGroupRegistrationImportDTO = {
                title: csvRow[usedTitleHeader]?.trim() || '',
            } as TutorialGroupRegistrationImportDTO;
            registration.student = {
                registrationNumber: csvRow[usedRegistrationNumberHeader]?.trim() || '',
                login: csvRow[usedLoginHeader]?.trim() || '',
                firstName: csvRow[usedFirstNameHeader]?.trim() || '',
                lastName: csvRow[usedLastNameHeader]?.trim() || '',
            } as StudentDTO;

            return registration;
        });
    }

    import() {
        this.isImporting = true;
        this.tutorialGroupService.import(this.courseId, this.registrationsToImport).subscribe({
            next: (res) => this.onSaveSuccess(res),
            error: () => this.onSaveError(),
        });
    }

    /**
     * Performs validations on the parsed csv rows
     * - checks if values for the required column 'tutorial group title' are present
     *
     * @param csvRows Parsed list of users
     */
    performExtraValidations(csvRows: ParsedCSVRow[]): void {
        const invalidRows = this.computeInvalidUserEntries(csvRows);
        if (invalidRows !== null) {
            const maxLength = 30;
            this.validationError = invalidRows.length <= maxLength ? invalidRows : invalidRows.slice(0, maxLength) + '...';
        }
    }

    /**
     * Returns a comma separated list of row numbers that contains invalid registration entries, null if all entries are valid
     */
    computeInvalidUserEntries(csvUsers: ParsedCSVRow[]): string | null {
        const invalidList: number[] = [];
        for (const [i, user] of csvUsers.entries()) {
            const hasTutorialGroupTitle = this.checkIfEntryContainsKey(user, POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS);
            if (!hasTutorialGroupTitle) {
                // '+ 2' instead of '+ 1' due to the header column in the csv file
                invalidList.push(i + 2);
            }
        }
        return invalidList.length === 0 ? null : invalidList.join(', ');
    }

    /**
     * Checks if the csv row contains one of the supplied keys.
     * @param csvRow which should be checked if it contains one of the keys.
     * @param keys that should be checked for in the row.
     */
    checkIfEntryContainsKey(csvRow: ParsedCSVRow, keys: string[]): boolean {
        return keys.some((key) => csvRow[key] !== undefined && csvRow[key] !== '');
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        this.activeModal.close();
    }

    onSaveSuccess(notImportedRegistrations: HttpResponse<TutorialGroupRegistrationImportDTO[]>) {
        this.isImporting = false;
        this.isImportDone = true;
        this.notImportedRegistrations = notImportedRegistrations.body ?? [];
    }

    onSaveError() {
        this.alertService.error('artemisApp.pages.tutorialGroupsManagement.import.genericErrorMessage');
        this.isImporting = false;
    }

    wasImported(registration: TutorialGroupRegistrationImportDTO): boolean {
        return this.isImportDone && !this.wasNotImported(registration);
    }

    wasNotImported(registration: TutorialGroupRegistrationImportDTO): boolean {
        if (this.isImportDone && this.notImportedRegistrations?.length === 0) {
            return false;
        }

        // try to find a not imported registration that matches the given registration
        return this.notImportedRegistrations?.some((notImportedRegistration) => {
            const hasSameTutorialGroupTitle = notImportedRegistration.title === registration.title;
            if (!hasSameTutorialGroupTitle) {
                return false;
            } else {
                // if the tutorial group title matches, we check if the student data matches if it is present
                if (!registration.student?.registrationNumber && !notImportedRegistration.student?.registrationNumber) {
                    return notImportedRegistration.student.registrationNumber === registration.student.registrationNumber;
                }
                if (!registration.student?.login && !notImportedRegistration.student?.login) {
                    return notImportedRegistration.student.login === registration.student.login;
                }
                return true;
            }
        });
    }

    /**
     * Parses a csv file and returns a promise with a list of rows
     * @param csvFile File that should be parsed
     */
    private parseCSVFile(csvFile: File): Promise<ParsedCSVRow[]> {
        return new Promise((resolve, reject) => {
            parse(csvFile, {
                header: true,
                transformHeader: (header: string) => header.toLowerCase().replaceAll(' ', '').replaceAll('_', '').replaceAll('-', '').trim(),
                skipEmptyLines: true,
                complete: (results) => resolve(results.data as ParsedCSVRow[]),
                error: (error) => reject(error),
            });
        });
    }
}
