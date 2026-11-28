import { Component, OnInit, inject, signal } from '@angular/core';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { TextUnitApi } from 'app/openapi/api/text-unit-api';
import { toLectureUnit, toLectureUnitRequest } from 'app/lecture/shared/util/generated-lecture.util';
import { AlertService } from 'app/foundation/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { onError } from 'app/foundation/util/global.utils';
import { finalize, map, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { TextUnitFormComponent } from '../text-unit-form/text-unit-form.component';

@Component({
    selector: 'jhi-edit-text-unit',
    templateUrl: './edit-text-unit.component.html',
    imports: [LectureUnitLayoutComponent, TextUnitFormComponent],
})
export class EditTextUnitComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private textUnitApi = inject(TextUnitApi);
    private alertService = inject(AlertService);

    readonly isLoading = signal(false);
    textUnit!: TextUnit; // loaded in ngOnInit() before the form can trigger an update
    readonly formData = signal<TextUnitFormData>(undefined!);
    lectureId!: number; // set in ngOnInit() from route params

    ngOnInit(): void {
        this.isLoading.set(true);
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([this.activatedRoute.paramMap, lectureRoute.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const textUnitId = Number(params.get('textUnitId'));
                    this.lectureId = Number(parentParams.get('lectureId'));
                    return this.textUnitApi.getTextUnit(textUnitId, this.lectureId).pipe(map((unit) => toLectureUnit<TextUnit>(unit)));
                }),
                finalize(() => this.isLoading.set(false)),
            )
            .subscribe({
                next: (textUnit: TextUnit) => {
                    this.textUnit = textUnit;

                    this.formData.set({
                        name: this.textUnit.name,
                        releaseDate: this.textUnit.releaseDate,
                        content: this.textUnit.content,
                        competencyLinks: this.textUnit.competencyLinks,
                    });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateTextUnit(formData: TextUnitFormData) {
        const { name, releaseDate, content, competencyLinks } = formData;
        this.textUnit.name = name;
        this.textUnit.releaseDate = releaseDate;
        this.textUnit.content = content;
        this.textUnit.competencyLinks = competencyLinks;

        this.isLoading.set(true);
        this.textUnitApi
            .updateTextUnit(this.lectureId, toLectureUnitRequest(this.textUnit))
            .pipe(
                finalize(() => {
                    this.isLoading.set(false);
                    // navigate back to unit-management from :courseId/lectures/:lectureId/unit-management/text-units/:textUnitId/edit
                    void this.router.navigate(['../../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
