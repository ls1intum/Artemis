import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { Competency, CompetencyJol } from 'app/entities/competency.model';
import { Subscription, forkJoin } from 'rxjs';
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
    judgementOfLearningMap: { [key: number]: CompetencyJol } = {};
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

        this.setCourse(this.courseStorageService.getCourse(this.courseId));

        this.dashboardFeatureToggleActiveSubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.StudentCourseAnalyticsDashboard).subscribe((active) => {
            this.dashboardFeatureActive = active;
        });
    }

    ngOnDestroy(): void {
        this.dashboardFeatureToggleActiveSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
    }

    private setCourse(course?: Course) {
        this.course = course;
        // Note: this component is only shown if there are at least 1 competencies or at least 1 prerequisites, so if they do not exist, we load the data from the server
        if (this.course && ((this.course.competencies && this.course.competencies.length > 0) || (this.course.prerequisites && this.course.prerequisites.length > 0))) {
            this.competencies = this.course.competencies || [];
            this.prerequisites = this.course.prerequisites || [];
            this.judgementOfLearningMap = this.course.judgementOfLearningMap || {};
            this.promptForJolRatingMap = this.course.promptForJolRatingMap || {};
        } else {
            this.loadData();
        }
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
        forkJoin([
            this.competencyService.getAllForCourse(this.courseId),
            this.competencyService.getAllPrerequisitesForCourse(this.courseId),
            this.competencyService.getJoLAllForCourse(this.courseId),
        ]).subscribe({
            next: ([competencies, prerequisites, judgementOfLearningMap]) => {
                this.competencies = competencies.body!;
                this.prerequisites = prerequisites.body!;

                const competenciesMap: { [key: number]: Competency } = Object.fromEntries(this.competencies.map((competency) => [competency.id, competency]));
                this.judgementOfLearningMap = Object.fromEntries(
                    Object.entries(judgementOfLearningMap.body!).filter(([key, value]) => {
                        const progress = competenciesMap[Number(key)]?.userProgress?.first();
                        return value.competencyProgress === (progress?.progress ?? 0) && value.competencyConfidence === (progress?.confidence ?? 0);
                    }),
                );
                this.promptForJolRatingMap = Object.fromEntries(
                    this.competencies.map((competency) => [competency.id, CompetencyJol.shouldPromptForJol(competency, competency.userProgress?.first(), this.competencies)]),
                );

                // Also update the course, so we do not need to fetch again next time
                if (this.course) {
                    this.course.competencies = this.competencies;
                    this.course.prerequisites = this.prerequisites;
                    this.course.judgementOfLearningMap = this.judgementOfLearningMap;
                    this.course.promptForJolRatingMap = this.promptForJolRatingMap;
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
