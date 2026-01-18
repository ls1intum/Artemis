import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, finalize, switchMap, take } from 'rxjs/operators';
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
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditAttachmentVideoUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private alertService = inject(AlertService);
    private destroyRef = inject(DestroyRef);

    readonly attachmentVideoUnitForm = viewChild<AttachmentVideoUnitFormComponent>('attachmentVideoUnitForm');

    readonly isLoading = signal(false);
    readonly attachmentVideoUnit = signal<AttachmentVideoUnit | undefined>(undefined);
    readonly attachment = signal<Attachment | undefined>(undefined);
    readonly formData = signal<AttachmentVideoUnitFormData | undefined>(undefined);
    readonly lectureId = signal<number | undefined>(undefined);
    readonly notificationText = signal<string | undefined>(undefined);

    ngOnInit(): void {
        this.isLoading.set(true);
        const lectureRoute = this.activatedRoute.parent?.parent;
        if (!lectureRoute) {
            this.isLoading.set(false);
            return;
        }
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                filter(([params, parentParams]) => {
                    const hasUnitId = params.get('attachmentVideoUnitId') !== null;
                    const hasLectureId = parentParams.get('lectureId') !== null;
                    return hasUnitId && hasLectureId;
                }),
                switchMap(([params, parentParams]) => {
                    const attachmentVideoUnitId = Number(params.get('attachmentVideoUnitId'));
                    const lectureId = Number(parentParams.get('lectureId'));
                    this.lectureId.set(lectureId);
                    return this.attachmentVideoUnitService.findById(attachmentVideoUnitId, lectureId);
                }),
                finalize(() => this.isLoading.set(false)),
            )
            .subscribe({
                next: (attachmentVideoUnitResponse: HttpResponse<AttachmentVideoUnit>) => {
                    const unit = attachmentVideoUnitResponse.body;
                    if (!unit) {
                        return;
                    }
                    const attach = unit.attachment || ({} as Attachment);
                    // breaking the connection to prevent errors in deserialization. will be reconnected on the server side
                    unit.attachment = undefined;
                    attach.attachmentVideoUnit = undefined;

                    this.attachmentVideoUnit.set(unit);
                    this.attachment.set(attach);
                    this.formData.set({
                        formProperties: {
                            name: unit.name,
                            description: unit.description,
                            releaseDate: unit.releaseDate,
                            version: attach.version,
                            competencyLinks: unit.competencyLinks,
                            videoSource: unit.videoSource,
                        },
                        fileProperties: {
                            fileName: attach.link,
                        },
                        videoFileProperties: {
                            videoFileName: undefined,
                        },
                    });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData) {
        const { description, name, releaseDate, updateNotificationText, videoSource, competencyLinks } = attachmentVideoUnitFormData.formProperties;
        const { file, fileName } = attachmentVideoUnitFormData.fileProperties;
        const { videoFile, videoFileName } = attachmentVideoUnitFormData.videoFileProperties ?? {};
        const { uploadProgressCallback } = attachmentVideoUnitFormData;
        // optional update notification text for students
        if (updateNotificationText) {
            this.notificationText.set(updateNotificationText);
        }

        const currentAttachment = this.attachment();
        const currentUnit = this.attachmentVideoUnit();
        const lectureId = this.lectureId();

        if (!currentAttachment || !currentUnit || lectureId === undefined || currentUnit.id === undefined) {
            return;
        }

        // Create new objects to avoid mutating signal-stored objects
        const updatedAttachment = Object.assign(new Attachment(), currentAttachment, {
            name,
            releaseDate,
            attachmentType: AttachmentType.FILE,
        });

        const updatedUnit = Object.assign(new AttachmentVideoUnit(), currentUnit, {
            name,
            description,
            releaseDate,
            competencyLinks,
            videoSource,
        });

        this.isLoading.set(true);

        const formData = new FormData();
        // Add PDF file if provided
        if (file) {
            formData.append('file', file, fileName);
        }
        // Add video file if provided
        if (videoFile && videoFileName) {
            formData.append('videoFile', videoFile, videoFileName);
        }
        formData.append('attachment', objectToJsonBlob(updatedAttachment));
        formData.append('attachmentVideoUnit', objectToJsonBlob(updatedUnit));

        this.attachmentVideoUnitService
            .update(lectureId, currentUnit.id, formData, this.notificationText(), uploadProgressCallback)
            .pipe(
                takeUntilDestroyed(this.destroyRef),
                finalize(() => this.isLoading.set(false)),
            )
            .subscribe({
                next: () => this.router.navigate(['../../../'], { relativeTo: this.activatedRoute }),
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
