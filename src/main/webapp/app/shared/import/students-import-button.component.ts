import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { StudentsImportDialogComponent } from 'app/shared/import/students-import-dialog.component';

@Component({
    selector: 'jhi-students-import-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="'plus'"
            [title]="'artemisApp.examManagement.examStudents.importStudents.buttonLabel'"
            (onClick)="openStudentsImportDialog($event)"
        ></jhi-button>
    `,
})
export class StudentsImportButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() courseId: number;
    @Input() buttonSize: ButtonSize = ButtonSize.SMALL;

    @Output() finish: EventEmitter<void> = new EventEmitter();

    constructor(private modalService: NgbModal) {}

    /**
     * Open up import dialog for students
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openStudentsImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(StudentsImportDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;

        modalRef.result.then(
            () => this.finish.emit(),
            () => {},
        );
    }
}
