import { AfterViewInit, Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Subject, finalize } from 'rxjs';
import { BarControlConfiguration } from 'app/overview/tab-bar/tab-bar';
import { Course } from 'app/entities/course.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { map } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';

type filter = 'all' | 'registered';

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
})
export class CourseTutorialGroupsComponent implements AfterViewInit, OnInit {
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
        useIndentation: true,
    };
    tutorialGroups: TutorialGroup[] = [];
    courseId: number;
    course: Course;
    isLoading = false;
    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];

    selectedFilter: filter = 'registered';

    constructor(
        private router: Router,
        private courseCalculationService: CourseScoreCalculationService,
        private courseManagementService: CourseManagementService,
        private tutorialGroupService: TutorialGroupsService,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
    ) {}

    get registeredTutorialGroups() {
        return this.tutorialGroups.filter((tutorialGroup) => tutorialGroup.isUserRegistered);
    }

    ngOnInit(): void {
        this.activatedRoute.parent?.parent?.paramMap.subscribe((parentParams) => {
            this.courseId = Number(parentParams.get('courseId'));
            if (this.courseId) {
                this.setCourse();
                this.setTutorialGroups();
                this.subscribeToCourseUpdates();
            }
        });
        this.subscribeToQueryParameter();
        this.updateQueryParameters();
    }

    ngAfterViewInit(): void {
        this.renderTopBarControls();
    }

    subscribeToQueryParameter() {
        this.activatedRoute.queryParams.subscribe((queryParams) => {
            if (queryParams.filter) {
                this.selectedFilter = queryParams.filter as filter;
            }
        });
    }

    subscribeToCourseUpdates() {
        this.courseManagementService.getCourseUpdates(this.courseId).subscribe((course) => {
            this.course = course;
            this.setFreeDays();
            this.setTutorialGroups();
        });
    }

    private setFreeDays() {
        if (this.course?.tutorialGroupsConfiguration?.tutorialGroupFreePeriods) {
            this.tutorialGroupFreeDays = this.course.tutorialGroupsConfiguration.tutorialGroupFreePeriods;
        } else {
            this.tutorialGroupFreeDays = [];
        }
    }

    setTutorialGroups() {
        const tutorialGroupsLoadedFromCache = this.loadTutorialGroupsFromCache();
        if (!tutorialGroupsLoadedFromCache) {
            this.loadTutorialGroupsFromServer();
        }
    }

    setCourse() {
        const courseLoadedFromCache = this.loadCourseFromCache();
        if (!courseLoadedFromCache) {
            this.loadCourseFromServer();
        }
    }

    loadCourseFromCache() {
        const cachedCourse = this.courseCalculationService.getCourse(this.courseId);
        if (cachedCourse === undefined) {
            return false;
        } else {
            this.course = cachedCourse;
            this.setFreeDays();
            return true;
        }
    }

    loadTutorialGroupsFromCache(): boolean {
        const cachedTutorialGroups = this.courseCalculationService.getCourse(this.courseId)?.tutorialGroups;
        if (cachedTutorialGroups === undefined) {
            return false;
        } else {
            this.tutorialGroups = cachedTutorialGroups;
            return true;
        }
    }

    updateCachedTutorialGroups() {
        const course = this.courseCalculationService.getCourse(this.courseId);
        if (course) {
            course.tutorialGroups = this.tutorialGroups;
            this.courseCalculationService.updateCourse(course);
        }
    }

    loadTutorialGroupsFromServer() {
        this.isLoading = true;
        this.tutorialGroupService
            .getAllForCourse(this.courseId)
            .pipe(
                map((res: HttpResponse<TutorialGroup[]>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (tutorialGroups: TutorialGroup[]) => {
                    this.tutorialGroups = tutorialGroups ?? [];
                    this.updateCachedTutorialGroups();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    loadCourseFromServer() {
        this.isLoading = true;
        this.courseManagementService
            .find(this.courseId)
            .pipe(
                map((res: HttpResponse<Course>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (course: Course) => {
                    this.course = course;
                    this.setFreeDays();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    renderTopBarControls() {
        if (this.controls) {
            this.controlConfiguration.subject!.next(this.controls);
        }
    }

    updateQueryParameters() {
        this.router.navigate([], {
            relativeTo: this.activatedRoute,
            queryParams: {
                filter: this.selectedFilter,
            },
            replaceUrl: true,
            queryParamsHandling: 'merge',
        });
    }

    onFilterChange(newFilter: filter) {
        this.selectedFilter = newFilter;
        this.updateQueryParameters();
    }
}
