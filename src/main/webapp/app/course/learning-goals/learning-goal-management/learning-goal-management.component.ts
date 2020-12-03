import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { JhiAlertService } from 'ng-jhipster';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { finalize, map } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-learning-goal-management',
    templateUrl: './learning-goal-management.component.html',
    styleUrls: ['./learning-goal-management.component.scss'],
})
export class LearningGoalManagementComponent implements OnInit, OnDestroy {
    courseId: number;
    isLoading = false;
    learningGoals: LearningGoal[] = [];
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private learningGoalService: LearningGoalService, private alertService: JhiAlertService) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }

    deleteLearningGoal(learningGoalId: number) {
        this.learningGoalService.delete(learningGoalId, this.courseId).subscribe(
            () => {
                this.dialogErrorSource.next('');
                this.loadData();
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    loadData() {
        this.isLoading = true;
        this.learningGoalService
            .getAllForCourse(this.courseId)
            .pipe(
                map((response: HttpResponse<LearningGoal[]>) => response.body!),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (learningGoals) => {
                    if (learningGoals) {
                        this.learningGoals = learningGoals;
                    }
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }
}
