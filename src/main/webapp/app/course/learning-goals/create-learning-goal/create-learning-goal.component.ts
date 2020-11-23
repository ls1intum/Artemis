import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-create-learning-goal',
    templateUrl: './create-learning-goal.component.html',
    styles: [],
})
export class CreateLearningGoalComponent implements OnInit {
    learningGoalToCreate: LearningGoal = new LearningGoal();
    isLoading: boolean;
    courseId: number;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private learningGoalService: LearningGoalService, private alertService: JhiAlertService) {}

    ngOnInit(): void {
        this.activatedRoute.paramMap.subscribe((params) => {
            this.courseId = Number(params.get('courseId'));
        });
        this.learningGoalToCreate = new LearningGoal();
    }

    createLearningGoal(formData: LearningGoalFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description } = formData;

        this.learningGoalToCreate.title = title;
        this.learningGoalToCreate.description = description;

        this.isLoading = true;

        this.learningGoalService
            .create(this.learningGoalToCreate!, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                () => {
                    // ToDo change to correct path
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                },
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }
}
