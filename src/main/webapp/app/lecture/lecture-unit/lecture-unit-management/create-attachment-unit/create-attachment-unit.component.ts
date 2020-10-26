import { Component, OnInit, ViewChild } from '@angular/core';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import * as moment from 'moment';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { FormBuilder } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';
import { AttachmentUnitFormComponent, AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';

@Component({
    selector: 'jhi-create-attachment-unit',
    templateUrl: './create-attachment-unit.component.html',
    styles: [],
})
export class CreateAttachmentUnitComponent implements OnInit {
    @ViewChild('attachmentUnitForm')
    attachmentUnitForm: AttachmentUnitFormComponent;
    attachmentUnitToCreate?: AttachmentUnit;
    attachmentToCreate?: Attachment;

    isLoading: boolean;
    lectureId: number;
    courseId: number;

    constructor(
        protected activatedRoute: ActivatedRoute,
        private attachmentService: AttachmentService,
        private httpClient: HttpClient,
        private fileUploaderService: FileUploaderService,
        private attachmentUnitService: AttachmentUnitService,
        private fb: FormBuilder,
        private alertService: JhiAlertService,
    ) {}

    ngOnInit() {
        this.activatedRoute.paramMap.subscribe((params) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(params.get('courseId'));
        });
        this.attachmentUnitToCreate = new AttachmentUnit();
        this.attachmentToCreate = new Attachment();
    }

    createAttachmentUnit(attachmentUnitFormData: AttachmentUnitFormData): void {
        if (!attachmentUnitFormData || !attachmentUnitFormData.file || !attachmentUnitFormData.fileName) {
            return;
        }

        // === Setting attachment ===

        if (attachmentUnitFormData.name) {
            this.attachmentToCreate!.name = attachmentUnitFormData.name;
        }
        if (attachmentUnitFormData.releaseDate) {
            this.attachmentToCreate!.releaseDate = attachmentUnitFormData.releaseDate;
        }
        this.attachmentToCreate!.attachmentType = AttachmentType.FILE;
        this.attachmentToCreate!.version = 1;
        this.attachmentToCreate!.uploadDate = moment();

        // === Setting attachmentUnit ===
        if (attachmentUnitFormData.description) {
            this.attachmentUnitToCreate!.description = attachmentUnitFormData.description;
        }

        this.isLoading = true;
        this.fileUploaderService.uploadFile(attachmentUnitFormData.file, attachmentUnitFormData.fileName, { keepFileName: true }).then(
            (result) => {
                // update link to the path provided by the server
                this.attachmentToCreate!.link = result.path;
                this.attachmentUnitService.create(this.attachmentUnitToCreate!, this.lectureId).subscribe((response: HttpResponse<AttachmentUnit>) => {
                    // connect attachment unit to created attachment
                    this.attachmentToCreate!.attachmentUnit = response.body!;
                    this.attachmentService
                        .create(this.attachmentToCreate!)
                        .pipe(
                            finalize(() => {
                                this.isLoading = false;
                            }),
                        )
                        .subscribe(
                            () => {
                                this.attachmentToCreate = new Attachment();
                                this.attachmentUnitToCreate = new AttachmentUnit();
                                // reset the form so the user can quickly generate a new attachment unit
                                this.attachmentUnitForm.resetForm();
                            },
                            (res: HttpErrorResponse) => onError(this.alertService, res),
                        );
                });
            },
            (error) => {
                this.attachmentUnitForm.setFileUploadError(error);
                this.attachmentToCreate!.link = undefined;
                this.isLoading = false;
            },
        );
    }
}
