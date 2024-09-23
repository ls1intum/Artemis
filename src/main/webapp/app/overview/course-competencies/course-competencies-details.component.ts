import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Competency, CompetencyJol, CompetencyProgress, ConfidenceReason, getConfidence, getIcon, getMastery, getProgress } from 'app/entities/competency.model';
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
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

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
    competencyProgress: CompetencyProgress;
    judgementOfLearning: CompetencyJol | undefined;
    promptForJolRating = false;
    showFireworks = false;
    dashboardFeatureActive = false;
    paramsSubscription: Subscription;

    readonly LectureUnitType = LectureUnitType;
    protected readonly ConfidenceReason = ConfidenceReason;

    faPencilAlt = faPencilAlt;
    getIcon = getIcon;

    constructor(
        private featureToggleService: FeatureToggleService,
        private courseStorageService: CourseStorageService,
        private alertService: AlertService,
        private activatedRoute: ActivatedRoute,
        private courseCompetencyService: CourseCompetencyService,
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

        const observables = [this.courseCompetencyService.findById(this.competencyId!, this.courseId!)] as Observable<
            HttpResponse<Competency | Competency[] | { current: CompetencyJol; prior?: CompetencyJol }>
        >[];

        if (this.judgementOfLearningEnabled) {
            observables.push(this.courseCompetencyService.getAllForCourse(this.courseId!));
            observables.push(this.courseCompetencyService.getJoL(this.courseId!, this.competencyId!));
        }

        forkJoin(observables).subscribe({
            next: ([competencyResp, courseCompetenciesResp, judgementOfLearningResp]) => {
                this.competency = competencyResp.body! as Competency;
                this.competencyProgress = this.getUserProgress();

                if (this.judgementOfLearningEnabled) {
                    const competencies = courseCompetenciesResp.body! as Competency[];
                    const progress = this.competency.userProgress?.first();
                    this.promptForJolRating = CompetencyJol.shouldPromptForJol(this.competency, progress, competencies);
                    const judgementOfLearning = (judgementOfLearningResp?.body ?? undefined) as { current: CompetencyJol; prior?: CompetencyJol } | undefined;
                    if (
                        !judgementOfLearning?.current ||
                        judgementOfLearning.current.competencyProgress !== (progress?.progress ?? 0) ||
                        judgementOfLearning.current.competencyConfidence !== (progress?.confidence ?? 1)
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
        return { progress: 0, confidence: 1 } as CompetencyProgress;
    }

    get progress(): number {
        return getProgress(this.competencyProgress);
    }

    get confidence(): number {
        return getConfidence(this.competencyProgress);
    }

    get mastery(): number {
        return getMastery(this.competencyProgress);
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

                this.courseCompetencyService.getProgress(this.competencyId!, this.courseId!, true).subscribe({
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
}
