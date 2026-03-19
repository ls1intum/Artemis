import { Component, OnDestroy, OnInit, computed, inject, input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { Competency, CourseCompetencyType, compareSoftDueDate, getMastery } from 'app/atlas/shared/entities/competency.model';
import { Subscription } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { CompetencyCardComponent } from 'app/atlas/overview/competency-card/competency-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ScienceEventType } from 'app/shared/science/science.model';
import { ScienceService } from 'app/shared/science/science.service';

@Component({
    selector: 'jhi-course-competencies',
    templateUrl: './course-competencies.component.html',
    styleUrls: ['../../../core/course/overview/course-overview/course-overview.scss'],
    imports: [CompetencyCardComponent, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class CourseCompetenciesComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private courseStorageService = inject(CourseStorageService);
    private courseCompetencyService = inject(CourseCompetencyService);
    private readonly scienceService = inject(ScienceService);

    courseId = input<number>();
    private _resolvedCourseId?: number;
    resolvedCourseId = computed(() => this.courseId() ?? this._resolvedCourseId!);

    isLoading = false;
    course?: Course;
    competencies: Competency[] = [];
    prerequisites: Competency[] = [];
    parentParamSubscription: Subscription;

    isCollapsed = true;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;

    ngOnInit(): void {
        const courseIdParams$ = this.activatedRoute.parent?.parent?.params;
        if (courseIdParams$) {
            this.parentParamSubscription = courseIdParams$.subscribe((params) => {
                this.scienceService.logEvent(ScienceEventType.COMPETENCY__OPEN_OVERVIEW, Number(params.courseId));
            });
        }

        // Resolve courseId from input or route params
        this._resolvedCourseId = this.courseId() ?? Number(this.activatedRoute.parent?.parent?.snapshot.paramMap.get('courseId'));
        if (!this._resolvedCourseId || isNaN(this._resolvedCourseId)) {
            this.alertService.error('artemisApp.error.invalidCourseId');
            return;
        }
        this.course = this.courseStorageService.getCourse(this.resolvedCourseId());

        this.loadData();
    }

    ngOnDestroy(): void {
        this.parentParamSubscription?.unsubscribe();
    }

    get countCompetencies() {
        return this.competencies.length;
    }

    get countMasteredCompetencies() {
        return this.competencies.filter((competency) => getMastery(competency.userProgress?.first()) >= (competency.masteryThreshold ?? 100)).length;
    }

    get countPrerequisites() {
        return this.prerequisites.length;
    }

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadData() {
        this.isLoading = true;

        this.courseCompetencyService.getAllForCourse(this.resolvedCourseId(), false).subscribe({
            next: (courseCompetencies) => {
                const courseCompetenciesResponse = courseCompetencies.body ?? [];
                this.competencies = courseCompetenciesResponse.filter((competency) => competency.type === CourseCompetencyType.COMPETENCY).sort(compareSoftDueDate);
                this.prerequisites = courseCompetenciesResponse.filter((competency) => competency.type === CourseCompetencyType.PREREQUISITE);

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
