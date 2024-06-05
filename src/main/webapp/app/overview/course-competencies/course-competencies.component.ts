import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Competency, CompetencyJol } from 'app/entities/competency.model';
import { Observable, Subscription, forkJoin } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-course-competencies',
    templateUrl: './course-competencies.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseCompetenciesComponent implements OnInit, OnDestroy {
    @Input()
    courseId: number;

    isLoading = false;
    course?: Course;
    competencies: Competency[] = [];
    prerequisites: Competency[] = [];
    parentParamSubscription: Subscription;
    judgementOfLearningMap: { [key: number]: { current: CompetencyJol; prior?: CompetencyJol } } = {};
    promptForJolRatingMap: { [key: number]: boolean } = {};

    isCollapsed = true;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

    private dashboardFeatureToggleActiveSubscription: Subscription;
    dashboardFeatureActive = false;

    constructor(
        private featureToggleService: FeatureToggleService,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private courseStorageService: CourseStorageService,
        private competencyService: CompetencyService,
    ) {}

    ngOnInit(): void {
        const courseIdParams$ = this.activatedRoute.parent?.parent?.parent?.params;
        if (courseIdParams$) {
            this.parentParamSubscription = courseIdParams$.subscribe((params) => {
                this.courseId = Number(params.courseId);
            });
        }

        this.course = this.courseStorageService.getCourse(this.courseId);

        this.dashboardFeatureToggleActiveSubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.StudentCourseAnalyticsDashboard).subscribe((active) => {
            this.dashboardFeatureActive = active;
            this.loadData();
        });
    }

    ngOnDestroy(): void {
        this.dashboardFeatureToggleActiveSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
    }

    get countCompetencies() {
        return this.competencies.length;
    }

    get countMasteredCompetencies() {
        return this.competencies.filter((competency) => {
            if (competency.userProgress?.length && competency.masteryThreshold) {
                return competency.userProgress.first()!.progress == 100 && competency.userProgress.first()!.confidence! >= competency.masteryThreshold!;
            }
            return false;
        }).length;
    }

    get countPrerequisites() {
        return this.prerequisites.length;
    }

    get judgementOfLearningEnabled() {
        return (this.course?.studentCourseAnalyticsDashboardEnabled ?? false) && this.dashboardFeatureActive;
    }

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadData() {
        this.isLoading = true;

        const observables = [this.competencyService.getAllForCourse(this.courseId), this.competencyService.getAllPrerequisitesForCourse(this.courseId)] as Observable<
            HttpResponse<Competency[] | { [key: number]: { current: CompetencyJol; prior?: CompetencyJol } }>
        >[];

        if (this.judgementOfLearningEnabled) {
            observables.push(this.competencyService.getJoLAllForCourse(this.courseId));
        }

        forkJoin(observables).subscribe({
            next: ([competencies, prerequisites, judgementOfLearningMap]) => {
                this.competencies = competencies.body! as Competency[];
                this.prerequisites = prerequisites.body! as Competency[];

                if (this.judgementOfLearningEnabled) {
                    const competenciesMap: { [key: number]: Competency } = Object.fromEntries(this.competencies.map((competency) => [competency.id, competency]));
                    this.judgementOfLearningMap = Object.fromEntries(
                        Object.entries((judgementOfLearningMap?.body ?? {}) as { [key: number]: { current: CompetencyJol; prior?: CompetencyJol } }).filter(([key, value]) => {
                            const progress = competenciesMap[Number(key)]?.userProgress?.first();
                            return value.current.competencyProgress === (progress?.progress ?? 0) && value.current.competencyConfidence === (progress?.confidence ?? 0);
                        }),
                    );
                    this.promptForJolRatingMap = Object.fromEntries(
                        this.competencies.map((competency) => [competency.id, CompetencyJol.shouldPromptForJol(competency, competency.userProgress?.first(), this.competencies)]),
                    );
                }

                // Also update the course, so we do not need to fetch again next time
                if (this.course) {
                    this.course.competencies = this.competencies;
                    this.course.prerequisites = this.prerequisites;
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    /**
     * Calculates a unique identity for each competency card shown in the component
     * @param index The index in the list
     * @param competency The competency of the current iteration
     */
    identify(index: number, competency: Competency) {
        return `${index}-${competency.id}`;
    }
}
