import { Component, OnInit } from '@angular/core';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { TextUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/text-unit-form/text-unit-form.component';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import { JhiAlertService } from 'ng-jhipster';
import { ActivatedRoute, Router } from '@angular/router';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { onError } from 'app/shared/util/global.utils';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-edit-text-unit',
    templateUrl: './edit-text-unit.component.html',
    styles: [],
})
export class EditTextUnitComponent implements OnInit {
    isLoading = false;
    textUnit: TextUnit;
    formData: TextUnitFormData;
    lectureId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private textUnitService: TextUnitService, private alertService: JhiAlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.paramMap
            .pipe(
                take(1),
                switchMap((params) => {
                    const textUnitId = Number(params.get('textUnitId'));
                    this.lectureId = Number(params.get('lectureId'));
                    return this.textUnitService.findById(textUnitId, this.lectureId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe(
                (textUnitResponse: HttpResponse<VideoUnit>) => {
                    this.textUnit = textUnitResponse.body!;

                    this.formData = {
                        name: this.textUnit.name,
                        releaseDate: this.textUnit.releaseDate,
                        content: this.textUnit.content,
                    };
                },
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }

    updateTextUnit(formData: TextUnitFormData) {
        const { name, releaseDate, content } = formData;
        this.textUnit.name = name;
        this.textUnit.releaseDate = releaseDate;
        this.textUnit.content = content;
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
            .subscribe(
                () => {},
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }
}
