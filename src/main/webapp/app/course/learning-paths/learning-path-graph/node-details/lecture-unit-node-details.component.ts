import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { LectureUnit, getIcon, getIconTooltip } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';

@Component({
    selector: 'jhi-lecture-unit-node-details',
    templateUrl: './lecture-unit-node-details.component.html',
})
export class LectureUnitNodeDetailsComponent implements OnInit {
    private lectureUnitService = inject(LectureUnitService);
    private alertService = inject(AlertService);

    @Input() lectureUnitId: number;

    @Input() lectureUnit?: LectureUnit;
    @Output() lectureUnitChange = new EventEmitter<LectureUnit>();

    isLoading = false;

    ngOnInit() {
        if (!this.lectureUnit) {
            this.loadData();
        }
    }
    private loadData() {
        this.isLoading = true;

        this.lectureUnitService.getLectureUnitForLearningPathNodeDetails(this.lectureUnitId!).subscribe({
            next: (lectureUnitResult) => {
                this.lectureUnit = lectureUnitResult.body!;
                this.isLoading = false;
                this.lectureUnitChange.emit(this.lectureUnit);
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    protected readonly getIcon = getIcon;
    protected readonly getIconTooltip = getIconTooltip;
}
