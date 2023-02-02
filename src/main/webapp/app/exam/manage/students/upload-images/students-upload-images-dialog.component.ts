import { Component, Input, OnDestroy, ViewChild, ViewEncapsulation } from '@angular/core';
import { NgForm } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExamUserDTO } from 'app/entities/exam-user-dto.mode';
import { Subject } from 'rxjs';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { StudentDTO } from 'app/entities/student-dto.model';
import { faArrowRight, faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
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
    examUsersToUploadImage: ExamUserDTO[] = [];
    notFoundUsers: StudentDTO[] = [];
    dataWithImages: any[] = [];
    file: File;

    @Input() courseId: number;
    @Input() exam: Exam | undefined;

    isParsing = false;
    validationError?: string;
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

    constructor(
        private activeModal: NgbActiveModal,
        private alertService: AlertService,
        private examManagementService: ExamManagementService,
        private courseManagementService: CourseManagementService,
        private tutorialGroupService: TutorialGroupsService,
    ) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    private resetDialog() {
        this.examUsersToUploadImage = [];
        this.notFoundUsers = [];
        this.hasParsed = false;
    }

    /**
     * Callback method that is called when the import request was successful
     * @param {HttpResponse<StudentDTO[]>} notFoundUsers - List of users that could NOT be imported since they were not found
     */
    onSaveSuccess(notFoundUsers: HttpResponse<StudentDTO[]>) {
        this.isParsing = false;
        this.hasParsed = true;
        this.notFoundUsers = notFoundUsers.body! || [];
    }

    /**
     * Callback method that is called when the import request failed
     */
    onSaveError() {
        this.alertService.error('artemisApp.importUsers.genericErrorMessage');
        this.isParsing = false;
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
     * Parse pdf file and extract images with student registration numbers
     */
    parsePDFFile() {
        if (this.exam?.id) {
            const formData: FormData = new FormData();
            formData.append('file', this.file);

            this.examManagementService.parsePDFFile(this.courseId, this.exam.id, formData).subscribe({
                next: (res: any) => {
                    if (res) {
                        this.dataWithImages = res.body;
                    }
                },
                error: (res: HttpErrorResponse) => {
                    if (res.error.params === 'file' && res?.error?.title) {
                        this.alertService.error(res.error.title);
                    } else {
                        onError(this.alertService, res);
                    }
                    this.isParsing = false;
                },
            });
        }
    }

    /**
     * Upload all images for registered students
     */
    uploadImagesForRegisteredStudents() {
        if (this.exam?.id) {
            this.examManagementService.uploadImagesForRegisteredStudents(this.courseId, this.exam.id, this.dataWithImages).subscribe({
                next: () => {
                    // todo: reload table
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
        }
    }
}
