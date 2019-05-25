import { JhiAlertService } from 'ng-jhipster';
import { Component, OnInit } from '@angular/core';
import { Course, CourseService, StatsForInstructorDashboard } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { InitializationState, Participation } from 'app/entities/participation';
import { getIcon, getIconTooltip } from 'app/entities/exercise';
import { ResultService } from 'app/entities/result';
import { TutorLeaderboardData } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './instructor-course-dashboard.component.html',
    providers: [JhiAlertService],
})
export class InstructorCourseDashboardComponent implements OnInit {
    course: Course;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    loading = true;

    stats: StatsForInstructorDashboard = {
        numberOfStudents: 0,
        numberOfSubmissions: 0,
        numberOfTutors: 0,
        numberOfAssessments: 0,
        numberOfComplaints: 0,
        numberOfOpenComplaints: 0,

        tutorLeaderboard: [],
    };
    dataForAssessmentPieChart: number[];

    tutorLeaderboardData: TutorLeaderboardData = {};

    readonly MIN_POINTS_GREEN = 100;
    readonly MIN_POINTS_ORANGE = 50;

    constructor(private courseService: CourseService, private resultService: ResultService, private route: ActivatedRoute, private jhiAlertService: JhiAlertService) {}

    ngOnInit(): void {
        this.loading = true;
        this.loadCourse(Number(this.route.snapshot.paramMap.get('courseId')));
    }

    private loadCourse(courseId: number) {
        this.courseService
            .findWithExercisesAndParticipations(courseId)
            .subscribe((res: HttpResponse<Course>) => (this.course = res.body), (response: HttpErrorResponse) => this.onError(response.message));

        this.courseService.getStatsForInstructors(courseId).subscribe(
            (res: HttpResponse<StatsForInstructorDashboard>) => {
                this.stats = Object.assign({}, this.stats, res.body);

                for (const tutor of this.stats.tutorLeaderboard) {
                    this.tutorLeaderboardData[tutor.login] = tutor;
                }

                this.dataForAssessmentPieChart = [this.stats.numberOfSubmissions - this.stats.numberOfAssessments, this.stats.numberOfAssessments];
            },
            (response: string) => this.onError(response),
            () => (this.loading = false),
        );
    }

    calculatePercentage(numerator: number, denominator: number): number {
        if (denominator === 0) {
            return 0;
        }

        return Math.round((numerator / denominator) * 100);
    }

    calculateClass(numberOfAssessments: number, length: number): string {
        const percentage = this.calculatePercentage(numberOfAssessments, length);

        if (percentage < this.MIN_POINTS_ORANGE) {
            return 'bg-danger';
        } else if (percentage < this.MIN_POINTS_GREEN) {
            return 'bg-warning';
        }

        return 'bg-success';
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }
}
