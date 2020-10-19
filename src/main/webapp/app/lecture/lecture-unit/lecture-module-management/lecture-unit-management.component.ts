import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { finalize, map } from 'rxjs/operators';
import { HTMLUnit } from 'app/entities/lecture-unit/htmlUnit.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { AlertService } from 'app/core/alert/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HTMLUnitService } from 'app/lecture/lecture-unit/htmlUnit/htmlunit.service';

@Component({
    selector: 'jhi-lecture-unit-management',
    templateUrl: './lecture-unit-management.component.html',
    styles: [],
})
export class LectureUnitManagementComponent implements OnInit {
    lectureId: number;
    lecture: Lecture;
    lectureUnits: LectureUnit[] = [];
    isLoading = false;

    constructor(private activatedRoute: ActivatedRoute, private lectureService: LectureService, private alertService: AlertService, private htmlUnitService: HTMLUnitService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.params.subscribe((params) => {
            this.lectureId = +params['lectureId'];
            this.loadData();
        });
    }

    loadData(): void {
        this.isLoading = true;
        if (this.lectureId) {
            this.lectureService
                .find(this.lectureId)
                .pipe(
                    map((response: HttpResponse<HTMLUnit>) => response.body!),
                    finalize(() => {
                        this.isLoading = false;
                    }),
                )
                .subscribe(
                    (lecture) => {
                        this.lecture = lecture;
                        if (this.lecture?.lectureUnits) {
                            this.lectureUnits = this.lecture?.lectureUnits;
                        }
                    },
                    (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                );
        }
    }

    createHTMLUnit() {
        const newHTMLUnit = new HTMLUnit();
        newHTMLUnit.name = 'Lorem Ipsum';
        newHTMLUnit.lecture = this.lecture;
        newHTMLUnit.markdown = '';
        this.isLoading = true;
        this.htmlUnitService.create(newHTMLUnit).subscribe(
            () => this.loadData(),
            (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        );
    }
}
