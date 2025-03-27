import { Component, OnDestroy, ViewEncapsulation, inject, input, viewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { faArrowRight, faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/shared/util/global.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

class NotFoundExamUserType {
    numberOfUsersNotFound: number;
    numberOfImagesSaved: number;
    listOfExamUserRegistrationNumbers: string[];
}
@Component({
    selector: 'jhi-student-upload-images-dialog',
    templateUrl: './students-upload-images-dialog.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, HelpIconComponent, FaIconComponent, NgClass, ArtemisTranslatePipe],
})
export class StudentsUploadImagesDialogComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);

    readonly ActionType = ActionType;

    importForm = viewChild<NgForm>('importForm');
    notFoundUsers?: NotFoundExamUserType;
    file: File;

    courseId = input.required<number>();
    exam = input.required<Exam>();

    isParsing = false;
    hasParsed = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;
    faCircleNotch = faCircleNotch;
    faUpload = faUpload;
    faArrowRight = faArrowRight;

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        this.activeModal.close();
    }

    private resetDialog() {
        this.isParsing = false;
        this.notFoundUsers = undefined;
        this.hasParsed = false;
    }

    onPDFFileSelect(event: any) {
        if (event.target.files.length > 0) {
            this.resetDialog();
            this.file = event.target.files[0];
        }
    }

    /**
     * Parse pdf file and save images of registered students
     */
    parsePDFFile() {
        this.isParsing = true;
        const exam = this.exam();
        if (exam?.id) {
            const formData: FormData = new FormData();
            formData.append('file', this.file);

            this.examManagementService.saveImages(this.courseId(), exam.id, formData).subscribe({
                next: (res: any) => {
                    if (res) {
                        this.notFoundUsers = res.body;
                        this.isParsing = false;
                        this.hasParsed = true;
                    }
                },
                error: (res: HttpErrorResponse) => {
                    if (res.error.params === 'file' && res?.error?.title) {
                        this.alertService.error(res.error.title);
                    } else {
                        onError(this.alertService, res);
                    }
                    this.isParsing = false;
                    this.hasParsed = false;
                },
            });
        }
    }
}
