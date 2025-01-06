import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { CourseManagementStatisticsDTO } from './course-management-statistics-dto';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { Course, isCommunicationEnabled } from 'app/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-management-statistics',
    templateUrl: './course-management-statistics.component.html',
    styleUrls: ['./course-management-statistics.component.scss'],
    imports: [TranslateDirective, DocumentationButtonComponent, StatisticsAverageScoreGraphComponent, StatisticsGraphComponent, ArtemisTranslatePipe],
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
