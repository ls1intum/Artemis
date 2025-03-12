import { Component, OnInit, inject } from '@angular/core';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { combineLatest, forkJoin } from 'rxjs';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { VideoUnitFormComponent } from '../video-unit-form/video-unit-form.component';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureService } from 'app/lecture/lecture.service';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';

@Component({
    selector: 'jhi-edit-video-unit',
    templateUrl: './edit-video-unit.component.html',
    imports: [LectureUnitLayoutComponent, VideoUnitFormComponent],
})
export class EditVideoUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private videoUnitService = inject(VideoUnitService);
    private lectureService = inject(LectureService);
    private alertService = inject(AlertService);

    isLoading = false;
    videoUnit: VideoUnit;
    formData: VideoUnitFormData;
    lectureId: number;
    videoUnitId: number;

    availableAttachmentUnits: AttachmentUnit[];

    ngOnInit(): void {
        this.isLoading = true;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    this.videoUnitId = Number(params.get('videoUnitId'));
                    this.lectureId = Number(parentParams.get('lectureId'));
                    const videoUnitObservable = this.videoUnitService.findById(this.videoUnitId, this.lectureId);
                    const lectureObservable = this.lectureService.findWithDetails(this.lectureId);
                    return forkJoin([videoUnitObservable, lectureObservable]);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: ([videoUnit, lecture]) => {
                    this.videoUnit = videoUnit.body!;
                    this.formData = {
                        name: this.videoUnit.name,
                        description: this.videoUnit.description,
                        releaseDate: this.videoUnit.releaseDate,
                        source: this.videoUnit.source,
                        competencyLinks: this.videoUnit.competencyLinks,
                        correspondingAttachmentUnitId: this.videoUnit.correspondingAttachmentUnit?.id,
                    };

                    this.availableAttachmentUnits = [
                        {},
                        ...(lecture.body?.lectureUnits ?? []).filter((unit: LectureUnit) => {
                            if (unit.type !== LectureUnitType.ATTACHMENT) return false;

                            const attachmentUnit = unit as AttachmentUnit;
                            return !attachmentUnit.correspondingVideoUnit || attachmentUnit.correspondingVideoUnit.id === this.videoUnitId;
                        }),
                    ];
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateVideoUnit(formData: VideoUnitFormData) {
        const { name, description, releaseDate, source, competencyLinks, correspondingAttachmentUnitId } = formData;
        this.videoUnit.name = name;
        this.videoUnit.description = description;
        this.videoUnit.releaseDate = releaseDate;
        this.videoUnit.source = source;
        this.videoUnit.competencyLinks = competencyLinks;
        this.videoUnit.correspondingAttachmentUnit = correspondingAttachmentUnitId ? { id: correspondingAttachmentUnitId, type: LectureUnitType.ATTACHMENT } : undefined;

        this.isLoading = true;
        this.videoUnitService
            .update(this.videoUnit, this.lectureId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    // navigate back to unit-management from :courseId/lectures/:lectureId/unit-management/video-units/:videoUnitId/edit
                    this.router.navigate(['../../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
