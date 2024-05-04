import { Component, OnDestroy, OnInit } from '@angular/core';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Subscription, forkJoin } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Competency } from 'app/entities/competency.model';
import { onError } from 'app/shared/util/global.utils';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-course-dashboard',
    templateUrl: './course-dashboard.component.html',
})
export class CourseDashboardComponent implements OnInit, OnDestroy {
    courseId: number;
    exerciseId: number;
    isLoading = false;

    public competencies: Competency[] = [];
    private prerequisites: Competency[] = [];

    private paramSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private courseExercises: Exercise[] = [];
    public course?: Course;
    public data: any;

    constructor(
        private courseStorageService: CourseStorageService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private competencyService: CompetencyService,
    ) {}

    ngOnInit(): void {
        this.paramSubscription = this.route.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });
        this.setCourse(this.courseStorageService.getCourse(this.courseId));

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.setCourse(course);
        });
    }

    private onCourseLoad(): void {
        if (this.course?.exercises) {
            this.courseExercises = this.course.exercises;
        }
    }
    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
    }

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadCompetencies() {
        this.isLoading = true;
        forkJoin([this.competencyService.getAllForCourse(this.courseId), this.competencyService.getAllPrerequisitesForCourse(this.courseId)]).subscribe({
            next: ([competencies, prerequisites]) => {
                this.competencies = competencies.body!;
                this.prerequisites = prerequisites.body!;
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

    private setCourse(course?: Course) {
        this.course = course;
        this.onCourseLoad();
        // Note: this component is only shown if there are at least 1 competency or at least 1 prerequisite, so if they do not exist, we load the data from the server
        if (this.course && ((this.course.competencies && this.course.competencies.length > 0) || (this.course.prerequisites && this.course.prerequisites.length > 0))) {
            this.competencies = this.course.competencies || [];
            this.prerequisites = this.course.prerequisites || [];
        } else {
            this.loadCompetencies();
        }
    }

    get competency() {
        return this.competencies[0]!;
    }

    protected readonly FeatureToggle = FeatureToggle;
}
