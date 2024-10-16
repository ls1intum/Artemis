import { Component, OnInit, inject } from '@angular/core';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';

@Component({
    selector: 'jhi-create-text-unit',
    templateUrl: './create-text-unit.component.html',
    styles: [],
})
export class CreateTextUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private textUnitService = inject(TextUnitService);
    private alertService = inject(AlertService);

    textUnitToCreate: TextUnit = new TextUnit();
    isLoading: boolean;
    lectureId: number;
    courseId: number;

    ngOnInit(): void {
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params, parentParams]) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(parentParams.get('courseId'));
        });
        this.textUnitToCreate = new TextUnit();
    }

    createTextUnit(formData: TextUnitFormData) {
        if (!formData?.name) {
            return;
        }

        const { name, releaseDate, content, competencies } = formData;

        this.textUnitToCreate.name = name;
        this.textUnitToCreate.releaseDate = releaseDate;
        this.textUnitToCreate.content = content;
        this.textUnitToCreate.competencies = competencies || [];

        this.isLoading = true;

        this.textUnitService
            .create(this.textUnitToCreate!, this.lectureId)
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
