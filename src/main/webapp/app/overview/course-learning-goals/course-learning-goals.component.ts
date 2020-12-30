import { Component, Input, OnInit } from '@angular/core';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { forkJoin } from 'rxjs';
import { IndividualLearningGoalProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';

@Component({
    selector: 'jhi-course-learning-goals',
    templateUrl: './course-learning-goals.component.html',
    styles: [],
})
export class CourseLearningGoalsComponent implements OnInit {
    @Input()
    courseId: number;

    isLoading = false;
    learningGoals: LearningGoal[] = [];
    learningGoalIdToLearningGoalProgress = new Map<number, IndividualLearningGoalProgress>();

    constructor(private activatedRoute: ActivatedRoute, private alertService: JhiAlertService, private learningGoalService: LearningGoalService) {}

    ngOnInit(): void {
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    getLearningGoalProgress(learningGoal: LearningGoal) {
        return this.learningGoalIdToLearningGoalProgress.get(learningGoal.id!);
    }

    loadData() {
        this.isLoading = true;
        this.learningGoalService
            .getAllForCourse(this.courseId)
            .switchMap((res) => {
                this.learningGoals = res.body!;

                const progressObservable = this.learningGoals.map((lg) => {
                    return this.learningGoalService.getProgress(lg.id!, this.courseId);
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
                        this.learningGoalIdToLearningGoalProgress.set(learningGoalProgress.learningGoalId, learningGoalProgress);
                    }
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }
    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }
}
