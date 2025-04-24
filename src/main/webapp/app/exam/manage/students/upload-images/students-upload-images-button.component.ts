import { Component, inject, input, output } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { StudentsUploadImagesDialogComponent } from 'app/exam/manage/students/upload-images/students-upload-images-dialog.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button/button.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faPlus, faUpload } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/button/button.component';

@Component({
    selector: 'jhi-student-upload-images-button',

    template: `
        <jhi-button
            [btnType]="ButtonType.PRIMARY"
            [btnSize]="buttonSize()"
            [icon]="faUpload"
            [title]="'artemisApp.exam.examUsers.uploadImage'"
            (onClick)="openUploadImagesDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class StudentsUploadImagesButtonComponent {
    private modalService = inject(NgbModal);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    courseId = input.required<number>();
    exam = input.required<Exam>();
    buttonSize = input<ButtonSize>(ButtonSize.MEDIUM);

    uploadDone = output<void>();

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
        modalRef.componentInstance.courseId = this.courseId();
        modalRef.componentInstance.exam = this.exam();
        modalRef.result.then(
            () => this.uploadDone.emit(),
            () => {},
        );
    }
}
