import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { JhiAlertService } from 'ng-jhipster';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { forkJoin, Subject } from 'rxjs';
import { CourseLearningGoalProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';

@Component({
    selector: 'jhi-learning-goal-management',
    templateUrl: './learning-goal-management.component.html',
    styleUrls: ['./learning-goal-management.component.scss'],
})
export class LearningGoalManagementComponent implements OnInit, OnDestroy {
    courseId: number;
    isLoading = false;
    learningGoals: LearningGoal[] = [];
    learningGoalIdToLearningGoalCourseProgress = new Map<number, CourseLearningGoalProgress>();
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private learningGoalService: LearningGoalService, private alertService: JhiAlertService) {}

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    ngOnInit(): void {
        this.activatedRoute.parent!.params.subscribe((params) => {
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

    getLearningGoalCourseProgress(learningGoal: LearningGoal) {
        return this.learningGoalIdToLearningGoalCourseProgress.get(learningGoal.id!);
    }

    loadData() {
        this.isLoading = true;
        this.learningGoalService
            .getAllForCourse(this.courseId)
            .switchMap((res) => {
                this.learningGoals = res.body!;

                const progressObservable = this.learningGoals.map((lg) => {
                    return this.learningGoalService.getCourseProgress(lg.id!, this.courseId);
                });

                return forkJoin(progressObservable);
            })
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (learningGoalProgressResponses) => {
                    for (const learningGoalProgressResponse of learningGoalProgressResponses) {
                        const learningGoalProgress = learningGoalProgressResponse.body!;
                        this.learningGoalIdToLearningGoalCourseProgress.set(learningGoalProgress.learningGoalId, learningGoalProgress);
                    }
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }
}
