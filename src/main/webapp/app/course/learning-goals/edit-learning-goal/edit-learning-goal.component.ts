import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, switchMap, take } from 'rxjs/operators';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureService } from 'app/lecture/lecture.service';
import { forkJoin, combineLatest } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-edit-learning-goal',
    templateUrl: './edit-learning-goal.component.html',
    styles: [],
})
export class EditLearningGoalComponent implements OnInit {
    isLoading = false;
    learningGoal: LearningGoal;
    lecturesWithLectureUnits: Lecture[] = [];
    formData: LearningGoalFormData;
    courseId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private lectureService: LectureService,
        private router: Router,
        private learningGoalService: LearningGoalService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const learningGoalId = Number(params.get('learningGoalId'));
                    this.courseId = Number(parentParams.get('courseId'));

                    const learningGoalObservable = this.learningGoalService.findById(learningGoalId, this.courseId);
                    const lecturesObservable = this.lectureService.findAllByCourseId(this.courseId, true);
                    return forkJoin([learningGoalObservable, lecturesObservable]);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: ([learningGoalResult, lecturesResult]) => {
                    if (learningGoalResult.body) {
                        this.learningGoal = learningGoalResult.body;
                        // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                        if (!this.learningGoal.lectureUnits) {
                            this.learningGoal.lectureUnits = [];
                        }
                    }
                    if (lecturesResult.body) {
                        this.lecturesWithLectureUnits = lecturesResult.body;
                        for (const lecture of this.lecturesWithLectureUnits) {
                            // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                            if (!lecture.lectureUnits) {
                                lecture.lectureUnits = [];
                            }
                        }
                    }

                    this.formData = {
                        id: this.learningGoal.id,
                        title: this.learningGoal.title,
                        description: this.learningGoal.description,
                        connectedLectureUnits: this.learningGoal.lectureUnits,
                        taxonomy: this.learningGoal.taxonomy,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateLearningGoal(formData: LearningGoalFormData) {
        const { title, description, taxonomy, connectedLectureUnits } = formData;

        this.learningGoal.title = title;
        this.learningGoal.description = description;
        this.learningGoal.taxonomy = taxonomy;
        this.learningGoal.lectureUnits = connectedLectureUnits;

        this.isLoading = true;

        this.learningGoalService
            .update(this.learningGoal, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    // currently at /course-management/{courseId}/goal-management/{learninGoalId}/edit, going back to /course-management/{courseId}/goal-management/
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
