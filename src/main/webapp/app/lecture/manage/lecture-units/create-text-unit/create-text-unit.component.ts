import { Component, OnInit, inject, signal } from '@angular/core';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { TextUnitApi } from 'app/openapi/api/text-unit-api';
import { toLectureUnit, toLectureUnitRequest } from 'app/lecture/shared/util/generated-lecture.util';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import { TextUnitFormData } from 'app/lecture/manage/lecture-units/text-unit-form/text-unit-form.component';
import { onError } from 'app/foundation/util/global.utils';
import { finalize, map } from 'rxjs/operators';
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
    private textUnitApi = inject(TextUnitApi);
    private alertService = inject(AlertService);

    textUnitToCreate: TextUnit = new TextUnit();
    readonly isLoading = signal<boolean>(undefined!);
    lectureId!: number; // set in ngOnInit() from route params
    courseId!: number; // set in ngOnInit() from route params

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

        this.isLoading.set(true);

        this.textUnitApi
            .createTextUnit(this.lectureId, toLectureUnitRequest(this.textUnitToCreate))
            .pipe(
                map((unit) => toLectureUnit<TextUnit>(unit)),
                finalize(() => {
                    this.isLoading.set(false);
                }),
            )
            .subscribe({
                next: () => {
                    void this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
