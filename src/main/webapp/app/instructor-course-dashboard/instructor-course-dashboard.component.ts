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
    providers: [JhiAlertService]
})
export class InstructorCourseDashboardComponent implements OnInit {
    course: Course;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    stats: StatsForInstructorDashboard;
    dataForAssessmentPieChart: number[];

    tutorLeaderboardData: TutorLeaderboardData = {};

    readonly MIN_POINTS_GREEN = 100;
    readonly MIN_POINTS_ORANGE = 50;

    constructor(
        private courseService: CourseService,
        private resultService: ResultService,
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService
    ) {}

    ngOnInit(): void {
        this.loadCourse(Number(this.route.snapshot.paramMap.get('courseId')));
    }

    private loadCourse(courseId: number) {
        this.courseService.findWithExercisesAndParticipations(courseId).subscribe(
            (res: HttpResponse<Course>) => {
                this.course = res.body;

                let numberOfSubmissions = 0;
                let numberOfAssessments = 0;

                for (const exercise of this.course.exercises) {
                    const validParticipations: Participation[] = exercise.participations.filter(participation => participation.initializationState === InitializationState.FINISHED);
                    for (const participation of validParticipations) {
                        for (const result of participation.results) {
                            if (result.rated) {
                                const tutorId = result.assessor.id;
                                if (!this.tutorLeaderboardData[tutorId]) {
                                    this.tutorLeaderboardData[tutorId] = {
                                        tutor: result.assessor,
                                        numberOfAssessments: 0
                                    };
                                }

                                this.tutorLeaderboardData[tutorId].numberOfAssessments++;
                            }
                        }
                    }

                    exercise.participations = exercise.participations.filter(participation => participation.initializationState === InitializationState.FINISHED);
                    exercise.numberOfAssessments = exercise.participations.filter(participation => participation.results.filter(result => result.rated).length > 0).length;

                    numberOfAssessments += exercise.numberOfAssessments;
                    numberOfSubmissions += exercise.participations.length;
                }

                this.stats.numberOfAssessments = numberOfAssessments;
                this.stats.numberOfSubmissions = numberOfSubmissions;

                this.dataForAssessmentPieChart = [
                    numberOfSubmissions - numberOfAssessments,
                    numberOfAssessments
                ];
            },
            (response: HttpErrorResponse) => this.onError(response.message)
        );

        this.courseService.getStatsForInstructors(courseId).subscribe(
            (res: HttpResponse<StatsForInstructorDashboard>) => {
                this.stats = Object.assign({}, this.stats, res.body);
            },
            (response: string) => this.onError(response)
        );
    }

    calculatePercentage(numerator: number, denominator: number): number {
        if (denominator === 0) {
            return 0;
        }

        return Math.round(numerator / denominator * 100);
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
