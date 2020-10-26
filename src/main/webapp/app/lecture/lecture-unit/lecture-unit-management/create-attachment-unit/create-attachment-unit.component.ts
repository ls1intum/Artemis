import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { FileService } from 'app/shared/http/file.service';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import * as moment from 'moment';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-create-attachment-unit',
    templateUrl: './create-attachment-unit.component.html',
    styles: [],
})
export class CreateAttachmentUnitComponent implements OnInit {
    form: FormGroup;

    @ViewChild('fileInput', { static: false })
    fileInput: ElementRef;
    attachmentUnit?: AttachmentUnit;
    attachment?: Attachment;
    attachmentBackup?: Attachment;

    file?: Blob | File;
    // Setting it to the placeholder value as default
    fileName?: string = this.translateService.instant('artemisApp.attachmentUnit.createAttachmentUnit.chooseFile');
    isLoading: boolean;
    isDownloadingAttachmentLink?: string;
    erroredFile?: Blob;
    errorMessage?: string;
    lectureId: number;
    courseId: number;
    notificationText: any;

    constructor(
        protected activatedRoute: ActivatedRoute,
        private attachmentService: AttachmentService,
        private httpClient: HttpClient,
        private fileUploaderService: FileUploaderService,
        private fileService: FileService,
        private translateService: TranslateService,
        private attachmentUnitService: AttachmentUnitService,
        private fb: FormBuilder,
        private alertService: JhiAlertService,
    ) {}

    ngOnInit() {
        this.activatedRoute.paramMap.subscribe((params) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(params.get('courseId'));
        });
        this.attachmentUnit = new AttachmentUnit();
        this.attachment = new Attachment();
        this.form = this.fb.group({
            name: ['', [Validators.required, Validators.maxLength(255)]],
            description: ['', [Validators.maxLength(255)]],
            file: [undefined, Validators.required],
            releaseDate: [undefined],
        });
    }

    get name() {
        return this.form.get('name');
    }

    get description() {
        return this.form.get('description');
    }

    setFile($event: any): void {
        if ($event.target.files.length) {
            this.erroredFile = undefined; // removes the file size error message when the user selects a new file
            const fileList: FileList = $event.target.files;
            this.file = fileList[0];
            this.fileName = this.file['name'];
        }
    }

    submit(): void {
        if (!this.file || !this.fileName) {
            return;
        }
        const formValue = this.form.value;
        console.log(formValue);
        this.attachment!.releaseDate = formValue.releaseDate;
        this.attachment!.name = formValue.name;
        this.attachment!.attachmentType = AttachmentType.FILE;
        this.attachment!.version = 1;
        this.attachment!.uploadDate = moment();
        this.attachmentUnit!.description = formValue.description;

        this.isLoading = true;
        this.erroredFile = undefined;
        this.errorMessage = undefined;
        this.fileUploaderService.uploadFile(this.file, this.fileName, { keepFileName: true }).then(
            (result) => {
                // update link to the path provided by the server
                this.attachment!.link = result.path;
                this.attachmentUnitService.create(this.attachmentUnit!, this.lectureId).subscribe((response: HttpResponse<AttachmentUnit>) => {
                    this.attachment!.attachmentUnit = response.body!;
                    this.attachmentService
                        .create(this.attachment!)
                        .pipe(
                            finalize(() => {
                                this.isLoading = false;
                            }),
                        )
                        .subscribe(
                            () => {
                                this.form.reset();
                                this.fileName = this.translateService.instant('artemisApp.attachmentUnit.createAttachmentUnit.chooseFile');
                                this.attachment = new Attachment();
                                this.attachmentUnit = new AttachmentUnit();
                            },
                            (res: HttpErrorResponse) => onError(this.alertService, res),
                        );
                });
            },
            (error) => {
                this.errorMessage = error.message;
                this.erroredFile = this.file;
                this.fileInput.nativeElement.value = '';
                this.attachment!.link = undefined;
                this.isLoading = false;
                this.file = undefined;
            },
        );
    }
}
