import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningGoalManagementService } from 'app/learning-goal/learning-goal-management/learning-goal-management.service';
import { concatMap, filter, map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LearningGoal } from 'app/entities/learning-goal.model';
import { AlertService } from 'app/core/alert/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-learning-goal-edit',
    templateUrl: './learning-goal-edit.component.html',
    styles: [],
})
export class LearningGoalEditComponent implements OnInit, OnDestroy {
    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private learningGoalManagementService: LearningGoalManagementService,
        private alertService: AlertService,
    ) {}

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    learningGoal: LearningGoal;

    ngOnInit(): void {
        this.activatedRoute.paramMap
            .pipe(
                map((params) => params.get('goalid')),
                concatMap((goalId: string) => this.learningGoalManagementService.findById(+goalId)),
                filter((response: HttpResponse<LearningGoal>) => response.ok),
                map((response: HttpResponse<LearningGoal>) => response.body!),
            )
            .subscribe(
                (learningGoal: LearningGoal) => {
                    this.learningGoal = learningGoal;
                },
                (err: HttpErrorResponse) => onError(this.alertService, err),
            );
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    updateLearningGoal(learningGoal: LearningGoal) {
        this.learningGoalManagementService.updateLearningGoal(learningGoal).subscribe(
            () => {
                this.router.navigate(['/course-management', this.learningGoal?.course?.id, 'goals']);
            },
            (err: HttpErrorResponse) => onError(this.alertService, err),
        );
    }

    deleteLearningGoal(learningGoal: LearningGoal) {
        this.learningGoalManagementService.deleteLearningGoal(learningGoal).subscribe(
            () => {
                this.dialogErrorSource.next('');
                this.router.navigate(['/course-management', this.learningGoal?.course?.id, 'goals']);
            },
            (err: HttpErrorResponse) => this.dialogErrorSource.next(err.message),
        );
    }
}
