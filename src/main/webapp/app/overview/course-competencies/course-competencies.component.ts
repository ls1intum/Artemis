import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { Competency } from 'app/entities/competency.model';
import { Subscription, forkJoin } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

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

    isCollapsed = true;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

    constructor(
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private courseStorageService: CourseStorageService,
        private competencyService: CompetencyService,
    ) {}

    ngOnInit(): void {
        const courseIdParams$ = this.activatedRoute.parent?.parent?.parent?.params;
        if (courseIdParams$) {
            this.parentParamSubscription = courseIdParams$.subscribe((params) => {
                this.courseId = parseInt(params.courseId, 10);
            });
        }

        this.setCourse(this.courseStorageService.getCourse(this.courseId));
    }

    private setCourse(course?: Course) {
        this.course = course;
        // Note: this component is only shown if there are at least 1 competencies or at least 1 prerequisites, so if they do not exist, we load the data from the server
        if (this.course && ((this.course.competencies && this.course.competencies.length > 0) || (this.course.prerequisites && this.course.prerequisites.length > 0))) {
            this.competencies = this.course.competencies || [];
            this.prerequisites = this.course.prerequisites || [];
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

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadData() {
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

    /**
     * Calculates a unique identity for each competency card shown in the component
     * @param index The index in the list
     * @param competency The competency of the current iteration
     */
    identify(index: number, competency: Competency) {
        return `${index}-${competency.id}`;
    }

    ngOnDestroy(): void {
        this.parentParamSubscription?.unsubscribe();
    }
}
