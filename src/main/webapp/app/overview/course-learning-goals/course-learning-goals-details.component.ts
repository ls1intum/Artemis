import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { faPencilAlt } from '@fortawesome/free-solid-svg-icons';

import { AlertService } from 'app/core/util/alert.service';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal, LearningGoalProgress, getIcon, getIconTooltip } from 'app/entities/learningGoal.model';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-course-learning-goals-details',
    templateUrl: './course-learning-goals-details.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseLearningGoalsDetailsComponent implements OnInit {
    learningGoalId?: number;
    courseId?: number;
    isLoading = false;
    learningGoal: LearningGoal;
    showFireworks = false;

    readonly LectureUnitType = LectureUnitType;

    faPencilAlt = faPencilAlt;
    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    constructor(
        private alertService: AlertService,
        private activatedRoute: ActivatedRoute,
        private learningGoalService: LearningGoalService,
        private lectureUnitService: LectureUnitService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.learningGoalId = +params['learningGoalId'];
            this.courseId = +params['courseId'];
            if (this.learningGoalId && this.courseId) {
                this.loadData();
            }
        });
    }

    private loadData() {
        this.isLoading = true;
        this.learningGoalService.findById(this.learningGoalId!, this.courseId!).subscribe({
            next: (resp) => {
                this.learningGoal = resp.body!;
                if (this.learningGoal && this.learningGoal.exercises) {
                    // Add exercises as lecture units for display
                    this.learningGoal.lectureUnits = this.learningGoal.lectureUnits ?? [];
                    this.learningGoal.lectureUnits.push(
                        ...this.learningGoal.exercises.map((exercise) => {
                            const exerciseUnit = new ExerciseUnit();
                            exerciseUnit.id = exercise.id;
                            exerciseUnit.exercise = exercise;
                            return exerciseUnit as LectureUnit;
                        }),
                    );
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    showFireworksIfMastered() {
        if (this.mastery >= 100 && !this.showFireworks) {
            setTimeout(() => (this.showFireworks = true), 1000);
            setTimeout(() => (this.showFireworks = false), 6000);
        }
    }

    getUserProgress(): LearningGoalProgress {
        if (this.learningGoal.userProgress?.length) {
            return this.learningGoal.userProgress.first()!;
        }
        return { progress: 0, confidence: 0 } as LearningGoalProgress;
    }

    get progress(): number {
        // The percentage of completed lecture units and participated exercises
        return Math.round(this.getUserProgress().progress ?? 0);
    }

    get confidence(): number {
        // Confidence level (average score in exercises) in proportion to the threshold value (max. 100 %)
        // Example: If the student’s latest confidence level equals 60 % and the mastery threshold is set to 80 %, the ring would be 75 % full.
        return Math.min(Math.round(((this.getUserProgress().confidence ?? 0) / (this.learningGoal.masteryThreshold ?? 100)) * 100), 100);
    }

    get mastery(): number {
        // Advancement towards mastery as a weighted function of progress and confidence
        const weight = 2 / 3;
        return Math.round((1 - weight) * this.progress + weight * this.confidence);
    }

    get isMastered(): boolean {
        return this.mastery >= 100;
    }

    completeLectureUnit(event: LectureUnitCompletionEvent): void {
        if (!event.lectureUnit.lecture || !event.lectureUnit.visibleToStudents || event.lectureUnit.completed === event.completed) {
            return;
        }

        this.lectureUnitService.setCompletion(event.lectureUnit.id!, event.lectureUnit.lecture!.id!, event.completed).subscribe({
            next: () => {
                event.lectureUnit.completed = event.completed;

                this.learningGoalService.getProgress(this.learningGoalId!, this.courseId!, true).subscribe({
                    next: (resp) => {
                        this.learningGoal.userProgress = [resp.body!];
                        this.showFireworksIfMastered();
                    },
                });
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }
}
