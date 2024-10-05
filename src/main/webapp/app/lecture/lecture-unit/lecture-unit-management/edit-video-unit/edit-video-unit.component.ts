import { Component, OnInit, inject } from '@angular/core';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';

@Component({
    selector: 'jhi-edit-video-unit',
    templateUrl: './edit-video-unit.component.html',
    styles: [],
})
export class EditVideoUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private videoUnitService = inject(VideoUnitService);
    private alertService = inject(AlertService);

    isLoading = false;
    videoUnit: VideoUnit;
    formData: VideoUnitFormData;
    lectureId: number;

    ngOnInit(): void {
        this.isLoading = true;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const videoUnitId = Number(params.get('videoUnitId'));
                    this.lectureId = Number(parentParams.get('lectureId'));
                    return this.videoUnitService.findById(videoUnitId, this.lectureId);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (videoUnitResponse: HttpResponse<VideoUnit>) => {
                    this.videoUnit = videoUnitResponse.body!;

                    this.formData = {
                        name: this.videoUnit.name,
                        description: this.videoUnit.description,
                        releaseDate: this.videoUnit.releaseDate,
                        source: this.videoUnit.source,
                        competencies: this.videoUnit.competencies,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateVideoUnit(formData: VideoUnitFormData) {
        const { name, description, releaseDate, source, competencies } = formData;
        this.videoUnit.name = name;
        this.videoUnit.description = description;
        this.videoUnit.releaseDate = releaseDate;
        this.videoUnit.source = source;
        this.videoUnit.competencies = competencies;
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
