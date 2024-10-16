import { Component, OnInit, inject } from '@angular/core';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { onError } from 'app/shared/util/global.utils';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';

@Component({
    selector: 'jhi-edit-text-unit',
    templateUrl: './edit-text-unit.component.html',
    styles: [],
})
export class EditTextUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private textUnitService = inject(TextUnitService);
    private alertService = inject(AlertService);

    isLoading = false;
    textUnit: TextUnit;
    formData: TextUnitFormData;
    lectureId: number;

    ngOnInit(): void {
        this.isLoading = true;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const textUnitId = Number(params.get('textUnitId'));
                    this.lectureId = Number(parentParams.get('lectureId'));
                    return this.textUnitService.findById(textUnitId, this.lectureId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (textUnitResponse: HttpResponse<TextUnit>) => {
                    this.textUnit = textUnitResponse.body!;

                    this.formData = {
                        name: this.textUnit.name,
                        releaseDate: this.textUnit.releaseDate,
                        content: this.textUnit.content,
                        competencies: this.textUnit.competencies,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateTextUnit(formData: TextUnitFormData) {
        const { name, releaseDate, content, competencies } = formData;
        this.textUnit.name = name;
        this.textUnit.releaseDate = releaseDate;
        this.textUnit.content = content;
        this.textUnit.competencies = competencies;
        this.isLoading = true;
        this.textUnitService
            .update(this.textUnit, this.lectureId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    // navigate back to unit-management from :courseId/lectures/:lectureId/unit-management/text-units/:textUnitId/edit
                    this.router.navigate(['../../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
