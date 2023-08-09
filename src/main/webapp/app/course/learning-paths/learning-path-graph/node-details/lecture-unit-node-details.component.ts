import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnit, getIcon, getIconTooltip } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-lecture-unit-node-details',
    templateUrl: './lecture-unit-node-details.component.html',
})
export class LectureUnitNodeDetailsComponent implements OnInit {
    @Input() lectureId: number;
    @Input() lectureUnitId: number;

    @Input() lecture?: Lecture;
    @Output() lectureChange = new EventEmitter<Lecture>();
    @Input() lectureUnit?: LectureUnit;
    @Output() lectureUnitChange = new EventEmitter<LectureUnit>();

    isLoading = false;

    constructor(private lectureService: LectureService, private alertService: AlertService) {}

    ngOnInit() {
        console.log(this.lectureUnit);
        if (!this.lecture || !this.lectureUnit) {
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
                this.lectureChange.emit(this.lecture);
                this.lectureUnitChange.emit(this.lectureUnit);
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    protected readonly getIcon = getIcon;
    protected readonly getIconTooltip = getIconTooltip;
}
