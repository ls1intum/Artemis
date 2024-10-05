import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { StudentsUploadImagesDialogComponent } from 'app/exam/manage/students/upload-images/students-upload-images-dialog.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { Exam } from 'app/entities/exam/exam.model';
import { faPlus, faUpload } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-student-upload-images-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize"
            [icon]="faUpload"
            [title]="'artemisApp.exam.examUsers.uploadImage'"
            (onClick)="openUploadImagesDialog($event)"
        />
    `,
})
export class StudentsUploadImagesButtonComponent {
    private modalService = inject(NgbModal);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() courseId: number;
    @Input() exam: Exam;
    @Input() buttonSize: ButtonSize = ButtonSize.MEDIUM;

    @Output() finish: EventEmitter<void> = new EventEmitter();

    // Icons
    faPlus = faPlus;
    faUpload = faUpload;

    /**
     * Open up upload dialog for exam users image upload
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openUploadImagesDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(StudentsUploadImagesDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.exam = this.exam;
        modalRef.result.then(
            () => this.finish.emit(),
            () => {},
        );
    }
}
