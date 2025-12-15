import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, switchMap, take } from 'rxjs/operators';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { combineLatest } from 'rxjs';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';

@Component({
    selector: 'jhi-edit-attachment-video-unit',
    templateUrl: './edit-attachment-video-unit.component.html',
    imports: [LectureUnitLayoutComponent, AttachmentVideoUnitFormComponent],
})
export class EditAttachmentVideoUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private alertService = inject(AlertService);

    @ViewChild('attachmentVideoUnitForm') attachmentVideoUnitForm: AttachmentVideoUnitFormComponent;

    isLoading = false;
    attachmentVideoUnit: AttachmentVideoUnit;
    attachment: Attachment;
    formData: AttachmentVideoUnitFormData;
    lectureId: number;
    notificationText: string;

    ngOnInit(): void {
        this.isLoading = true;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const attachmentVideoUnitId = Number(params.get('attachmentVideoUnitId'));
                    this.lectureId = Number(parentParams.get('lectureId'));
                    return this.attachmentVideoUnitService.findById(attachmentVideoUnitId, this.lectureId);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (attachmentVideoUnitResponse: HttpResponse<AttachmentVideoUnit>) => {
                    this.attachmentVideoUnit = attachmentVideoUnitResponse.body!;
                    this.attachment = this.attachmentVideoUnit.attachment || {};
                    // breaking the connection to prevent errors in deserialization. will be reconnected on the server side
                    this.attachmentVideoUnit.attachment = undefined;
                    this.attachment.attachmentVideoUnit = undefined;

                    this.formData = {
                        formProperties: {
                            name: this.attachmentVideoUnit.name,
                            description: this.attachmentVideoUnit.description,
                            releaseDate: this.attachmentVideoUnit.releaseDate,
                            version: this.attachment.version,
                            competencyLinks: this.attachmentVideoUnit.competencyLinks,
                            videoSource: this.attachmentVideoUnit.videoSource,
                        },
                        fileProperties: {
                            fileName: this.attachment.link,
                        },
                    };
                    // Check if playlist URL is available for existing video to enable transcription generation
                    this.attachmentVideoUnitService.fetchAndUpdatePlaylistUrl(this.attachmentVideoUnit.videoSource, this.formData).subscribe({
                        next: (updatedFormData) => {
                            this.formData = updatedFormData;
                        },
                    });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData) {
        const { description, name, releaseDate, updateNotificationText, videoSource, competencyLinks } = attachmentVideoUnitFormData.formProperties;
        const { file, fileName } = attachmentVideoUnitFormData.fileProperties;

        // optional update notification text for students
        if (updateNotificationText) {
            this.notificationText = updateNotificationText;
        }

        // === Setting attachment ===
        this.attachment.name = name;
        this.attachment.releaseDate = releaseDate;
        this.attachment.attachmentType = AttachmentType.FILE;
        // === Setting attachmentVideoUnit ===

        this.attachmentVideoUnit.name = name;
        this.attachmentVideoUnit.description = description;
        this.attachmentVideoUnit.releaseDate = releaseDate;
        this.attachmentVideoUnit.competencyLinks = competencyLinks;

        this.attachmentVideoUnit.videoSource = videoSource;

        this.isLoading = true;

        const formData = new FormData();
        if (file) {
            formData.append('file', file, fileName);
        }
        formData.append('attachment', objectToJsonBlob(this.attachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(this.attachmentVideoUnit));

        this.attachmentVideoUnitService
            .update(this.lectureId, this.attachmentVideoUnit.id!, formData, this.notificationText)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe({
                next: () => this.router.navigate(['../../../'], { relativeTo: this.activatedRoute }),
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
