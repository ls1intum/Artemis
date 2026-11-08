import { Component, OnInit, inject, signal } from '@angular/core';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { OnlineUnitFormData } from 'app/lecture/manage/lecture-units/online-unit-form/online-unit-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { OnlineUnitApi } from 'app/openapi/api/online-unit-api';
import { toLectureUnit, toLectureUnitRequest } from 'app/lecture/shared/util/generated-lecture.util';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { OnlineUnitFormComponent } from '../online-unit-form/online-unit-form.component';

@Component({
    selector: 'jhi-edit-online-unit',
    templateUrl: './edit-online-unit.component.html',
    imports: [LectureUnitLayoutComponent, OnlineUnitFormComponent],
})
export class EditOnlineUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private onlineUnitApi = inject(OnlineUnitApi);
    private alertService = inject(AlertService);

    readonly isLoading = signal(false);
    onlineUnit!: OnlineUnit; // loaded in ngOnInit() before the form can trigger an update
    readonly formData = signal<OnlineUnitFormData>(undefined!);
    lectureId!: number; // set in ngOnInit() from route params
    readonly courseId = signal<number>(undefined!); // set in ngOnInit() from route params

    ngOnInit(): void {
        this.isLoading.set(true);
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap, lectureRoute.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams, courseParams]) => {
                    const onlineUnitId = Number(params.get('onlineUnitId'));
                    this.lectureId = Number(parentParams.get('lectureId'));
                    this.courseId.set(Number(courseParams.get('courseId')));
                    return this.onlineUnitApi.getOnlineUnit(onlineUnitId, this.lectureId).pipe(map((unit) => toLectureUnit<OnlineUnit>(unit)));
                }),
                finalize(() => {
                    this.isLoading.set(false);
                }),
            )
            .subscribe({
                next: (onlineUnit: OnlineUnit) => {
                    this.onlineUnit = onlineUnit;

                    this.formData.set({
                        name: this.onlineUnit.name,
                        description: this.onlineUnit.description,
                        releaseDate: this.onlineUnit.releaseDate,
                        source: this.onlineUnit.source,
                        competencyLinks: this.onlineUnit.competencyLinks,
                    });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateOnlineUnit(formData: OnlineUnitFormData) {
        const { name, description, releaseDate, source, competencyLinks } = formData;
        this.onlineUnit.name = name;
        this.onlineUnit.description = description;
        this.onlineUnit.releaseDate = releaseDate;
        this.onlineUnit.source = source;
        this.onlineUnit.competencyLinks = competencyLinks;
        this.isLoading.set(true);
        this.onlineUnitApi
            .updateOnlineUnit(this.lectureId, toLectureUnitRequest(this.onlineUnit))
            .pipe(
                finalize(() => {
                    this.isLoading.set(false);
                    // navigate back to unit-management from :courseId/lectures/:lectureId/unit-management/online-units/:onlineUnitId/edit
                    void this.router.navigate(['../../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
