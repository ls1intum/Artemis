import { Component, OnInit, ViewChild } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, switchMap, take } from 'rxjs/operators';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { AttachmentUnitFormComponent, AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AttachmentService } from 'app/lecture/attachment.service';
import { forkJoin, combineLatest } from 'rxjs';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-edit-attachment-unit',
    templateUrl: './edit-attachment-unit.component.html',
    styles: [],
})
export class EditAttachmentUnitComponent implements OnInit {
    @ViewChild('attachmentUnitForm')
    attachmentUnitForm: AttachmentUnitFormComponent;
    isLoading = false;
    attachmentUnit: AttachmentUnit;
    attachment: Attachment;
    formData: AttachmentUnitFormData;
    lectureId: number;
    notificationText: string;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private attachmentUnitService: AttachmentUnitService,
        private attachmentService: AttachmentService,
        private alertService: AlertService,
        private fileUploaderService: FileUploaderService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const attachmentUnitId = Number(params.get('attachmentUnitId'));
                    this.lectureId = Number(parentParams.get('lectureId'));
                    return this.attachmentUnitService.findById(attachmentUnitId, this.lectureId);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (attachmentUnitResponse: HttpResponse<AttachmentUnit>) => {
                    this.attachmentUnit = attachmentUnitResponse.body!;
                    this.attachment = this.attachmentUnit.attachment!;
                    // breaking the connection to prevent errors in deserialization. will be reconnected on the server side
                    this.attachmentUnit.attachment = undefined;
                    this.attachment.attachmentUnit = undefined;

                    this.formData = {
                        formProperties: {
                            name: this.attachment.name,
                            description: this.attachmentUnit.description,
                            releaseDate: this.attachment.releaseDate,
                            version: this.attachment.version,
                        },
                        fileProperties: {
                            fileName: this.attachment.link,
                        },
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateAttachmentUnit(formData: AttachmentUnitFormData) {
        const { description, name, releaseDate, updateNotificationText } = formData.formProperties;
        const { file, fileName } = formData.fileProperties;

        // optional update notification text for students
        if (updateNotificationText) {
            this.notificationText = updateNotificationText;
        }

        // === Setting attachment ===
        this.attachment.name = name;
        this.attachment.releaseDate = releaseDate;
        this.attachment.attachmentType = AttachmentType.FILE;
        // === Setting attachmentUnit ===
        this.attachmentUnit.description = description;

        this.isLoading = true;
        // when the file has changed the new file needs to be uploaded first before making the put request
        if (file) {
            this.fileUploaderService.uploadFile(file, fileName, { keepFileName: true }).then(
                (result) => {
                    // we only update the version when the underlying file has changed
                    this.attachment.version = this.attachment.version! + 1;
                    this.attachment.uploadDate = dayjs();
                    // update link to the path provided by the server
                    this.attachment.link = result.path;
                    this.performUpdate();
                },
                (error) => {
                    // displaying the file upload error in the form but not resetting the form
                    this.attachmentUnitForm.setFileUploadError(error.message);
                    this.isLoading = false;
                },
            );
        } else {
            this.performUpdate();
        }
    }

    performUpdate() {
        const attachmentUnitObservable = this.attachmentUnitService.update(this.attachmentUnit, this.lectureId);
        const requestOptions = {} as any;
        if (this.notificationText) {
            requestOptions.notificationText = this.notificationText;
        }
        const attachmentObservable = this.attachmentService.update(this.attachment, requestOptions);
        forkJoin([attachmentUnitObservable, attachmentObservable])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    this.router.navigate(['../../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
