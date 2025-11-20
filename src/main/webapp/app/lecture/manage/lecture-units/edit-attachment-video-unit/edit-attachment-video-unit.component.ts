import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, finalize, map, switchMap, take } from 'rxjs/operators';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { AttachmentVideoUnit, LectureTranscriptionDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { combineLatest, of } from 'rxjs';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AccountService } from 'app/core/auth/account.service';

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
    private lectureTranscriptionService = inject(LectureTranscriptionService);
    private accountService = inject(AccountService);

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
                switchMap((attachmentVideoUnitResponse: HttpResponse<AttachmentVideoUnit>) => {
                    const attachmentVideoUnit = attachmentVideoUnitResponse.body!;
                    if (!this.accountService.isAdmin()) {
                        return of({ attachmentVideoUnit, transcription: undefined });
                    }
                    return this.lectureTranscriptionService.getTranscription(attachmentVideoUnit.id!).pipe(
                        map((transcription) => ({
                            attachmentVideoUnit,
                            transcription,
                        })),
                    );
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: ({ attachmentVideoUnit, transcription }) => {
                    this.attachmentVideoUnit = attachmentVideoUnit;
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
                        transcriptionProperties: {
                            videoTranscription: transcription ? JSON.stringify(transcription) : '',
                        },
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData) {
        const { description, name, releaseDate, updateNotificationText, videoSource, competencyLinks, generateTranscript } = attachmentVideoUnitFormData.formProperties;
        const { file, fileName } = attachmentVideoUnitFormData.fileProperties;
        const { videoTranscription } = attachmentVideoUnitFormData.transcriptionProperties || {};
        const { playlistUrl } = attachmentVideoUnitFormData || {};

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
            .pipe(
                switchMap(() => {
                    // Trigger transcript generation if enabled
                    if (generateTranscript && this.attachmentVideoUnit.id) {
                        const transcriptionUrl = playlistUrl ?? this.attachmentVideoUnit.videoSource;
                        if (transcriptionUrl) {
                            this.attachmentVideoUnitService.startTranscription(this.lectureId, this.attachmentVideoUnit.id, transcriptionUrl).subscribe();
                        }
                    }

                    if (!videoTranscription) {
                        return of(undefined);
                    }
                    let transcription: LectureTranscriptionDTO;
                    try {
                        transcription = JSON.parse(videoTranscription) as LectureTranscriptionDTO;
                    } catch (e) {
                        this.alertService.error('artemisApp.lectureUnit.attachmentVideoUnit.transcriptionInvalidJson');
                        return of(undefined);
                    }
                    transcription.lectureUnitId = this.attachmentVideoUnit.id!;
                    return this.lectureTranscriptionService.createTranscription(this.lectureId, this.attachmentVideoUnit.id!, transcription).pipe(
                        catchError((err) => {
                            onError(this.alertService, err);
                            return of(undefined);
                        }),
                    );
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: () => this.router.navigate(['../../../'], { relativeTo: this.activatedRoute }),
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
