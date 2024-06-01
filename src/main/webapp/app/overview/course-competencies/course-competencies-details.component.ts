import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Competency, CompetencyJol, CompetencyProgress, getIcon } from 'app/entities/competency.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import { Observable, Subscription, combineLatest, forkJoin } from 'rxjs';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-competencies-details',
    templateUrl: './course-competencies-details.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseCompetenciesDetailsComponent implements OnInit, OnDestroy {
    competencyId?: number;
    course?: Course;
    courseId?: number;
    isLoading = false;
    competency: Competency;
    judgementOfLearning: CompetencyJol | undefined;
    promptForJolRating = false;
    showFireworks = false;
    dashboardFeatureActive = false;
    paramsSubscription: Subscription;

    readonly LectureUnitType = LectureUnitType;

    faPencilAlt = faPencilAlt;
    getIcon = getIcon;

    constructor(
        private featureToggleService: FeatureToggleService,
        private courseStorageService: CourseStorageService,
        private alertService: AlertService,
        private activatedRoute: ActivatedRoute,
        private competencyService: CompetencyService,
        private lectureUnitService: LectureUnitService,
    ) {}

    ngOnInit(): void {
        // example route looks like: /courses/1/competencies/10
        const courseIdParams$ = this.activatedRoute.parent?.parent?.parent?.params;
        const competencyIdParams$ = this.activatedRoute.params;
        const dashboardFeatureToggleActive$ = this.featureToggleService.getFeatureToggleActive(FeatureToggle.StudentCourseAnalyticsDashboard);

        if (courseIdParams$) {
            this.paramsSubscription = combineLatest([courseIdParams$, competencyIdParams$, dashboardFeatureToggleActive$]).subscribe(
                ([courseIdParams, competencyIdParams, dashboardFeatureActive]) => {
                    this.competencyId = Number(competencyIdParams.competencyId);
                    this.courseId = Number(courseIdParams.courseId);
                    this.dashboardFeatureActive = dashboardFeatureActive;
                    this.course = this.courseStorageService.getCourse(this.courseId);
                    if (this.competencyId && this.courseId) {
                        this.loadData();
                    }
                },
            );
        }
    }

    ngOnDestroy(): void {
        this.paramsSubscription?.unsubscribe();
    }

    private loadData() {
        this.isLoading = true;

        const observables = [this.competencyService.findById(this.competencyId!, this.courseId!)] as Observable<
            HttpResponse<Competency | Competency[] | { current: CompetencyJol; prior?: CompetencyJol }>
        >[];

        if (this.judgementOfLearningEnabled) {
            observables.push(this.competencyService.getAllForCourse(this.courseId!));
            observables.push(this.competencyService.getJoL(this.courseId!, this.competencyId!));
        }

        forkJoin(observables).subscribe({
            next: ([competencyResp, courseCompetenciesResp, judgementOfLearningResp]) => {
                this.competency = competencyResp.body! as Competency;

                if (this.judgementOfLearningEnabled) {
                    const competencies = courseCompetenciesResp.body! as Competency[];
                    const progress = this.competency.userProgress?.first();
                    this.promptForJolRating = CompetencyJol.shouldPromptForJol(this.competency, progress, competencies);
                    const judgementOfLearning = (judgementOfLearningResp?.body ?? undefined) as { current: CompetencyJol; prior?: CompetencyJol } | undefined;
                    if (
                        judgementOfLearning &&
                        progress &&
                        (judgementOfLearning.current.competencyProgress !== (progress?.progress ?? 0) ||
                            judgementOfLearning.current.competencyConfidence !== (progress?.confidence ?? 0))
                    ) {
                        this.judgementOfLearning = undefined;
                    } else {
                        this.judgementOfLearning = judgementOfLearning?.current;
                    }
                }

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

    get softDueDatePassed(): boolean {
        return dayjs().isAfter(this.competency.softDueDate);
    }

    get judgementOfLearningEnabled() {
        return (this.course?.studentCourseAnalyticsDashboardEnabled ?? false) && this.dashboardFeatureActive;
    }

    onRatingChange(newRating: number) {
        this.judgementOfLearning = {
            competencyId: this.competencyId!,
            jolValue: newRating,
            judgementTime: dayjs().toString(),
            competencyProgress: this.progress,
            competencyConfidence: this.confidence,
        } satisfies CompetencyJol;
    }
}
