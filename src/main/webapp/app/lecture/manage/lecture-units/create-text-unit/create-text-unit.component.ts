import { Component, OnInit, inject } from '@angular/core';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { TextUnitService } from 'app/lecture/manage/lecture-units/textUnit.service';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { TextUnitFormComponent } from '../text-unit-form/text-unit-form.component';

@Component({
    selector: 'jhi-create-text-unit',
    templateUrl: './create-text-unit.component.html',
    imports: [LectureUnitLayoutComponent, TextUnitFormComponent],
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

        const { name, releaseDate, content, competencyLinks } = formData;

        this.textUnitToCreate.name = name;
        this.textUnitToCreate.releaseDate = releaseDate;
        this.textUnitToCreate.content = content;
        this.textUnitToCreate.competencyLinks = competencyLinks || [];

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
