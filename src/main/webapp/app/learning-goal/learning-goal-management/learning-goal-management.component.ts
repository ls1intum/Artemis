import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LearningGoalManagementService } from 'app/learning-goal/learning-goal-management/learning-goal-management.service';
import { LearningGoal } from 'app/entities/learning-goal.model';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-learning-goal-management',
    templateUrl: './learning-goal-management.component.html',
    styleUrls: ['./learning-goal-management.component.scss'],
})
export class LearningGoalManagementComponent implements OnInit {
    learningGoals: LearningGoal[] = [];

    constructor(private learningGoalManagementService: LearningGoalManagementService, private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.activatedRoute.paramMap.subscribe((paramMap) => {
            const courseId = paramMap.get('courseId');

            if (courseId) {
                this.learningGoalManagementService.findAllByCourseId(+courseId).subscribe(
                    (res) => {
                        if (res.body) {
                            this.learningGoals = res.body;
                        }
                    },
                    (err: HttpErrorResponse) => {
                        // ToDo: Implement correct error handling
                    },
                );
            }
        });
    }
}
