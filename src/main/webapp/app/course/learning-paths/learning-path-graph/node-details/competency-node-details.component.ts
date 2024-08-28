import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { Competency, CompetencyProgress, getIcon, getMastery, getProgress } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

@Component({
    selector: 'jhi-competency-node-details',
    templateUrl: './competency-node-details.component.html',
})
export class CompetencyNodeDetailsComponent implements OnInit {
    @Input() courseId: number;
    @Input() competencyId: number;
    @Input() competency?: Competency;
    @Output() competencyChange = new EventEmitter<Competency>();
    @Input() competencyProgress: CompetencyProgress;

    isLoading = false;

    protected readonly getIcon = getIcon;

    constructor(
        private courseCompetencyService: CourseCompetencyService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        if (!this.competency) {
            this.loadData();
        }
    }
    private loadData() {
        this.isLoading = true;
        this.courseCompetencyService.findById(this.competencyId!, this.courseId!).subscribe({
            next: (resp) => {
                this.competency = resp.body!;
                this.isLoading = false;
                this.competencyChange.emit(this.competency);
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    get progress(): number {
        return getProgress(this.competencyProgress!);
    }

    get mastery(): number {
        return getMastery(this.competencyProgress);
    }
}
