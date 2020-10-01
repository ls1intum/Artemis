import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { StudentsExamImportDialogComponent } from 'app/exam/manage/students/students-exam-import-dialog/students-exam-import-dialog.component';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-students-exam-import-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="'plus'"
            [title]="'artemisApp.examManagement.examStudents.importStudents.buttonLabel'"
            (onClick)="openStudentsExamImportDialog($event)"
        ></jhi-button>
    `,
})
export class StudentsExamImportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() courseId: number;
    @Input() exam: Exam;
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    @Output() finish: EventEmitter<void> = new EventEmitter();

    constructor(private modalService: NgbModal) {}

    /**
     * Open up import dialog for students
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openStudentsExamImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(StudentsExamImportDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.exam = this.exam;

        modalRef.result.then(
            () => this.finish.emit(),
            () => {},
        );
    }
}
