import { Component, Input, OnDestroy, ViewChild, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { faArrowRight, faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-student-upload-images-dialog',
    templateUrl: './students-upload-images-dialog.component.html',
    styleUrls: ['./students-upload-images-dialog.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class StudentsUploadImagesDialogComponent implements OnDestroy {
    readonly ActionType = ActionType;

    @ViewChild('importForm', { static: false }) importForm: NgForm;
    notFoundUsers: string[] = [];
    file: File;

    @Input() courseId: number;
    @Input() exam: Exam | undefined;

    isParsing = false;
    noUsersFoundError?: boolean;
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

    constructor(private activeModal: NgbActiveModal, private alertService: AlertService, private examManagementService: ExamManagementService) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        this.activeModal.close();
    }

    onPDFFileSelect(event: any) {
        if (event.target.files.length > 0) {
            this.file = event.target.files[0];
        }
    }

    /**
     * Parse pdf file and save images of registered students
     */
    parsePDFFile() {
        if (this.exam?.id) {
            console.log('inside:');
            const formData: FormData = new FormData();
            formData.append('file', this.file);

            this.examManagementService.saveImages(this.courseId, this.exam.id, formData).subscribe({
                next: (res: any) => {
                    if (res) {
                        console.log(res, 'saved');
                        this.notFoundUsers = res.body;
                        console.log(this.notFoundUsers);
                        this.isParsing = false;
                        this.hasParsed = true;
                        this.activeModal.close();
                    }
                },
                error: (res: HttpErrorResponse) => {
                    if (res.error.params === 'file' && res?.error?.title) {
                        console.log(res.error.title);
                        this.alertService.error(res.error.title);
                    } else {
                        console.log(res.error);
                        onError(this.alertService, res);
                    }
                    this.isParsing = false;
                    this.hasParsed = false;
                },
            });
        }
    }
}
