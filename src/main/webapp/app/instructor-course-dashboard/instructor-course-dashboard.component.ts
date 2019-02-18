import {JhiAlertService} from 'ng-jhipster';
import {Component, OnInit} from '@angular/core';
import {Course, CourseService, StatsForInstructorDashboard} from 'app/entities/course';
import {ActivatedRoute} from '@angular/router';
import {HttpResponse, HttpErrorResponse} from '@angular/common/http';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './instructor-course-dashboard.component.html',
    providers: [JhiAlertService]
})
export class InstructorCourseDashboardComponent implements OnInit {
    course: Course;

    stats: StatsForInstructorDashboard;

    constructor(
        private courseService: CourseService,
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService
    ) {}

    ngOnInit(): void {
        this.loadCourse(Number(this.route.snapshot.paramMap.get('courseId')));
    }

    private loadCourse(courseId: number) {
        this.courseService.findWithExercises(courseId).subscribe(
            (res: HttpResponse<Course>) => {
                this.course = res.body;
            },
            (response: HttpErrorResponse) => this.onError(response.message)
        );

        this.courseService.getStatsForInstructors(courseId).subscribe(
            (res: HttpResponse<StatsForInstructorDashboard>) => {
                this.stats = res.body;
            },
            (response: string) => this.onError(response)
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }
}
