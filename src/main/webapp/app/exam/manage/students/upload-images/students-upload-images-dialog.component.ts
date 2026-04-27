import { Component, OnDestroy, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { faArrowRight, faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/shared/util/global.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

class NotFoundExamUserType {
    numberOfUsersNotFound: number;
    numberOfImagesSaved: number;
}

@Component({
    selector: 'jhi-student-upload-images-dialog',
    templateUrl: './students-upload-images-dialog.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, HelpIconComponent, FaIconComponent, NgClass, ArtemisTranslatePipe],
})
export class StudentsUploadImagesDialogComponent implements OnInit, OnDestroy {
    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);

    readonly ActionType = ActionType;

    notFoundUsers = signal<NotFoundExamUserType | undefined>(undefined);
    file = signal<File | undefined>(undefined);

    courseId = signal<number | undefined>(undefined);
    exam = signal<Exam | undefined>(undefined);

    isParsing = signal(false);
    hasParsed = signal(false);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;
    faCircleNotch = faCircleNotch;
    faUpload = faUpload;
    faArrowRight = faArrowRight;

    ngOnInit(): void {
        const data = this.dialogConfig?.data;
        if (data) {
            if (data.courseId !== undefined) {
                this.courseId.set(data.courseId);
            }
            if (data.exam !== undefined) {
                this.exam.set(data.exam);
            }
        }
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    clear() {
        this.dialogRef.close();
    }

    onFinish() {
        this.dialogRef.close('finished');
    }

    private resetDialog() {
        this.isParsing.set(false);
        this.notFoundUsers.set(undefined);
        this.hasParsed.set(false);
    }

    onPDFFileSelect(event: any) {
        if (event.target.files.length > 0) {
            this.resetDialog();
            this.file.set(event.target.files[0]);
        }
    }

    /**
     * Parse pdf file and save images of registered students
     */
    parsePDFFile() {
        this.isParsing.set(true);
        const exam = this.exam();
        if (exam?.id) {
            const formData: FormData = new FormData();
            formData.append('file', this.file()!);

            this.examManagementService.saveImages(this.courseId()!, exam.id, formData).subscribe({
                next: (res: any) => {
                    if (res) {
                        this.notFoundUsers.set(res.body);
                        this.isParsing.set(false);
                        this.hasParsed.set(true);
                    }
                },
                error: (res: HttpErrorResponse) => {
                    if (res.error.params === 'file' && res?.error?.title) {
                        this.alertService.error(res.error.title);
                    } else {
                        onError(this.alertService, res);
                    }
                    this.isParsing.set(false);
                    this.hasParsed.set(false);
                },
            });
        }
    }
}
