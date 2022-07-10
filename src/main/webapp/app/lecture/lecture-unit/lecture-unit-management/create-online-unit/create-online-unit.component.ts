import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { combineLatest } from 'rxjs';

@Component({
    selector: 'jhi-create-online-unit',
    templateUrl: './create-online-unit.component.html',
    styles: [],
})
export class CreateOnlineUnitComponent implements OnInit {
    onlineUnitToCreate: OnlineUnit = new OnlineUnit();
    isLoading: boolean;
    lectureId: number;
    courseId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private onlineUnitService: OnlineUnitService, private alertService: AlertService) {}

    ngOnInit(): void {
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params, parentParams]) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(parentParams.get('courseId'));
        });
        this.onlineUnitToCreate = new OnlineUnit();
    }

    createOnlineUnit(formData: OnlineUnitFormData) {
        if (!formData?.name || !formData?.source) {
            return;
        }

        const { name, description, releaseDate, source } = formData;

        this.onlineUnitToCreate.name = name || undefined;
        this.onlineUnitToCreate.releaseDate = releaseDate || undefined;
        this.onlineUnitToCreate.description = description || undefined;
        this.onlineUnitToCreate.source = source || undefined;

        this.isLoading = true;

        this.onlineUnitService
            .create(this.onlineUnitToCreate!, this.lectureId)
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
