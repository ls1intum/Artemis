import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-lecture-unit-node-details',
    templateUrl: './lecture-unit-node-details.component.html',
})
export class LectureUnitNodeDetailsComponent implements OnInit {
    @Input() lectureId: number;
    @Input() lectureUnitId: number;

    lecture: Lecture;
    lectureUnit: LectureUnit;

    isLoading = false;

    constructor(private lectureService: LectureService, private alertService: AlertService) {}

    ngOnInit() {
        if (this.lectureId && this.lectureUnitId) {
            this.loadData();
        }
    }
    private loadData() {
        this.isLoading = true;
        this.lectureService.findWithDetails(this.lectureId!).subscribe({
            next: (findLectureResult) => {
                this.lecture = findLectureResult.body!;
                if (this.lecture?.lectureUnits) {
                    this.lectureUnit = this.lecture.lectureUnits.find((lectureUnit) => lectureUnit.id === this.lectureUnitId)!;
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }
}
