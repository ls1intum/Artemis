import { Component, inject, input, output } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { StudentsUploadImagesDialogComponent } from 'app/exam/manage/students/upload-images/students-upload-images-dialog.component';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faPlus, faUpload } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

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
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);

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
        const dialogRef = this.dialogService.open(StudentsUploadImagesDialogComponent, {
            header: this.translateService.instant('artemisApp.exam.examUsers.dialogTitle'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: {
                courseId: this.courseId(),
                exam: this.exam(),
            },
        });
        dialogRef?.onClose.subscribe((result) => {
            if (result === 'finished') {
                this.uploadDone.emit();
            }
        });
    }
}
