import {JhiAlertService} from 'ng-jhipster';
import {Component, OnInit} from '@angular/core';
import {Course, CourseService, StatsForInstructorDashboard} from 'app/entities/course';
import {ActivatedRoute} from '@angular/router';
import {HttpErrorResponse, HttpResponse} from '@angular/common/http';
import {InitializationState} from 'app/entities/participation';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './instructor-course-dashboard.component.html',
    providers: [JhiAlertService]
})
export class InstructorCourseDashboardComponent implements OnInit {
    course: Course;

    stats: StatsForInstructorDashboard;
    dataForGraph: number[];

    constructor(
        private courseService: CourseService,
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
                this.dataForGraph = [
                    res.body.numberOfSubmissions - res.body.numberOfAssessments,
                    res.body.numberOfAssessments
                ];
            },
            (response: string) => this.onError(response)
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
