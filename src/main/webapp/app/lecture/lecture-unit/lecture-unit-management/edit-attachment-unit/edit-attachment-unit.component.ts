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
import { combineLatest } from 'rxjs';
import { objectToJsonBlob } from 'app/utils/blob-util';

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

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private attachmentUnitService: AttachmentUnitService, private alertService: AlertService) {}

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

    updateAttachmentUnit(attachmentUnitFormData: AttachmentUnitFormData) {
        const { description, name, releaseDate, updateNotificationText } = attachmentUnitFormData.formProperties;
        const { file, fileName } = attachmentUnitFormData.fileProperties;

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

        const formData = new FormData();
        if (file) {
            formData.append('file', file, fileName);
        }
        formData.append('attachment', objectToJsonBlob(this.attachment));
        formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnit));

        this.attachmentUnitService.update(this.lectureId, this.attachmentUnit.id!, formData, this.notificationText).subscribe({
            next: () => this.router.navigate(['../../../'], { relativeTo: this.activatedRoute }),
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
            complete: () => (this.isLoading = false),
        });
    }
}
