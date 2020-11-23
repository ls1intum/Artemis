import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { JhiAlertService } from 'ng-jhipster';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { finalize, map } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-learning-goal-management',
    templateUrl: './learning-goal-management.component.html',
    styles: [],
})
export class LearningGoalManagementComponent implements OnInit {
    courseId: number;
    isLoading = false;
    learningGoals: LearningGoal[] = [];
    constructor(private activatedRoute: ActivatedRoute, private router: Router, private learningGoalService: LearningGoalService, private alertService: JhiAlertService) {}

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
