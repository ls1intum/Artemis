import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LearningGoalManagementService } from 'app/learning-goal/learning-goal-management/learning-goal-management.service';
import { filter, map, switchMap } from 'rxjs/operators';
import { HttpResponse } from '@angular/common/http';
import { LearningGoal } from 'app/entities/learning-goal.model';

@Component({
    selector: 'jhi-learning-goal-edit',
    templateUrl: './learning-goal-edit.component.html',
    styles: [],
})
export class LearningGoalEditComponent implements OnInit {
    constructor(private route: ActivatedRoute, private router: Router, private learningGoalManagementService: LearningGoalManagementService) {}

    learningGoal: LearningGoal;

    ngOnInit(): void {
        this.route.paramMap
            .pipe(
                map((params) => params.get('goalid')),
                switchMap((goalId: string) => this.learningGoalManagementService.findById(+goalId)),
                filter((response: HttpResponse<LearningGoal>) => response.ok),
                map((response: HttpResponse<LearningGoal>) => response.body!),
            )
            .subscribe((learningGoal: LearningGoal) => {
                this.learningGoal = learningGoal;
            });
    }

    updateBook(learningGoal: LearningGoal) {
        this.learningGoalManagementService.updateLearningGoal(learningGoal).subscribe(() => {
            this.router.navigate(['/course-management', this.learningGoal?.course?.id, 'goals']);
        });
    }
}
