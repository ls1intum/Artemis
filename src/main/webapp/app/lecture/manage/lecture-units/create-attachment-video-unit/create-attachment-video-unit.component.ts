import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AttachmentVideoUnit, LectureTranscriptionDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { combineLatest, of } from 'rxjs';
import { catchError, finalize, map, switchMap } from 'rxjs/operators';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { LectureTranscriptionService } from '../../services/lecture-transcription.service';

@Component({
    selector: 'jhi-create-attachment-video-unit',
    templateUrl: './create-attachment-video-unit.component.html',
    imports: [LectureUnitLayoutComponent, AttachmentVideoUnitFormComponent],
})
export class CreateAttachmentVideoUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private alertService = inject(AlertService);
    private lectureTranscriptionService = inject(LectureTranscriptionService);

    @ViewChild('attachmentVideoUnitForm')
    attachmentVideoUnitForm: AttachmentVideoUnitFormComponent;
    attachmentVideoUnitToCreate: AttachmentVideoUnit = new AttachmentVideoUnit();
    attachmentToCreate: Attachment = new Attachment();

    isLoading: boolean;
    lectureId: number;
    courseId: number;

    ngOnInit() {
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params, parentParams]) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(parentParams.get('courseId'));
        });
        this.attachmentVideoUnitToCreate = new AttachmentVideoUnit();
        this.attachmentToCreate = new Attachment();
    }

    createAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData): void {
        const { name, videoSource, description, releaseDate, competencyLinks, generateTranscript } = attachmentVideoUnitFormData?.formProperties || {};
        const { file, fileName } = attachmentVideoUnitFormData?.fileProperties || {};
        const { videoTranscription } = attachmentVideoUnitFormData?.transcriptionProperties || {};
        const playlistUrl = attachmentVideoUnitFormData.playlistUrl;

        if (!name || (!(file && fileName) && !videoSource)) {
            return;
        }

        // === Setting attachment ===
        this.attachmentToCreate.name = name;
        this.attachmentToCreate.releaseDate = releaseDate;
        this.attachmentToCreate.attachmentType = AttachmentType.FILE;
        this.attachmentToCreate.version = 1;
        this.attachmentToCreate.uploadDate = dayjs();

        // === Setting attachmentVideoUnit ===
        this.attachmentVideoUnitToCreate.name = name;
        this.attachmentVideoUnitToCreate.releaseDate = releaseDate;
        this.attachmentVideoUnitToCreate.description = description;
        this.attachmentVideoUnitToCreate.videoSource = videoSource;
        this.attachmentVideoUnitToCreate.competencyLinks = competencyLinks || [];

        this.isLoading = true;

        const formData = new FormData();

        if (!!file && !!fileName) {
            formData.append('file', file, fileName);
            formData.append('attachment', objectToJsonBlob(this.attachmentToCreate));
        }
        formData.append('attachmentVideoUnit', objectToJsonBlob(this.attachmentVideoUnitToCreate));

        this.attachmentVideoUnitService
            .create(formData, this.lectureId)
            .pipe(
                switchMap((response) => {
                    const lectureUnit = response.body!;

                    // Handle automatic transcription generation if requested
                    if (generateTranscript && lectureUnit?.id) {
                        const transcriptionUrl = playlistUrl ?? lectureUnit.videoSource;
                        if (transcriptionUrl) {
                            this.attachmentVideoUnitService.startTranscription(this.lectureId, lectureUnit.id, transcriptionUrl).subscribe({
                                error: (err) => onError(this.alertService, err),
                            });
                        }
                    }

                    if (!videoTranscription) {
                        return of(lectureUnit);
                    }
                    let transcription: LectureTranscriptionDTO;
                    try {
                        transcription = JSON.parse(videoTranscription) as LectureTranscriptionDTO;
                    } catch {
                        this.alertService.error('artemisApp.lectureUnit.attachmentVideoUnit.transcriptionInvalidJson');
                        return of(lectureUnit);
                    }
                    transcription.lectureUnitId = lectureUnit.id!;
                    return this.lectureTranscriptionService.createTranscription(this.lectureId, lectureUnit.id!, transcription).pipe(
                        map(() => lectureUnit),
                        catchError((err) => {
                            onError(this.alertService, err);
                            return of(lectureUnit);
                        }),
                    );
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: () => this.router.navigate(['../../'], { relativeTo: this.activatedRoute }),
                error: (res: HttpErrorResponse | Error) => {
                    if (res instanceof Error) {
                        this.alertService.error(res.message);
                        return;
                    }
                    if (res.error?.params === 'file' && res?.error?.title) {
                        this.alertService.error(res.error.title);
                    } else {
                        onError(this.alertService, res);
                    }
                },
            });
    }
}
