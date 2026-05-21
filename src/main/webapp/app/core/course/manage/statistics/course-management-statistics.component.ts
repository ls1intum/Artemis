import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/exercise/shared/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { CourseManagementStatisticsDTO } from '../../shared/entities/course-management-statistics-dto';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { Course, isCommunicationEnabled } from 'app/core/course/shared/entities/course.model';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/average-score-graph/statistics-average-score-graph.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarTitleComponent } from 'app/core/course/shared/course-title-bar-title/course-title-bar-title.component';

@Component({
    selector: 'jhi-course-management-statistics',
    templateUrl: './course-management-statistics.component.html',
    styleUrls: ['./course-management-statistics.component.scss'],
    imports: [
        DocumentationButtonComponent,
        StatisticsAverageScoreGraphComponent,
        StatisticsGraphComponent,
        ArtemisTranslatePipe,
        CourseTitleBarTitleDirective,
        CourseTitleBarTitleComponent,
    ],
})
export class CourseManagementStatisticsComponent implements OnDestroy {
    private service = inject(StatisticsService);
    private route = inject(ActivatedRoute);

    readonly documentationType: DocumentationType = 'Statistics';
    // html properties
    readonly SpanType = SpanType;
    readonly graph = Graphs;

    readonly currentSpan = signal<SpanType>(SpanType.WEEK);
    readonly statisticsView = signal<StatisticsView>(StatisticsView.COURSE);

    private paramSub: Subscription;
    private dataSub: Subscription;
    private statisticsSub: Subscription;

    readonly courseId = signal<number | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);

    readonly defaultTitle = 'Course';

    // Average Score
    readonly selectedValueAverageScore = signal<string | undefined>(undefined);
    readonly currentAverageScore = signal<number>(0);
    readonly currentAbsolutePoints = signal<number>(0);
    readonly currentMaxPoints = signal<number>(1);
    readonly exerciseTitles = signal<string[] | undefined>(undefined);

    // Average Rating
    readonly selectedValueAverageRating = signal<string | undefined>(undefined);
    readonly currentAverageRating = signal<number>(0);
    readonly currentAverageRatingInPercent = signal<number>(0);
    readonly tutorNames = signal<string[] | undefined>(undefined);

    readonly courseStatistics = signal<CourseManagementStatisticsDTO | undefined>(undefined);

    readonly graphTypes = computed<Graphs[]>(() => {
        const currentCourse = this.course();
        if (!currentCourse) {
            return [];
        }
        return [
            Graphs.SUBMISSIONS,
            Graphs.ACTIVE_USERS,
            Graphs.RELEASED_EXERCISES,
            Graphs.EXERCISES_DUE,
            Graphs.ACTIVE_TUTORS,
            Graphs.CREATED_RESULTS,
            Graphs.CREATED_FEEDBACKS,
            isCommunicationEnabled(currentCourse) && Graphs.POSTS,
            isCommunicationEnabled(currentCourse) && Graphs.RESOLVED_POSTS,
            Graphs.CONDUCTED_EXAMS,
            Graphs.EXAM_PARTICIPATIONS,
            Graphs.EXAM_REGISTRATIONS,
        ].filter(Boolean) as Graphs[];
    });

    constructor() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId.set(params['courseId']);
        });
        this.dataSub = this.route.data.subscribe(({ course }) => {
            this.course.set(course);
        });

        // Effect to fetch statistics when courseId changes
        effect(() => {
            const id = this.courseId();
            if (id) {
                this.statisticsSub?.unsubscribe();
                this.statisticsSub = this.service.getCourseStatistics(id).subscribe((res: CourseManagementStatisticsDTO) => {
                    this.courseStatistics.set(res);
                });
            }
        });
    }

    ngOnDestroy(): void {
        this.paramSub?.unsubscribe();
        this.dataSub?.unsubscribe();
        this.statisticsSub?.unsubscribe();
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan.set(span);
    }
}
