import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Competency, CompetencyProgress, getIcon, getIconTooltip } from 'app/entities/competency.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { faPencilAlt } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-competencies-details',
    templateUrl: './course-competencies-details.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseCompetenciesDetailsComponent implements OnInit {
    competencyId?: number;
    courseId?: number;
    isLoading = false;
    competency: Competency;
    showFireworks = false;

    readonly LectureUnitType = LectureUnitType;

    faPencilAlt = faPencilAlt;
    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    constructor(
        private alertService: AlertService,
        private activatedRoute: ActivatedRoute,
        private competencyService: CompetencyService,
        private lectureUnitService: LectureUnitService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.competencyId = +params['competencyId'];
            this.courseId = +params['courseId'];
            if (this.competencyId && this.courseId) {
                this.loadData();
            }
        });
    }

    private loadData() {
        this.isLoading = true;
        this.competencyService.findById(this.competencyId!, this.courseId!).subscribe({
            next: (resp) => {
                this.competency = resp.body!;
                if (this.competency && this.competency.exercises) {
                    // Add exercises as lecture units for display
                    this.competency.lectureUnits = this.competency.lectureUnits ?? [];
                    this.competency.lectureUnits.push(
                        ...this.competency.exercises.map((exercise) => {
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

    getUserProgress(): CompetencyProgress {
        if (this.competency.userProgress?.length) {
            return this.competency.userProgress.first()!;
        }
        return { progress: 0, confidence: 0 } as CompetencyProgress;
    }

    get progress(): number {
        // The percentage of completed lecture units and participated exercises
        return Math.round(this.getUserProgress().progress ?? 0);
    }

    get confidence(): number {
        // Confidence level (average score in exercises) in proportion to the threshold value (max. 100 %)
        // Example: If the student’s latest confidence level equals 60 % and the mastery threshold is set to 80 %, the ring would be 75 % full.
        return Math.min(Math.round(((this.getUserProgress().confidence ?? 0) / (this.competency.masteryThreshold ?? 100)) * 100), 100);
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

                this.competencyService.getProgress(this.competencyId!, this.courseId!, true).subscribe({
                    next: (resp) => {
                        this.competency.userProgress = [resp.body!];
                        this.showFireworksIfMastered();
                    },
                });
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }
}
