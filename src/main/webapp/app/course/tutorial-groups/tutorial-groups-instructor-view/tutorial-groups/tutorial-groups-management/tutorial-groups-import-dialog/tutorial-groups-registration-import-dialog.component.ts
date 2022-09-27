import { Component, ElementRef, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupRegistrationImportDTO } from 'app/entities/tutorial-group/tutorial-group-import-dto.model';
import { parse } from 'papaparse';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject, Subscription } from 'rxjs';
import { StudentDTO } from 'app/entities/student-dto.model';
import { HttpResponse } from '@angular/common/http';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';

/**
 * Each row is a object with the structure
 * 	{
 * 		"Column Header 1": "foo",
 * 		"Column Header 2": "bar"
 * 	}
 */
type ParsedCSVRow = object;

// ToDo: Idea for future: Let the specify the column names / values in the dialog
const POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS = ['gruppe', 'titel', 'group', 'title'];
const POSSIBLE_REGISTRATION_NUMBER_HEADERS = ['registrationnumber', 'matriculationnumber', 'matrikelnummer', 'number'];
const POSSIBLE_LOGIN_HEADERS = ['login', 'user', 'username', 'benutzer', 'benutzername'];
const POSSIBLE_FIRST_NAME_HEADERS = ['firstname', 'firstnameofstudent', 'givenname', 'forename', 'vorname'];
const POSSIBLE_LAST_NAME_HEADERS = ['familyname', 'lastname', 'familynameofstudent', 'surname', 'nachname', 'familienname', 'name'];

function cleanString(str?: string): string {
    if (!str) {
        return '';
    }
    return str.toLowerCase().replaceAll(' ', '').replaceAll('_', '').replaceAll('-', '').trim();
}

type filterValues = 'all' | 'onlyImported' | 'onlyNotImported';

@Component({
    selector: 'jhi-tutorial-groups-import-dialog',
    templateUrl: './tutorial-groups-registration-import-dialog.component.html',
    styleUrls: ['./tutorial-groups-registration-import-dialog.component.scss'],
})
export class TutorialGroupsRegistrationImportDialog implements OnInit, OnDestroy {
    // ToDo: Implement filter for registrations that are not completed

    @ViewChild('fileInput') fileInput: ElementRef<HTMLInputElement>;
    selectedFile?: File;

    @Input() courseId: number;

    registrationsDisplayedInTable: TutorialGroupRegistrationImportDTO[] = [];
    allRegistrations: TutorialGroupRegistrationImportDTO[] = [];
    notImportedRegistrations: TutorialGroupRegistrationImportDTO[] = [];
    importedRegistrations: TutorialGroupRegistrationImportDTO[] = [];

    isCSVParsing = false;
    validationErrors: string[] = [];
    isImporting = false;
    isImportDone = false;
    numberOfImportedRegistrations = 0;
    numberOfNotImportedRegistration = 0;

    showOnlyNotImported = false;

    private subscriptions: (Subscription | undefined)[] = [];
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    supportedTitleHeader = POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS.join(', ');
    supportedRegistrationNumberHeaders = POSSIBLE_REGISTRATION_NUMBER_HEADERS.join(', ');
    supportedLoginHeaders = POSSIBLE_LOGIN_HEADERS.join(', ');
    supportedFirstNameHeaders = POSSIBLE_FIRST_NAME_HEADERS.join(', ');
    supportedLastNameHeaders = POSSIBLE_LAST_NAME_HEADERS.join(', ');

    fixedPlaceForm: FormGroup;

    get statusHeaderControl() {
        return this.fixedPlaceForm.get('statusHeader');
    }
    get fixedPlaceValueControl() {
        return this.fixedPlaceForm.get('fixedPlaceValue');
    }

    get specifyFixedPlaceControl() {
        return this.fixedPlaceForm.get('specifyFixedPlace');
    }

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;
    faCircleNotch = faCircleNotch;
    faUpload = faUpload;
    selectedFilter: filterValues = 'all';

    constructor(
        private fb: FormBuilder,
        private translateService: TranslateService,
        private activeModal: NgbActiveModal,
        private alertService: AlertService,
        private tutorialGroupService: TutorialGroupsService,
    ) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
        this.subscriptions.forEach((sub) => sub?.unsubscribe());
    }

    ngOnInit(): void {
        this.fixedPlaceForm = this.fb.group({
            specifyFixedPlace: [false],
            statusHeader: ['', [Validators.maxLength(255)]],
            fixedPlaceValue: ['', [Validators.maxLength(255)]],
        });
        this.fixedPlaceValueControl?.disable();
        this.statusHeaderControl?.disable();
        this.onStatusChanges();
        this.onFixedPlaceCheckboxChange();
    }

    onStatusChanges() {
        this.subscriptions.push(
            this.statusHeaderControl?.valueChanges.subscribe((selectedStatusColumn) => {
                if (!selectedStatusColumn) {
                    this.fixedPlaceValueControl?.reset();
                    this.fixedPlaceValueControl?.disable();
                } else {
                    this.fixedPlaceValueControl?.enable();
                }
            }),
        );
    }

    onFixedPlaceCheckboxChange() {
        this.subscriptions.push(
            this.specifyFixedPlaceControl?.valueChanges.subscribe((specifyFixedPlace) => {
                this.fixedPlaceValueControl?.reset();
                this.statusHeaderControl?.reset();
                if (specifyFixedPlace) {
                    this.statusHeaderControl?.enable();
                } else {
                    this.statusHeaderControl?.disable();
                }
            }),
        );
    }

    get isParseDisabled() {
        return (
            this.selectedFile === undefined ||
            this.isCSVParsing ||
            this.isImporting ||
            (this.specifyFixedPlaceControl?.value && (!this.statusHeaderControl?.value || !this.fixedPlaceValueControl?.value)) ||
            this.fixedPlaceForm.invalid
        );
    }

    get isSubmitDisabled(): boolean {
        return this.isImporting || !this.registrationsDisplayedInTable?.length;
    }
    private resetDialog() {
        this.registrationsDisplayedInTable = [];
        this.allRegistrations = [];
        this.notImportedRegistrations = [];
        this.validationErrors = [];
        this.isImportDone = false;
        this.isImporting = false;
        this.isCSVParsing = false;
        this.numberOfImportedRegistrations = 0;
        this.numberOfNotImportedRegistration = 0;
    }

    onCSVFileSelected(event: Event) {
        const target = event.target as HTMLInputElement;

        if (target.files && target.files.length > 0) {
            this.resetDialog();
            if (target.files[0]) {
                this.selectedFile = target.files[0];
            }
        }
    }

    /**
     * Reads registrations from a csv file
     * The column "title" is mandatory, all other columns are optional
     * @param csvFile File that contains one registration per row
     */
    private async readRegistrationsFromCSVFile(csvFile: File): Promise<TutorialGroupRegistrationImportDTO[]> {
        let csvRows: ParsedCSVRow[] = [];
        try {
            this.isCSVParsing = true;
            this.validationErrors = [];
            csvRows = await this.parseCSVFile(csvFile);
        } catch (error) {
            this.validationErrors.push(error.message);
        } finally {
            this.isCSVParsing = false;
        }
        if (csvRows.length > 0) {
            this.performExtraRowValidation(csvRows);
        }
        if (this.validationErrors && this.validationErrors.length > 0) {
            this.fileInput.nativeElement.value = ''; // remove selected file so user can fix the file and select it again
            this.selectedFile = undefined;
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

        // if status header is used filter out those rows that do not have a fixed place
        const statusColumn = cleanString(this.statusHeaderControl?.value);
        const fixedPlaceValue = cleanString(this.fixedPlaceValueControl?.value);

        const csvFixedPlaceRows = csvRows.filter((row) => !statusColumn || !fixedPlaceValue || cleanString(row[statusColumn]) === fixedPlaceValue);
        // convert the 'raw' csv rows into a list of TutorialGroupImportDTOs
        const registrations = csvFixedPlaceRows
            .map((csvRow) => {
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
            })
            .sort((a, b) => a.title.localeCompare(b.title));

        this.performExtraDTOValidation(registrations);
        if (this.validationErrors && this.validationErrors.length > 0) {
            this.fileInput.nativeElement.value = ''; // remove selected file so user can fix the file and select it again
            this.selectedFile = undefined;
            return [];
        } else {
            return registrations;
        }
    }

    import() {
        this.isImporting = true;
        this.tutorialGroupService.import(this.courseId, this.registrationsDisplayedInTable).subscribe({
            next: (res) => this.onSaveSuccess(res),
            error: () => this.onSaveError(),
        });
    }

    /**
     * Performs validations on the parsed csv rows
     * - checks if values for the required column 'tutorial group title' are present
     *
     * @param csvRows Parsed list of rows
     */
    performExtraRowValidation(csvRows: ParsedCSVRow[]): void {
        const titleValidationError = this.withoutTitleValidation(csvRows);
        const withoutIdentifierValidationError = this.withoutIdentifierValidation(csvRows);
        const maxLength = 1000;
        if (titleValidationError !== null) {
            this.validationErrors.push(titleValidationError.length <= maxLength ? titleValidationError : titleValidationError.slice(0, maxLength) + '...');
        }
        if (withoutIdentifierValidationError !== null) {
            this.validationErrors.push(
                withoutIdentifierValidationError.length <= maxLength ? withoutIdentifierValidationError : withoutIdentifierValidationError.slice(0, maxLength) + '...',
            );
        }
    }

    performExtraDTOValidation(registrations: TutorialGroupRegistrationImportDTO[]): void {
        const duplicatedRegistrationNumbers = this.duplicatedRegistrationNumbers(registrations);
        const maxLength = 1000;
        if (duplicatedRegistrationNumbers !== null) {
            this.validationErrors.push(
                duplicatedRegistrationNumbers.length <= maxLength ? duplicatedRegistrationNumbers : duplicatedRegistrationNumbers.slice(0, maxLength) + '...',
            );
        }
        const duplicatedLogins = this.duplicatedLogins(registrations);
        if (duplicatedLogins !== null) {
            this.validationErrors.push(duplicatedLogins.length <= maxLength ? duplicatedLogins : duplicatedLogins.slice(0, maxLength) + '...');
        }
    }
    withoutTitleValidation(csvRows: ParsedCSVRow[]): string | null {
        const invalidList: number[] = [];
        for (const [i, row] of csvRows.entries()) {
            const hasTutorialGroupTitle = this.checkIfRowContainsKey(row, POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS);
            if (!hasTutorialGroupTitle) {
                // '+ 2' instead of '+ 1' due to the header column in the csv file
                invalidList.push(i + 2);
            }
        }
        return invalidList.length === 0 ? null : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.withoutTitle') + invalidList.join(', ');
    }

    withoutIdentifierValidation(csvRows: ParsedCSVRow[]): string | null {
        const invalidList: number[] = [];
        for (const [i, user] of csvRows.entries()) {
            const specifiesAUser = this.checkIfRowContainsKey(user, POSSIBLE_FIRST_NAME_HEADERS) || this.checkIfRowContainsKey(user, POSSIBLE_LAST_NAME_HEADERS);
            const specifiesARegistrationNumber = this.checkIfRowContainsKey(user, POSSIBLE_REGISTRATION_NUMBER_HEADERS);
            const specifiesALogin = this.checkIfRowContainsKey(user, POSSIBLE_LOGIN_HEADERS);

            if (specifiesAUser && !(specifiesARegistrationNumber || specifiesALogin)) {
                // '+ 2' instead of '+ 1' due to the header column in the csv file
                invalidList.push(i + 2);
            }
        }
        return invalidList.length === 0
            ? null
            : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.noIdentificationInformation') + invalidList.join(', ');
    }

    duplicatedRegistrationNumbers(registrations: TutorialGroupRegistrationImportDTO[]): string | null {
        const duplicatedRegistrationNumbers: string[] = [];
        const registrationNumbers = registrations.map((registration) => registration.student?.registrationNumber).filter((registrationNumber) => registrationNumber);

        const uniqueRegistrationNumbers = [...new Set(registrationNumbers)];

        uniqueRegistrationNumbers.forEach((registrationNumber) => {
            if (registrationNumbers.filter((rn) => rn === registrationNumber).length > 1) {
                duplicatedRegistrationNumbers.push(registrationNumber);
            }
        });

        return duplicatedRegistrationNumbers.length === 0
            ? null
            : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.duplicatedRegistrationNumbers') + duplicatedRegistrationNumbers.join(', ');
    }

    duplicatedLogins(registrations: TutorialGroupRegistrationImportDTO[]): string | null {
        const duplicatedLogins: string[] = [];
        const logins = registrations.map((registration) => registration.student?.login).filter((login) => login);

        const uniqueLogins = [...new Set(logins)];

        uniqueLogins.forEach((login) => {
            if (logins.filter((l) => l === login).length > 1) {
                duplicatedLogins.push(login);
            }
        });

        return duplicatedLogins.length === 0
            ? null
            : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.duplicatedLogins') + duplicatedLogins.join(', ');
    }

    /**
     * Checks if the csv row contains one of the supplied keys.
     * @param csvRow which should be checked if it contains one of the keys.
     * @param keys that should be checked for in the row.
     */
    checkIfRowContainsKey(csvRow: ParsedCSVRow, keys: string[]): boolean {
        return keys.some((key) => csvRow[key] !== undefined && csvRow[key] !== '');
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        this.activeModal.close();
    }

    onFilterPress() {
        this.showOnlyNotImported = !this.showOnlyNotImported;
        if (this.showOnlyNotImported) {
            this.registrationsDisplayedInTable = this.notImportedRegistrations;
        } else {
            this.registrationsDisplayedInTable = this.allRegistrations;
        }
    }

    onSaveSuccess(registrations: HttpResponse<TutorialGroupRegistrationImportDTO[]>) {
        this.isImporting = false;
        this.isImportDone = true;
        this.registrationsDisplayedInTable = registrations.body ?? [];
        this.registrationsDisplayedInTable = this.registrationsDisplayedInTable.sort((a, b) => a.title.localeCompare(b.title));
        this.allRegistrations = this.registrationsDisplayedInTable;
        this.notImportedRegistrations = this.allRegistrations.filter((registration) => registration.importSuccessful !== true);
        this.importedRegistrations = this.allRegistrations.filter((registration) => registration.importSuccessful === true);
        this.numberOfNotImportedRegistration = this.notImportedRegistrations.length;
        this.numberOfImportedRegistrations = this.importedRegistrations.length;
    }

    onSaveError() {
        this.alertService.error('artemisApp.tutorialGroupImportDialog.errorMessages.genericErrorMessage');
        this.isImporting = false;
    }

    wasImported(registration: TutorialGroupRegistrationImportDTO): boolean {
        return this.isImportDone && registration.importSuccessful === true;
    }

    wasNotImported(registration: TutorialGroupRegistrationImportDTO): boolean {
        return this.isImportDone && !this.wasImported(registration);
    }

    /**
     * Parses a csv file and returns a promise with a list of rows
     * @param csvFile File that should be parsed
     */
    private parseCSVFile(csvFile: File): Promise<ParsedCSVRow[]> {
        return new Promise((resolve, reject) => {
            parse(csvFile, {
                header: true,
                transformHeader: (header: string) => cleanString(header),
                skipEmptyLines: true,
                complete: (results) => resolve(results.data as ParsedCSVRow[]),
                error: (error) => reject(error),
            });
        });
    }

    async onParseClicked() {
        this.resetDialog();
        if (this.selectedFile) {
            this.registrationsDisplayedInTable = await this.readRegistrationsFromCSVFile(this.selectedFile);
        }
    }

    onFilterChange(newFilterValue: filterValues) {
        this.selectedFilter = newFilterValue;
        switch (newFilterValue) {
            case 'all':
                this.registrationsDisplayedInTable = this.allRegistrations;
                break;
            case 'onlyImported':
                this.registrationsDisplayedInTable = this.importedRegistrations;
                break;
            case 'onlyNotImported':
                this.registrationsDisplayedInTable = this.notImportedRegistrations;
        }
    }
}
