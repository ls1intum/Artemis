import { Component, OnInit, inject } from '@angular/core';
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
export class CourseManagementStatisticsComponent implements OnInit {
    private service = inject(StatisticsService);
    private route = inject(ActivatedRoute);

    readonly documentationType: DocumentationType = 'Statistics';
    // html properties
    SpanType = SpanType;
    graph = Graphs;
    graphTypes: Graphs[];
    currentSpan: SpanType = SpanType.WEEK;
    statisticsView: StatisticsView = StatisticsView.COURSE;
    paramSub: Subscription;
    courseId: number;
    course: Course;

    defaultTitle = 'Course';
    // Average Score
    selectedValueAverageScore: string;
    currentAverageScore = 0;
    currentAbsolutePoints = 0;
    currentMaxPoints = 1;
    exerciseTitles: string[];

    // Average Rating
    selectedValueAverageRating: string;
    currentAverageRating = 0;
    currentAverageRatingInPercent = 0;
    tutorNames: string[];

    courseStatistics: CourseManagementStatisticsDTO;

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
        });
        this.route.data.subscribe(({ course }) => {
            this.course = course;
            this.initializeGraphTypes();
        });
        this.service.getCourseStatistics(this.courseId).subscribe((res: CourseManagementStatisticsDTO) => {
            this.courseStatistics = res;
        });
    }

    initializeGraphTypes(): void {
        this.graphTypes = [
            Graphs.SUBMISSIONS,
            Graphs.ACTIVE_USERS,
            Graphs.RELEASED_EXERCISES,
            Graphs.EXERCISES_DUE,
            Graphs.ACTIVE_TUTORS,
            Graphs.CREATED_RESULTS,
            Graphs.CREATED_FEEDBACKS,
            isCommunicationEnabled(this.course) && Graphs.POSTS,
            isCommunicationEnabled(this.course) && Graphs.RESOLVED_POSTS,
            Graphs.CONDUCTED_EXAMS,
            Graphs.EXAM_PARTICIPATIONS,
            Graphs.EXAM_REGISTRATIONS,
        ].filter(Boolean) as Graphs[];
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
