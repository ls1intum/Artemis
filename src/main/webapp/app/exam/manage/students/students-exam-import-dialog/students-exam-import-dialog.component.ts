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

    isParsing = false;
    isImporting = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private activeModal: NgbActiveModal, private jhiAlertService: AlertService, private examManagementService: ExamManagementService) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    get isSubmitDisabled(): boolean {
        return this.isImporting || !this.studentsToImport?.length;
    }

    /**
     * Cancel the import dialog
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    async onCSVFileSelect($event: any) {
        if ($event.target.files.length > 0) {
            this.studentsToImport = await this.readStudentsFromCSVFile($event.target.files[0]);
        }
    }

    private async readStudentsFromCSVFile(csvFile: File): Promise<StudentDTO[]> {
        try {
            this.isParsing = true;
            return (await this.parseCSVFile(csvFile))
                .filter((csvStudent) => Boolean(csvStudent[csvColumns.registrationNumber]))
                .map(
                    (student) =>
                        ({
                            registrationNumber: student[csvColumns.registrationNumber],
                            firstName: student[csvColumns.firstName],
                            lastName: student[csvColumns.lastName],
                        } as StudentDTO),
                );
        } finally {
            this.isParsing = false;
        }
    }

    private parseCSVFile(csvFile: File): Promise<any[]> {
        return new Promise((resolve) => {
            Papa.parse(csvFile, { header: true, complete: (results) => resolve(results.data) });
        });
    }

    importStudents() {
        if (this.isSubmitDisabled) {
            return;
        }
        this.isImporting = true;
        this.examManagementService.addStudentsToExam(this.courseId, this.exam.id, this.studentsToImport).subscribe(
            (res) => this.onSaveSuccess(res),
            () => this.onSaveError(),
        );
    }

    /**
     * Callback method that is called when the import request was successful
     * @param {HttpResponse<StudentDTO[]>} students - List of students that could NOT be imported since they were not found
     */
    onSaveSuccess(students: HttpResponse<StudentDTO[]>) {
        this.activeModal.close(students.body);
        this.isImporting = false;
    }

    /**
     * Hook to indicate an error on save
     */
    onSaveError() {
        this.jhiAlertService.error('artemisApp.examManagement.examStudents.importStudents.genericErrorMessage');
        this.isImporting = false;
    }
}
