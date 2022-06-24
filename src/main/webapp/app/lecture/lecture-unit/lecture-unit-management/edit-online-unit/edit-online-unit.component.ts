import { Component, OnInit } from '@angular/core';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';

@Component({
    selector: 'jhi-edit-online-unit',
    templateUrl: './edit-online-unit.component.html',
    styles: [],
})
export class EditOnlineUnitComponent implements OnInit {
    isLoading = false;
    onlineUnit: OnlineUnit;
    formData: OnlineUnitFormData;
    lectureId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private onlineUnitService: OnlineUnitService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const onlineUnitId = Number(params.get('onlineUnitId'));
                    this.lectureId = Number(parentParams.get('lectureId'));
                    return this.onlineUnitService.findById(onlineUnitId, this.lectureId);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (onlineUnitResponse: HttpResponse<OnlineUnit>) => {
                    this.onlineUnit = onlineUnitResponse.body!;

                    this.formData = {
                        name: this.onlineUnit.name,
                        description: this.onlineUnit.description,
                        releaseDate: this.onlineUnit.releaseDate,
                        source: this.onlineUnit.source,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateOnlineUnit(formData: OnlineUnitFormData) {
        const { name, description, releaseDate, source } = formData;
        this.onlineUnit.name = name;
        this.onlineUnit.description = description;
        this.onlineUnit.releaseDate = releaseDate;
        this.onlineUnit.source = source;
        this.isLoading = true;
        this.onlineUnitService
            .update(this.onlineUnit, this.lectureId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    // navigate back to unit-management from :courseId/lectures/:lectureId/unit-management/online-units/:onlineUnitId/edit
                    this.router.navigate(['../../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
