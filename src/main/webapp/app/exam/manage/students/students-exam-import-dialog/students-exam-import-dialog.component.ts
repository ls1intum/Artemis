import { Component, Input, ViewChild, OnDestroy, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/alert/alert.service';
import { HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { StudentDTO } from 'app/entities/student-dto.model';
import * as Papa from 'papaparse';

const csvColumns = Object.freeze({
    registrationNumber: 'REGISTRATION_NUMBER',
    firstName: 'FIRST_NAME_OF_STUDENT',
    lastName: 'FAMILY_NAME_OF_STUDENT',
});

type CsvStudent = object;

@Component({
    selector: 'jhi-students-exam-import-dialog',
    templateUrl: './students-exam-import-dialog.component.html',
    styleUrls: ['./students-exam-import-dialog.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class StudentsExamImportDialogComponent implements OnDestroy {
    readonly ActionType = ActionType;

    @ViewChild('importForm', { static: false }) importForm: NgForm;

    @Input() courseId: number;
    @Input() exam: Exam;

    studentsToImport: StudentDTO[] = [];
    notFoundStudents: StudentDTO[] = [];

    isParsing = false;
    validationError: string | null = null;
    isImporting = false;
    hasImported = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private activeModal: NgbActiveModal, private jhiAlertService: AlertService, private examManagementService: ExamManagementService) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    private resetDialog() {
        this.studentsToImport = [];
        this.notFoundStudents = [];
        this.hasImported = false;
    }

    async onCSVFileSelect($event: any) {
        if ($event.target.files.length > 0) {
            this.resetDialog();
            this.studentsToImport = await this.readStudentsFromCSVFile($event, $event.target.files[0]);
        }
    }

    /**
     * Reads students from a csv file into a list of StudentDTOs
     * The column "registrationNumber" is mandatory since the import requires it as an identifier
     * @param $event File change event from the HTML input of type file
     * @param csvFile File that contains one student per row and has at least the columns specified in csvColumns
     */
    private async readStudentsFromCSVFile($event: any, csvFile: File): Promise<StudentDTO[]> {
        let csvStudents: CsvStudent[] = [];
        try {
            this.isParsing = true;
            this.validationError = null;
            csvStudents = await this.parseCSVFile(csvFile);
        } catch (error) {
            this.validationError = error.message;
        } finally {
            this.isParsing = false;
        }
        if (csvStudents.length > 0) {
            this.performExtraValidations(csvStudents);
        }
        if (this.validationError) {
            $event.target.value = ''; // remove selected file so user can fix the file and select it again
            return [];
        }
        return csvStudents.map(
            (student) =>
                ({
                    registrationNumber: student[csvColumns.registrationNumber],
                    firstName: student[csvColumns.firstName] || '',
                    lastName: student[csvColumns.lastName] || '',
                } as StudentDTO),
        );
    }

    /**
     * Performs validations on the parsed students
     * - checks if values for the required column {csvColumns.registrationNumber} are present
     * @param csvStudents Parsed list of students
     */
    performExtraValidations(csvStudents: CsvStudent[]) {
        const invalidStudentEntries = this.computeInvalidStudentEntries(csvStudents);
        if (invalidStudentEntries) {
            const msg = 'Error: Rows without value in required column';
            const maxLength = 30;
            const entriesFormatted = invalidStudentEntries.length <= maxLength ? invalidStudentEntries : invalidStudentEntries.slice(0, maxLength) + '...';
            this.validationError = `${msg} "${csvColumns.registrationNumber}": ${entriesFormatted}`;
        }
    }

    /**
     * Returns a comma separated list of row numbers that contains invalid student entries
     * @param csvStudents Parsed list of students
     */
    computeInvalidStudentEntries(csvStudents: CsvStudent[]): string | null {
        const invalidList = [];
        for (const [i, student] of csvStudents.entries()) {
            if (!student[csvColumns.registrationNumber]) {
                invalidList.push(i + 2); // +2 instead of +1 due to header column in csv file
            }
        }
        return invalidList.length === 0 ? null : invalidList.join(', ');
    }

    /**
     * Parses a csv file and returns a promise with a list of rows
     * @param csvFile File that should be parsed
     */
    private parseCSVFile(csvFile: File): Promise<CsvStudent[]> {
        return new Promise((resolve, reject) => {
            Papa.parse(csvFile, {
                header: true,
                skipEmptyLines: true,
                complete: (results) => resolve(results.data as CsvStudent[]),
                error: (error) => reject(error),
            });
        });
    }

    /**
     * Sends the import request to the server with the list of students to be imported
     */
    importStudents() {
        this.isImporting = true;
        this.examManagementService.addStudentsToExam(this.courseId, this.exam.id, this.studentsToImport).subscribe(
            (res) => this.onSaveSuccess(res),
            () => this.onSaveError(),
        );
    }

    /**
     * True if this student was successfully imported, false otherwise
     * @param student The student to be checked
     */
    wasImported(student: StudentDTO): boolean {
        return this.hasImported && !this.wasNotImported(student);
    }

    /**
     * True if this student could not be imported, false otherwise
     * @param student The student to be checked
     */
    wasNotImported(student: StudentDTO): boolean {
        return this.hasImported && this.notFoundStudents.map((s) => s.registrationNumber).includes(student.registrationNumber);
    }

    /**
     * Number of students that were successfully imported
     */
    get numberOfStudentsImported(): number {
        return !this.hasImported ? 0 : this.studentsToImport.length - this.numberOfStudentsNotImported;
    }

    /**
     * Number of students which could not be imported
     */
    get numberOfStudentsNotImported(): number {
        return !this.hasImported ? 0 : this.notFoundStudents.length;
    }

    get isSubmitDisabled(): boolean {
        return this.isImporting || !this.studentsToImport?.length;
    }

    /**
     * Callback method that is called when the import request was successful
     * @param {HttpResponse<StudentDTO[]>} notFoundStudents - List of students that could NOT be imported since they were not found
     */
    onSaveSuccess(notFoundStudents: HttpResponse<StudentDTO[]>) {
        this.isImporting = false;
        this.hasImported = true;
        this.notFoundStudents = notFoundStudents.body! || [];
    }

    /**
     * Callback method that is called when the import request failed
     */
    onSaveError() {
        this.jhiAlertService.error('artemisApp.examManagement.examStudents.importStudents.genericErrorMessage');
        this.isImporting = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        this.activeModal.close();
    }
}
