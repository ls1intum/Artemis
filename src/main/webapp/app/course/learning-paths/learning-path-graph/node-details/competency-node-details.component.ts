import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { Competency, CompetencyProgress, getIcon, getIconTooltip } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-competency-node-details',
    templateUrl: './competency-node-details.component.html',
})
export class CompetencyNodeDetailsComponent implements OnInit {
    @Input() courseId: number;
    @Input() competencyId: number;
    @Input() competency?: Competency;
    @Output() competencyChange = new EventEmitter<Competency>();
    @Input() competencyProgress?: CompetencyProgress;
    @Output() competencyProgressChange = new EventEmitter<CompetencyProgress>();

    isLoading = false;

    constructor(
        private competencyService: CompetencyService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        if (!this.competency || !this.competencyProgress) {
            this.loadData();
        }
    }
    private loadData() {
        this.isLoading = true;
        this.competencyService.findById(this.competencyId!, this.courseId!).subscribe({
            next: (resp) => {
                this.competency = resp.body!;
                if (this.competency.userProgress?.length) {
                    this.competencyProgress = this.competency.userProgress.first()!;
                } else {
                    this.competencyProgress = { progress: 0, confidence: 0 } as CompetencyProgress;
                }
                this.isLoading = false;
                this.competencyChange.emit(this.competency);
                this.competencyProgressChange.emit(this.competencyProgress);
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    get progress(): number {
        return Math.round(this.competencyProgress?.progress ?? 0);
    }

    get confidence(): number {
        return Math.min(Math.round(((this.competencyProgress?.confidence ?? 0) / (this.competency?.masteryThreshold ?? 100)) * 100), 100);
    }

    get mastery(): number {
        const weight = 2 / 3;
        return Math.round((1 - weight) * this.progress + weight * this.confidence);
    }

    protected readonly getIcon = getIcon;
    protected readonly getIconTooltip = getIconTooltip;
}
