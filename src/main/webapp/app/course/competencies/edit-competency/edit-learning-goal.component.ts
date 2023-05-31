import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { Competency } from 'app/entities/competency.model';
import { CompetencyFormData } from 'app/course/competencies/competency-form/competency-form.component';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { finalize, switchMap, take } from 'rxjs/operators';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureService } from 'app/lecture/lecture.service';
import { combineLatest, forkJoin } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-edit-learning-goal',
    templateUrl: './edit-learning-goal.component.html',
    styles: [],
})
export class EditLearningGoalComponent implements OnInit {
    isLoading = false;
    learningGoal: Competency;
    lecturesWithLectureUnits: Lecture[] = [];
    formData: CompetencyFormData;
    courseId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private lectureService: LectureService,
        private router: Router,
        private learningGoalService: CompetencyService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        combineLatest([this.activatedRoute.paramMap, this.activatedRoute.parent!.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    const learningGoalId = Number(params.get('competencyId'));
                    this.courseId = Number(parentParams.get('courseId'));

                    const learningGoalObservable = this.learningGoalService.findById(learningGoalId, this.courseId);
                    const learningGoalCourseProgressObservable = this.learningGoalService.getCourseProgress(learningGoalId, this.courseId);
                    const lecturesObservable = this.lectureService.findAllByCourseId(this.courseId, true);
                    return forkJoin([learningGoalObservable, learningGoalCourseProgressObservable, lecturesObservable]);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: ([learningGoalResult, courseProgressResult, lecturesResult]) => {
                    if (learningGoalResult.body) {
                        this.learningGoal = learningGoalResult.body;
                        if (courseProgressResult.body) {
                            this.learningGoal.courseProgress = courseProgressResult.body;
                        }
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
                            } else {
                                // Filter out exercise units, they should be added via the exercise management for now
                                // TODO: User experience improvements for linking learning objects when editing a competency
                                lecture.lectureUnits = lecture.lectureUnits.filter((lectureUnit) => lectureUnit.type !== LectureUnitType.EXERCISE);
                            }
                        }
                    }

                    this.formData = {
                        id: this.learningGoal.id,
                        title: this.learningGoal.title,
                        description: this.learningGoal.description,
                        connectedLectureUnits: this.learningGoal.lectureUnits,
                        taxonomy: this.learningGoal.taxonomy,
                        masteryThreshold: this.learningGoal.masteryThreshold,
                    };
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    updateLearningGoal(formData: CompetencyFormData) {
        const { title, description, taxonomy, masteryThreshold, connectedLectureUnits } = formData;

        this.learningGoal.title = title;
        this.learningGoal.description = description;
        this.learningGoal.taxonomy = taxonomy;
        this.learningGoal.masteryThreshold = masteryThreshold;
        this.learningGoal.lectureUnits = connectedLectureUnits;

        this.isLoading = true;

        this.learningGoalService
            .update(this.learningGoal, this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                    // currently at /course-management/{courseId}/competency-management/{competencyId}/edit, going back to /course-management/{courseId}/competency-management/
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }
}
