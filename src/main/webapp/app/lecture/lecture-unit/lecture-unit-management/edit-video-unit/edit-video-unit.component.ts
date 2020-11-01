import { Component, OnInit } from '@angular/core';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { JhiAlertService } from 'ng-jhipster';
import { onError } from 'app/shared/util/global.utils';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-edit-video-unit',
    templateUrl: './edit-video-unit.component.html',
    styles: [],
})
export class EditVideoUnitComponent implements OnInit {
    isLoading = false;
    videoUnit: VideoUnit;
    formData: VideoUnitFormData;
    lectureId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private videoUnitService: VideoUnitService, private alertService: JhiAlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.paramMap
            .pipe(
                take(1),
                switchMap((params) => {
                    const videoUnitId = Number(params.get('videoUnitId'));
                    this.lectureId = Number(params.get('lectureId'));
                    return this.videoUnitService.findById(videoUnitId);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (videoUnitResponse: HttpResponse<VideoUnit>) => {
                    this.videoUnit = videoUnitResponse.body!;

                    this.formData = {
                        name: this.videoUnit.name,
                        description: this.videoUnit.description,
                        releaseDate: this.videoUnit.releaseDate,
                        source: this.videoUnit.source,
                    };
                },
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }

    updateVideoUnit(formData: VideoUnitFormData) {
        const { name, description, releaseDate, source } = formData;
        this.videoUnit.name = name;
        this.videoUnit.description = description;
        this.videoUnit.releaseDate = releaseDate;
        this.videoUnit.source = source;
        this.isLoading = true;
        this.videoUnitService
            .update(this.videoUnit, this.lectureId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    this.router.navigate(['../../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe(
                () => {},
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }
}
