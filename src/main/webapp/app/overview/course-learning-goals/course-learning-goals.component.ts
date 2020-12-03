import { Component, Input, OnInit } from '@angular/core';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { onError } from 'app/shared/util/global.utils';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LearningGoal } from 'app/entities/learningGoal.model';

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

    constructor(private activatedRoute: ActivatedRoute, private alertService: JhiAlertService, private learningGoalService: LearningGoalService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute
            .parent!.params.pipe(
                take(1),
                switchMap((params) => {
                    this.courseId = +params['courseId'];
                    return this.learningGoalService.getAllForCourse(this.courseId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe(
                (learningGoalResponse: HttpResponse<LearningGoal[]>) => {
                    if (learningGoalResponse.body) {
                        this.learningGoals = learningGoalResponse.body;
                    }
                },
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }

    identify(index: number, learningGoal: LearningGoal) {
        return `${index}-${learningGoal.id}`;
    }
}
