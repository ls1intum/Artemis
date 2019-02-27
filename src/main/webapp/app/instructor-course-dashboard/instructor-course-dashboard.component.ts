import {JhiAlertService} from 'ng-jhipster';
import {Component, OnInit} from '@angular/core';
import {Course, CourseService, StatsForInstructorDashboard} from 'app/entities/course';
import {ActivatedRoute} from '@angular/router';
import {HttpErrorResponse, HttpResponse} from '@angular/common/http';
import {InitializationState} from 'app/entities/participation';
import {getIcon, getIconTooltip} from 'app/entities/exercise';
import {Result, ResultService} from 'app/entities/result';
import {TutorLeaderboardData} from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';

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
    dataNumbersForPieChart: number[];

    tutors: TutorLeaderboardData = {};

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

                for (const exercise of this.course.exercises) {
                    exercise.participations = exercise.participations.filter(participation => participation.initializationState === InitializationState.FINISHED);
                    exercise.numberOfAssessments = exercise.participations.filter(participation => participation.results.filter(result => result.rated).length > 0).length;
                }
            },
            (response: HttpErrorResponse) => this.onError(response.message)
        );

        this.courseService.getStatsForInstructors(courseId).subscribe(
            (res: HttpResponse<StatsForInstructorDashboard>) => {
                this.stats = res.body;
                this.dataNumbersForPieChart = [
                    res.body.numberOfSubmissions - res.body.numberOfAssessments,
                    res.body.numberOfAssessments
                ];
            },
            (response: string) => this.onError(response)
        );

        this.resultService.findByCourseId(courseId, {withAssessors: true}).subscribe(
            (res: HttpResponse<Result[]>) => {
                const results = res.body;

                for (const result of results) {
                    const tutorId = result.assessor.id;
                    if (!this.tutors[tutorId]) {
                        this.tutors[tutorId] = {
                            tutor: result.assessor,
                            nrOfAssessments: 0
                        };
                    }

                    this.tutors[tutorId].nrOfAssessments++;
                }
            }
        );
    }

    calculatePercentage(numerator: number, denominator: number): number {
        if (denominator === 0) {
            return 0;
        }

        return Math.round( numerator / denominator * 100);
    }

    calculateClass(numberOfAssessments: number, length: number): string {
        const percentage = this.calculatePercentage(numberOfAssessments, length);

        if (percentage < 50) {
            return 'bg-danger';
        } else if (percentage < 100) {
            return 'bg-warning';
        }

        return 'bg-success';
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }
}
