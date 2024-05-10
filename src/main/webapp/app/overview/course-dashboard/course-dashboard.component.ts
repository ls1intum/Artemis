import { Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Competency } from 'app/entities/competency.model';
import { onError } from 'app/shared/util/global.utils';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ICompetencyAccordionToggleEvent } from 'app/shared/competency/interfaces/competency-accordion-toggle-event.interface';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-course-dashboard',
    templateUrl: './course-dashboard.component.html',
    styleUrls: ['./course-dashboard.component.scss'],
})
export class CourseDashboardComponent implements OnInit, OnDestroy {
    courseId: number;
    exerciseId: number;
    isLoading = false;

    public competencies: Competency[] = [];
    public openedAccordionIndex: number | null = null;
    private subscriptions: Subscription[] = [];

    private paramSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;

    public course?: Course;
    public data: any;

    @ViewChildren('competencyAccordionElement', { read: ElementRef }) competencyAccordions: QueryList<ElementRef>;

    constructor(
        private courseStorageService: CourseStorageService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private router: Router,
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

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
        this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    }

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadCompetencies() {
        this.isLoading = true;
        this.subscriptions.push(
            this.competencyService.getAllForCourseStudentDashboard(this.courseId).subscribe({
                next: (response) => {
                    this.competencies = response.body!;
                    this.isLoading = false;
                    // scroll to the current competency
                    const scrollIndex = this.currentCompetencyIndex !== -1 ? this.currentCompetencyIndex : 0;
                    setTimeout(() => {
                        this.scrollToAccordion(scrollIndex);
                    }, 0);
                },
                error: (error: HttpErrorResponse) => {
                    onError(this.alertService, error);
                    this.isLoading = false;
                },
            }),
        );
    }

    get currentCompetencyIndex() {
        return this.competencies.findIndex((competency) => dayjs().isBefore(competency.softDueDate));
    }

    private scrollToAccordion(index: number) {
        const accordionsArray = this.competencyAccordions.toArray();
        if (index !== -1 && accordionsArray[index]) {
            accordionsArray[index].nativeElement.scrollIntoView({ behavior: 'smooth' });
        }
    }

    private setCourse(course?: Course) {
        this.course = course;
        // Note: this component is only shown if there is at least 1 competency or at least 1 prerequisite, so if they do not exist, we load the data from the server
        if (this.course && ((this.course.competencies && this.course.competencies.length > 0) || (this.course.prerequisites && this.course.prerequisites.length > 0))) {
            this.competencies = this.course.competencies || [];
        } else {
            this.loadCompetencies();
        }
    }
    handleToggle(event: ICompetencyAccordionToggleEvent) {
        this.openedAccordionIndex = event.opened ? event.index : null;
    }
    get learningPathsEnabled() {
        return this.course?.learningPathsEnabled || false;
    }

    protected readonly FeatureToggle = FeatureToggle;

    navigateToLearningPaths() {
        this.router.navigate(['courses', this.courseId, 'learning-path']);
    }
}
