import { __decorate, __metadata } from 'tslib';
import { Component, Input, ViewChild, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { faArrowRight, faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/shared/util/global.utils';
let StudentsUploadImagesDialogComponent = class StudentsUploadImagesDialogComponent {
    constructor(activeModal, alertService, examManagementService) {
        this.activeModal = activeModal;
        this.alertService = alertService;
        this.examManagementService = examManagementService;
        this.ActionType = ActionType;
        this.notFoundUsers = [];
        this.isParsing = false;
        this.hasParsed = false;
        this.dialogErrorSource = new Subject();
        this.dialogError$ = this.dialogErrorSource.asObservable();
        this.faBan = faBan;
        this.faSpinner = faSpinner;
        this.faCheck = faCheck;
        this.faCircleNotch = faCircleNotch;
        this.faUpload = faUpload;
        this.faArrowRight = faArrowRight;
    }
    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }
    clear() {
        this.activeModal.dismiss('cancel');
    }
    onFinish() {
        this.activeModal.close();
    }
    resetDialog() {
        this.isParsing = false;
        this.notFoundUsers = [];
        this.hasParsed = false;
    }
    onPDFFileSelect(event) {
        if (event.target.files.length > 0) {
            this.resetDialog();
            this.file = event.target.files[0];
        }
    }
    parsePDFFile() {
        this.isParsing = true;
        if (this.exam?.id) {
            const formData = new FormData();
            formData.append('file', this.file);
            this.examManagementService.saveImages(this.courseId, this.exam.id, formData).subscribe({
                next: (res) => {
                    if (res) {
                        this.notFoundUsers = res.body;
                        this.isParsing = false;
                        this.hasParsed = true;
                    }
                },
                error: (res) => {
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
    get numberOfImagesNotSaved() {
        return !this.hasParsed ? 0 : this.notFoundUsers.length;
    }
};
__decorate([ViewChild('importForm', { static: false }), __metadata('design:type', NgForm)], StudentsUploadImagesDialogComponent.prototype, 'importForm', void 0);
__decorate([Input(), __metadata('design:type', Number)], StudentsUploadImagesDialogComponent.prototype, 'courseId', void 0);
__decorate([Input(), __metadata('design:type', Object)], StudentsUploadImagesDialogComponent.prototype, 'exam', void 0);
StudentsUploadImagesDialogComponent = __decorate(
    [
        Component({
            selector: 'jhi-student-upload-images-dialog',
            templateUrl: './students-upload-images-dialog.component.html',
            styleUrls: ['./students-upload-images-dialog.component.scss'],
            encapsulation: ViewEncapsulation.None,
        }),
        __metadata('design:paramtypes', [NgbActiveModal, AlertService, ExamManagementService]),
    ],
    StudentsUploadImagesDialogComponent,
);
export { StudentsUploadImagesDialogComponent };
//# sourceMappingURL=students-upload-images-dialog.component.js.map
