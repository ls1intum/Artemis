import { Component, Input, OnDestroy, ViewChild, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { StudentDTO } from 'app/entities/student-dto.model';
import * as Papa from 'papaparse';

const csvColumns = Object.freeze({
    registrationNumber: 'registrationnumber',
    matrikelNummer: 'matrikelnummer',
    matriculationNumber: 'matriculationnumber',
    firstNameOfStudent: 'firstnameofstudent',
    familyNameOfStudent: 'familynameofstudent',
    firstName: 'firstname',
    familyName: 'familyname',
    lastName: 'lastname',
    login: 'login',
    username: 'username',
    user: 'user',
    benutzer: 'benutzer',
    benutzerName: 'benutzername',
});

type CsvStudent = object;

@Component({
    selector: 'jhi-students-import-dialog',
    templateUrl: './students-import-dialog.component.html',
    styleUrls: ['./students-import-dialog.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class StudentsImportDialogComponent implements OnDestroy {
    readonly ActionType = ActionType;

    @ViewChild('importForm', { static: false }) importForm: NgForm;

    @Input() courseId: number;
    @Input() courseGroup: String;
    @Input() exam: Exam;

    studentsToImport: StudentDTO[] = [];
    notFoundStudents: StudentDTO[] = [];

    isParsing = false;
    validationError?: string;
    isImporting = false;
    hasImported = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private courseManagementService: CourseManagementService,
        private examManagementService: ExamManagementService,
    ) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    private resetDialog() {
        this.studentsToImport = [];
        this.notFoundStudents = [];
        this.hasImported = false;
    }

    async onCSVFileSelect(event: any) {
        if (event.target.files.length > 0) {
            this.resetDialog();
            this.studentsToImport = await this.readStudentsFromCSVFile(event, event.target.files[0]);
        }
    }

    /**
     * Reads students from a csv file into a list of StudentDTOs
     * The column "registrationNumber" is mandatory since the import requires it as an identifier
     * @param event File change event from the HTML input of type file
     * @param csvFile File that contains one student per row and has at least the columns specified in csvColumns
     */
    private async readStudentsFromCSVFile(event: any, csvFile: File): Promise<StudentDTO[]> {
        let csvStudents: CsvStudent[] = [];
        try {
            this.isParsing = true;
            this.validationError = undefined;
            csvStudents = await this.parseCSVFile(csvFile);
        } catch (error) {
            this.validationError = error.message;
        } finally {
            this.isParsing = false;
        }
        if (csvStudents.length > 0) {
            this.performExtraValidations(csvFile, csvStudents);
        }
        if (this.validationError) {
            event.target.value = ''; // remove selected file so user can fix the file and select it again
            return [];
        }
        return csvStudents.map(
            (student) =>
                ({
                    registrationNumber: student[csvColumns.registrationNumber] || student[csvColumns.matrikelNummer] || student[csvColumns.matriculationNumber] || '',
                    login:
                        student[csvColumns.login] ||
                        student[csvColumns.username] ||
                        student[csvColumns.user] ||
                        student[csvColumns.benutzer] ||
                        student[csvColumns.benutzerName] ||
                        '',
                    firstName: student[csvColumns.firstName] || student[csvColumns.firstNameOfStudent] || '',
                    lastName: student[csvColumns.lastName] || student[csvColumns.familyName] || student[csvColumns.familyNameOfStudent] || '',
                } as StudentDTO),
        );
    }

    /**
     * Performs validations on the parsed students
     * - checks if values for the required column {csvColumns.registrationNumber} are present
     *
     * @param csvFile File that contains one student per row and has at least the columns specified in csvColumns
     * @param csvStudents Parsed list of students
     */
    performExtraValidations(csvFile: File, csvStudents: CsvStudent[]) {
        const invalidStudentEntries = this.computeInvalidStudentEntries(csvStudents);
        if (invalidStudentEntries) {
            const msg = (body: string) => `
                Could not read file <b>${csvFile.name}</b> due to the following error:
                <ul class="mt-1"><li><b>Rows must have a value in one of the required columns ${body}</b></li></ul>
                Please repair the file and try again.
            `;
            const maxLength = 30;
            const entriesFormatted = invalidStudentEntries.length <= maxLength ? invalidStudentEntries : invalidStudentEntries.slice(0, maxLength) + '...';
            this.validationError = msg(`${csvColumns.registrationNumber} or ${csvColumns.login}: ${entriesFormatted}`);
        }
    }

    /**
     * Returns a comma separated list of row numbers that contains invalid student entries
     * @param csvStudents Parsed list of students
     */
    computeInvalidStudentEntries(csvStudents: CsvStudent[]): string | null {
        const invalidList: number[] = [];
        for (const [i, student] of csvStudents.entries()) {
            if (
                !student[csvColumns.registrationNumber] &&
                !student[csvColumns.matrikelNummer] &&
                !student[csvColumns.matriculationNumber] &&
                !student[csvColumns.login] &&
                !student[csvColumns.user] &&
                !student[csvColumns.username] &&
                !student[csvColumns.benutzer] &&
                !student[csvColumns.benutzerName]
            ) {
                // '+ 2' instead of '+ 1' due to the header column in the csv file
                invalidList.push(i + 2);
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
                transformHeader: (header: string) => header.toLowerCase().replace(' ', '').replace('_', ''),
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
        if (this.courseGroup && !this.exam) {
            console.log('studentsImport');
            this.courseManagementService.addStudentsToGroupInCourse(this.courseId, this.studentsToImport, this.courseGroup).subscribe(
                (res) => this.onSaveSuccess(res),
                () => this.onSaveError(),
            );
        } else if (!this.courseGroup && this.exam) {
            console.log('examImport');
            this.examManagementService.addStudentsToExam(this.courseId, this.exam.id!, this.studentsToImport).subscribe(
                (res) => this.onSaveSuccess(res),
                () => this.onSaveError(),
            );
        }
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
        if (this.hasImported && this.notFoundStudents?.length === 0) {
            return false;
        }

        for (const notFound of this.notFoundStudents) {
            if (
                (notFound.registrationNumber?.length > 0 && notFound.registrationNumber === student.registrationNumber) ||
                (notFound.login?.length > 0 && notFound.login === student.login)
            ) {
                return true;
            }
        }

        return false;
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
