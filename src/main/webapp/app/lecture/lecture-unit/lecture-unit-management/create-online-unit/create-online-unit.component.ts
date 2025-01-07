import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { OnlineUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/online-unit-form/online-unit-form.component';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { finalize } from 'rxjs/operators';
import { combineLatest } from 'rxjs';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { OnlineUnitFormComponent } from '../online-unit-form/online-unit-form.component';

@Component({
    selector: 'jhi-create-online-unit',
    templateUrl: './create-online-unit.component.html',
    imports: [LectureUnitLayoutComponent, OnlineUnitFormComponent],
})
export class CreateOnlineUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private onlineUnitService = inject(OnlineUnitService);
    private alertService = inject(AlertService);

    onlineUnitToCreate: OnlineUnit = new OnlineUnit();
    isLoading: boolean;
    lectureId: number;
    courseId: number;

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

        const { name, description, releaseDate, source, competencyLinks } = formData;

        this.onlineUnitToCreate.name = name || undefined;
        this.onlineUnitToCreate.releaseDate = releaseDate || undefined;
        this.onlineUnitToCreate.description = description || undefined;
        this.onlineUnitToCreate.source = source || undefined;
        this.onlineUnitToCreate.competencyLinks = competencyLinks || [];

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
