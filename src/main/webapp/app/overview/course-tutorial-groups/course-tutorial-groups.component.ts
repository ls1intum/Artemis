import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Subject, finalize } from 'rxjs';
import { BarControlConfiguration } from 'app/shared/tab-bar/tab-bar';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { map, takeUntil } from 'rxjs/operators';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';

type filter = 'all' | 'registered';

@Component({
    selector: 'jhi-course-tutorial-groups',
    templateUrl: './course-tutorial-groups.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseTutorialGroupsComponent implements AfterViewInit, OnInit, OnDestroy {
    ngUnsubscribe = new Subject<void>();

    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };
    tutorialGroups: TutorialGroup[] = [];
    courseId: number;
    course: Course;
    configuration?: TutorialGroupsConfiguration;
    isLoading = false;
    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];

    selectedFilter: filter = 'registered';

    constructor(
        private router: Router,
        private courseStorageService: CourseStorageService,
        private courseManagementService: CourseManagementService,
        private tutorialGroupService: TutorialGroupsService,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private cdr: ChangeDetectorRef,
    ) {}

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    get registeredTutorialGroups() {
        return this.tutorialGroups.filter((tutorialGroup) => tutorialGroup.isUserRegistered);
    }

    ngOnInit(): void {
        this.activatedRoute.parent?.parent?.paramMap
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((parentParams) => {
                this.courseId = Number(parentParams.get('courseId'));
                if (this.courseId) {
                    this.setCourse();
                    this.setTutorialGroups();
                    this.subscribeToCourseUpdates();
                }
            })
            .add(() => this.cdr.detectChanges());
        this.subscribeToQueryParameter();
        this.updateQueryParameters();
    }

    ngAfterViewInit(): void {
        this.renderTopBarControls();
    }

    subscribeToQueryParameter() {
        this.activatedRoute.queryParams
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((queryParams) => {
                if (queryParams.filter) {
                    this.selectedFilter = queryParams.filter as filter;
                }
            })
            .add(() => this.cdr.detectChanges());
    }

    subscribeToCourseUpdates() {
        this.courseStorageService
            .subscribeToCourseUpdates(this.courseId)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((course) => {
                this.course = course;
                this.configuration = course?.tutorialGroupsConfiguration;
                this.setFreeDays();
                this.setTutorialGroups();
            })
            .add(() => this.cdr.detectChanges());
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
        const cachedCourse = this.courseStorageService.getCourse(this.courseId);
        if (cachedCourse === undefined) {
            return false;
        } else {
            this.course = cachedCourse;
            this.configuration = this.course?.tutorialGroupsConfiguration;
            this.setFreeDays();
            return true;
        }
    }

    loadTutorialGroupsFromCache(): boolean {
        const cachedTutorialGroups = this.courseStorageService.getCourse(this.courseId)?.tutorialGroups;
        if (cachedTutorialGroups === undefined) {
            return false;
        } else {
            this.tutorialGroups = cachedTutorialGroups;
            return true;
        }
    }

    updateCachedTutorialGroups() {
        const course = this.courseStorageService.getCourse(this.courseId);
        if (course) {
            course.tutorialGroups = this.tutorialGroups;
            this.courseStorageService.updateCourse(course);
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
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (tutorialGroups: TutorialGroup[]) => {
                    this.tutorialGroups = tutorialGroups ?? [];
                    this.updateCachedTutorialGroups();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            })
            .add(() => this.cdr.detectChanges());
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
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (course: Course) => {
                    this.course = course;
                    this.configuration = this.course?.tutorialGroupsConfiguration;
                    this.setFreeDays();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            })
            .add(() => this.cdr.detectChanges());
    }

    renderTopBarControls() {
        if (this.controls) {
            this.controlConfiguration.subject!.next(this.controls);
        }
    }

    updateQueryParameters() {
        this.router
            .navigate([], {
                relativeTo: this.activatedRoute,
                queryParams: {
                    filter: this.selectedFilter,
                },
                replaceUrl: true,
                queryParamsHandling: 'merge',
            })
            .finally(() => this.cdr.detectChanges());
    }

    onFilterChange(newFilter: filter) {
        this.selectedFilter = newFilter;
        this.updateQueryParameters();
    }
}
