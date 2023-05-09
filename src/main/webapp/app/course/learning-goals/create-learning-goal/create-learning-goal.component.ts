import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoalFormData } from 'app/course/learning-goals/learning-goal-form/learning-goal-form.component';
import { finalize, switchMap, take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-create-learning-goal',
    templateUrl: './create-learning-goal.component.html',
    styles: [],
})
export class CreateLearningGoalComponent implements OnInit {
    documentationType = DocumentationType.Competencies;
    learningGoalToCreate: LearningGoal = new LearningGoal();
    isLoading: boolean;
    courseId: number;
    lecturesWithLectureUnits: Lecture[] = [];

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private learningGoalService: LearningGoalService,
        private alertService: AlertService,
        private lectureService: LectureService,
    ) {}

    ngOnInit(): void {
        this.learningGoalToCreate = new LearningGoal();
        this.isLoading = true;
        this.activatedRoute
            .parent!.parent!.paramMap.pipe(
                take(1),
                switchMap((params) => {
                    this.courseId = Number(params.get('courseId'));
                    return this.lectureService.findAllByCourseId(this.courseId, true);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (lectureResult) => {
                    if (lectureResult.body) {
                        this.lecturesWithLectureUnits = lectureResult.body;
                        for (const lecture of this.lecturesWithLectureUnits) {
                            // server will send undefined instead of empty array, therefore we set it here as it is easier to handle
                            if (!lecture.lectureUnits) {
                                lecture.lectureUnits = [];
                            }
                        }
                    }
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    createLearningGoal(formData: LearningGoalFormData) {
        if (!formData?.title) {
            return;
        }

        const { title, description, taxonomy, masteryThreshold, connectedLectureUnits } = formData;

        this.learningGoalToCreate.title = title;
        this.learningGoalToCreate.description = description;
        this.learningGoalToCreate.taxonomy = taxonomy;
        this.learningGoalToCreate.masteryThreshold = masteryThreshold;
        this.learningGoalToCreate.lectureUnits = connectedLectureUnits;

        this.isLoading = true;

        this.learningGoalService
            .create(this.learningGoalToCreate!, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: () => {
                    // currently at /course-management/{courseId}/competency-management/create, going back to /course-management/{courseId}/competency-management/
                    this.router.navigate(['../'], { relativeTo: this.activatedRoute });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
