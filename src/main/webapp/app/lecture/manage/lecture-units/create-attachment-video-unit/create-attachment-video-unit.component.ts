import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { AttachmentVideoUnitFormComponent, AttachmentVideoUnitFormData } from 'app/lecture/manage/lecture-units/attachment-video-unit-form/attachment-video-unit-form.component';
import { combineLatest } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';

@Component({
    selector: 'jhi-create-attachment-video-unit',
    templateUrl: './create-attachment-video-unit.component.html',
    imports: [LectureUnitLayoutComponent, AttachmentVideoUnitFormComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateAttachmentVideoUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private alertService = inject(AlertService);
    private destroyRef = inject(DestroyRef);

    readonly attachmentVideoUnitForm = viewChild<AttachmentVideoUnitFormComponent>('attachmentVideoUnitForm');
    attachmentVideoUnitToCreate: AttachmentVideoUnit = new AttachmentVideoUnit();
    attachmentToCreate: Attachment = new Attachment();

    readonly isLoading = signal(false);
    readonly lectureId = signal<number | undefined>(undefined);
    readonly courseId = signal<number | undefined>(undefined);

    ngOnInit() {
        const lectureRoute = this.activatedRoute.parent?.parent;
        const courseRoute = lectureRoute?.parent;
        if (!lectureRoute || !courseRoute) {
            return;
        }
        combineLatest([lectureRoute.paramMap, courseRoute.paramMap])
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(([params, parentParams]) => {
                const lectureIdParam = params.get('lectureId');
                const courseIdParam = parentParams.get('courseId');
                this.lectureId.set(lectureIdParam ? Number(lectureIdParam) : undefined);
                this.courseId.set(courseIdParam ? Number(courseIdParam) : undefined);
            });
        this.attachmentVideoUnitToCreate = new AttachmentVideoUnit();
        this.attachmentToCreate = new Attachment();
    }

    createAttachmentVideoUnit(attachmentVideoUnitFormData: AttachmentVideoUnitFormData): void {
        const { name, videoSource, description, releaseDate, competencyLinks } = attachmentVideoUnitFormData?.formProperties || {};
        const { file, fileName } = attachmentVideoUnitFormData?.fileProperties || {};
        const { videoFile, videoFileName } = attachmentVideoUnitFormData.videoFileProperties ?? {};

        const lectureId = this.lectureId();
        if (!name || lectureId === undefined || (!(file && fileName) && !(videoFile && videoFileName) && !videoSource)) {
            return;
        }

        // === Setting attachment (PDF only) ===
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

        this.isLoading.set(true);

        const formData = new FormData();

        // Add PDF file if provided
        if (!!file && !!fileName) {
            formData.append('file', file, fileName);
            formData.append('attachment', objectToJsonBlob(this.attachmentToCreate));
        }

        // Add video file if provided
        if (!!videoFile && !!videoFileName) {
            formData.append('videoFile', videoFile, videoFileName);
        }

        formData.append('attachmentVideoUnit', objectToJsonBlob(this.attachmentVideoUnitToCreate));

        this.attachmentVideoUnitService
            .create(formData, lectureId)
            .pipe(finalize(() => this.isLoading.set(false)))
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
