import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { VideoUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/video-unit-form/video-unit-form.component';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { combineLatest } from 'rxjs';

@Component({
    selector: 'jhi-create-video-unit',
    templateUrl: './create-video-unit.component.html',
    styles: [],
})
export class CreateVideoUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private videoUnitService = inject(VideoUnitService);
    private alertService = inject(AlertService);

    videoUnitToCreate: VideoUnit = new VideoUnit();
    isLoading: boolean;
    lectureId: number;
    courseId: number;

    ngOnInit(): void {
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params, parentParams]) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(parentParams.get('courseId'));
        });
        this.videoUnitToCreate = new VideoUnit();
    }

    createVideoUnit(formData: VideoUnitFormData) {
        if (!formData?.name || !formData?.source) {
            return;
        }

        const { name, description, releaseDate, source, competencies } = formData;

        this.videoUnitToCreate.name = name || undefined;
        this.videoUnitToCreate.releaseDate = releaseDate || undefined;
        this.videoUnitToCreate.description = description || undefined;
        this.videoUnitToCreate.source = source || undefined;
        this.videoUnitToCreate.competencies = competencies || [];

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
