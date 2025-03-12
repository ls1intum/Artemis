import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { VideoUnitFormComponent } from '../video-unit-form/video-unit-form.component';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-create-video-unit',
    templateUrl: './create-video-unit.component.html',
    imports: [LectureUnitLayoutComponent, VideoUnitFormComponent],
})
export class CreateVideoUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private videoUnitService = inject(VideoUnitService);
    private alertService = inject(AlertService);
    private lectureService = inject(LectureService);

    videoUnitToCreate: VideoUnit = new VideoUnit();
    isLoading: boolean;
    lectureId: number;
    courseId: number;

    formData: VideoUnitFormData;
    availableAttachmentUnits: AttachmentUnit[];

    ngOnInit(): void {
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    this.lectureId = Number(params.get('lectureId'));
                    this.courseId = Number(parentParams.get('courseId'));
                    return this.lectureService.findWithDetails(this.lectureId);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (lecture: HttpResponse<Lecture>) => {
                    this.availableAttachmentUnits = [
                        {},
                        ...(lecture.body?.lectureUnits ?? []).filter((unit: LectureUnit) => {
                            if (unit.type !== LectureUnitType.ATTACHMENT) return false;

                            const attachmentUnit = unit as AttachmentUnit;
                            return !attachmentUnit.correspondingVideoUnit;
                        }),
                    ];
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        this.videoUnitToCreate = new VideoUnit();
    }

    createVideoUnit(formData: VideoUnitFormData) {
        if (!formData?.name || !formData?.source) {
            return;
        }

        const { name, description, releaseDate, source, competencyLinks, correspondingAttachmentUnitId } = formData;

        this.videoUnitToCreate.name = name || undefined;
        this.videoUnitToCreate.releaseDate = releaseDate || undefined;
        this.videoUnitToCreate.description = description || undefined;
        this.videoUnitToCreate.source = source || undefined;
        this.videoUnitToCreate.competencyLinks = competencyLinks || [];
        this.videoUnitToCreate.correspondingAttachmentUnit =
            correspondingAttachmentUnitId != undefined ? { id: correspondingAttachmentUnitId, type: LectureUnitType.ATTACHMENT } : undefined;

        this.isLoading = true;

        this.videoUnitService
            .create(this.videoUnitToCreate!, this.lectureId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
