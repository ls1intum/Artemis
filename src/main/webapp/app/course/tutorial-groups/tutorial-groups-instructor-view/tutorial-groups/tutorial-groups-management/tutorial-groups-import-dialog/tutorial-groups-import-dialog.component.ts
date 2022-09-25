import { Component, Input, OnDestroy } from '@angular/core';
import { faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { StudentDTO } from 'app/entities/student-dto.model';
import { TutorialGroupRegistrationImportDTO } from 'app/entities/tutorial-group/tutorial-group-import-dto.model';
import { parse } from 'papaparse';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';

type csvRow = object;

const POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS = ['gruppe', 'titel'];

@Component({
    selector: 'jhi-tutorial-groups-import-dialog',
    templateUrl: './tutorial-groups-import-dialog.component.html',
    styleUrls: ['./tutorial-groups-import-dialog.component.scss'],
})
export class TutorialGroupsImportDialogComponent implements OnDestroy {
    @Input() courseId: number;

    tutorialGroupRegistrationImportDTOs: TutorialGroupRegistrationImportDTO[] = [];
    notFoundTutorialGroupsToImport: StudentDTO[] = [];

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

    constructor(private activeModal: NgbActiveModal, private alertService: AlertService, private tutorialGroupService: TutorialGroupsService) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    get numberOfTutorialGroupsImported(): number {
        return !this.hasImported ? 0 : this.tutorialGroupRegistrationImportDTOs.length - this.numberOfUsersNotImported;
    }
    get numberOfUsersNotImported(): number {
        return !this.hasImported ? 0 : this.notFoundTutorialGroupsToImport.length;
    }

    get isSubmitDisabled(): boolean {
        return this.isImporting || !this.tutorialGroupRegistrationImportDTOs?.length;
    }

    private resetDialog() {
        this.tutorialGroupRegistrationImportDTOs = [];
        this.notFoundTutorialGroupsToImport = [];
        this.hasImported = false;
    }

    async onCSVFileSelect(event: any) {
        if (event.target.files.length > 0) {
            this.resetDialog();
            this.tutorialGroupRegistrationImportDTOs = await this.readTutorialGroupFromCSV(event, event.target.files[0]);
        }
    }

    /**
     * Reads tutorial groups from a csv file into a list of StudentDTOs
     * The column "Gruppe" is mandatory since the import requires it as an identifier
     * @param event File change event from the HTML input of type file
     * @param csvFile File that contains one registration per row and has at least the columns specified in csvColumns
     */
    private async readTutorialGroupFromCSV(event: Event, csvFile: File): Promise<TutorialGroupRegistrationImportDTO[]> {
        let csvRows: csvRow[] = [];
        try {
            this.isParsing = true;
            this.validationError = undefined;
            csvRows = await this.parseCSVFile(csvFile);
        } catch (error) {
            this.validationError = error.message;
        } finally {
            this.isParsing = false;
        }
        if (csvRows.length > 0) {
            this.performExtraValidations(csvRows);
        }
        if (this.validationError) {
            (event.target as HTMLInputElement).value = ''; // remove selected file so user can fix the file and select it again
            return [];
        }

        // Each row is a object with the structrue
        // 	{
        // 		"Column 1": "foo",
        // 		"Column 2": "bar"
        // 	},

        const usedHeaders = Object.keys(csvRows.first() || []);
        const titleHeader = usedHeaders.find((value) => POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS.includes(value)) || '';

        // convert the 'raw' csv rows into a list of TutorialGroupImportDTOs
        return csvRows.map(
            (row) =>
                ({
                    title: row[titleHeader]?.trim() || '',
                } as TutorialGroupRegistrationImportDTO),
        );
    }

    import() {
        this.isImporting = true;
        this.tutorialGroupService.import(this.courseId, this.tutorialGroupRegistrationImportDTOs).subscribe({
            next: () => this.onSaveSuccess(),
            error: () => this.onSaveError(),
        });
    }

    performExtraValidations(csvRows: csvRow[]): void {
        // Todo
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        this.activeModal.close();
    }

    onSaveSuccess() {
        this.isImporting = false;
        this.hasImported = true;
    }

    onSaveError() {
        this.alertService.error('artemisApp.pages.tutorialGroupsManagement.import.genericErrorMessage');
        this.isImporting = false;
    }

    wasImported(registration: TutorialGroupRegistrationImportDTO): boolean {
        // toDo
        return true;
    }

    wasNotImported(registration: TutorialGroupRegistrationImportDTO): boolean {
        // toDo
        return false;
    }

    /**
     * Parses a csv file and returns a promise with a list of rows
     * @param csvFile File that should be parsed
     */
    private parseCSVFile(csvFile: File): Promise<csvRow[]> {
        return new Promise((resolve, reject) => {
            parse(csvFile, {
                header: true,
                transformHeader: (header: string) => header.toLowerCase().replaceAll(' ', '').replaceAll('_', '').replaceAll('-', '').trim(),
                skipEmptyLines: true,
                complete: (results) => resolve(results.data as csvRow[]),
                error: (error) => reject(error),
            });
        });
    }
}
