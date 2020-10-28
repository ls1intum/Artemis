import { Component, OnInit, ViewChild } from '@angular/core';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { ActivatedRoute, Router } from '@angular/router';
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
    attachmentUnitToCreate: AttachmentUnit = new AttachmentUnit();
    attachmentToCreate: Attachment = new Attachment();

    isLoading: boolean;
    lectureId: number;
    courseId: number;

    constructor(
        protected activatedRoute: ActivatedRoute,
        private router: Router,
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

    createAttachmentUnit(formData: AttachmentUnitFormData): void {
        if (!formData?.formProperties?.name || !formData?.fileProperties?.file || !formData?.fileProperties?.fileName) {
            return;
        }
        const { description, name, releaseDate } = formData.formProperties;
        const { file, fileName } = formData.fileProperties;
        // === Setting attachment ===

        if (name) {
            this.attachmentToCreate.name = name;
        }
        if (releaseDate) {
            this.attachmentToCreate.releaseDate = releaseDate;
        }
        this.attachmentToCreate.attachmentType = AttachmentType.FILE;
        this.attachmentToCreate.version = 1;
        this.attachmentToCreate.uploadDate = moment();

        // === Setting attachmentUnit ===
        if (description) {
            this.attachmentUnitToCreate.description = description;
        }

        this.isLoading = true;
        this.fileUploaderService.uploadFile(file, fileName, { keepFileName: true }).then(
            (result) => {
                // update link to the path provided by the server
                this.attachmentToCreate.link = result.path;
                this.attachmentUnitService
                    .create(this.attachmentUnitToCreate!, this.lectureId)
                    .concatMap((response: HttpResponse<AttachmentUnit>) => {
                        this.attachmentToCreate.attachmentUnit = response.body!;
                        return this.attachmentService.create(this.attachmentToCreate);
                    })
                    .pipe(
                        finalize(() => {
                            this.isLoading = false;
                        }),
                    )
                    .subscribe(
                        () => {
                            this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                        },
                        (res: HttpErrorResponse) => onError(this.alertService, res),
                    );
            },
            (error) => {
                // displaying the file upload error in the form but not resetting the form]
                this.attachmentUnitForm.setFileUploadError(error.message);
                this.isLoading = false;
            },
        );
    }
}
